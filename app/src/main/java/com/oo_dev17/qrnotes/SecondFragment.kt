package com.oo_dev17.qrnotes

import FullscreenImageDialog
import ImageAdapter
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore

import android.text.Editable
import android.text.util.Linkify
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.oo_dev17.qrnotes.databinding.FragmentSecondBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

    private lateinit var cachedFileHandler: CachedFileHandler
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var stringAdapter: DocumentAdapter
    private var qrNote: QrNote? = null
    private var _binding: FragmentSecondBinding? = null

    // Create a storage reference from our app
    private val storageRef = Firebase.storage.reference
    private val sharedViewModel: SharedViewModel by activityViewModels()

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onPause() {
        super.onPause()
        saveText()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentSecondBinding.inflate(inflater, container, false)

        binding.textviewSecond.setTextIsSelectable(true)
        val textviewSecond: EditText = binding.textviewSecond
        textviewSecond.setTextIsSelectable(true)
        textviewSecond.autoLinkMask = Linkify.WEB_URLS
        val titleText = binding.tileText


        cachedFileHandler = CachedFileHandler(storageRef, requireContext())

        // Get the Bundle from the arguments
        val bundle = arguments
        // Check if the bundle is not null and contains the QrNote
        if (bundle != null && bundle.containsKey("qrNote")) {
            // Get the QrNote from the Bundle
            qrNote = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getParcelable("qrNote", QrNote::class.java)
            } else {
                bundle.getParcelable("qrNote")
            }
            // Use the QrNote
            if (qrNote != null) {
                binding.textviewSecond.text =
                    Editable.Factory.getInstance().newEditable(qrNote!!.content)
                titleText.text = qrNote?.title ?: "No title"
                binding.qrCode.text = "QR:${qrNote!!.qrCode}"
                moveOrphanedPdfsToDocuments() // Move any orphaned PDFs on startup
            }
        }
        binding.fabAddDoc.setOnClickListener { _ ->
            OpenFile(this).selectDocToAdd(Uri.EMPTY, true)
        }
        binding.fabAddPdf.setOnClickListener { _ ->
            OpenFile(this).selectDocToAdd(Uri.EMPTY, false)
        }
        // If the edit text contains previous text with potential links
        Linkify.addLinks(textviewSecond, Linkify.WEB_URLS)
        try {
            checkStoragePermission(false)
            setupImagesRecycler()
            setupFilesRecycler()
        } catch (e: Exception) {
            Log.e("Firestore", "Error getting QrNotes", e)
        }

        return binding.root
    }

    private fun moveOrphanedPdfsToDocuments() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val noteRootRef = storageRef.child(qrNote!!.documentId!!)
                val listResult = noteRootRef.listAll().await()

                // Filter for files directly in the note's root directory that are PDFs
                val pdfsInRoot = listResult.items.filter {
                    it.name.endsWith(".pdf", ignoreCase = true)
                }

                if (pdfsInRoot.isNotEmpty()) {
                    Log.d("PdfMigration", "Found ${pdfsInRoot.size} orphaned PDF(s) to move.")
                    val documentsFolderRef =
                        noteRootRef.child(CachedFileHandler.Category.Documents.name)

                    for (pdfRef in pdfsInRoot) {
                        val destinationRef = documentsFolderRef.child(pdfRef.name)

                        // Get file bytes
                        val bytes = pdfRef.getBytes(Long.MAX_VALUE).await()

                        // Upload to new location
                        destinationRef.putBytes(bytes).await()

                        // Delete original file
                        pdfRef.delete().await()

                        Log.d("PdfMigration", "Moved ${pdfRef.name} to Documents folder.")
                    }

                    // Refresh UI on the main thread
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            "Organized ${pdfsInRoot.size} PDF(s).",
                            Toast.LENGTH_SHORT
                        ).show()
                        // Refresh the documents recycler view to show the moved files
                        setupFilesRecycler()
                    }
                } else {
                    Log.d("PdfMigration", "No orphaned PDFs found.")
                }
            } catch (e: Exception) {
                Log.e("PdfMigration", "Error moving orphaned PDFs: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Error organizing PDFs.", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    private fun setupImagesRecycler() {
        assertQrNoteIsInStorageRef()

        viewLifecycleOwner.lifecycleScope.launch {
            val images =
                withContext(Dispatchers.IO) { qrNote!!.retrieveImageFiles(cachedFileHandler) }
            // Get the RecyclerView for images
            val recyclerViewImages: RecyclerView = binding.recyclerViewImages
            val pictures = images.map { ImageItem.FileImage(it) }

            // Set up the RecyclerView with a horizontal LinearLayoutManager
            recyclerViewImages.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            // Create and set the adapter
            val imagesItems = pictures +
                    ImageItem.ResourceImage(R.drawable.plus_sign) +
                    ImageItem.ResourceImage(android.R.drawable.ic_menu_camera)
            imageAdapter = ImageAdapter(imagesItems.toMutableList())
            recyclerViewImages.adapter = imageAdapter



            imageAdapter.onItemClick = { imageItem ->
                when (imageItem) {
                    is ImageItem.FileImage -> {
                        val dialog = FullscreenImageDialog(requireContext(), imageItem.file)
                        dialog.show()
                    }

                    is ImageItem.ResourceImage -> {
                        if (imageItem.resId == R.drawable.plus_sign) {
                            // Check storage permission and open the gallery
                            _binding!!.recyclerViewImages.post {
                                Snackbar.make(
                                    requireView(),
                                    "Book details loaded!",
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                            checkStoragePermission(true)
                        } else if (imageItem.resId == android.R.drawable.ic_menu_camera) {
                            // Check camera permission and open the camera
                            checkCameraPermission()
                        }
                    }
                }
            }
            imageAdapter.onItemLongClick = { imageItem, position ->
                when (imageItem) {
                    is ImageItem.FileImage -> {
                        val builder = android.app.AlertDialog.Builder(requireContext())
                        builder.setTitle("QrNote Options")
                            .setMessage("What do you want to do with img ${imageItem.file.name} ?")
                            .setNeutralButton("Make gallery picture") { dialog, _ ->
                                val noteId = qrNote!!.documentId!!
                                val newGalleryPicName = imageItem.file.name
                                Firebase.firestore.collection("qrNotes").document(noteId)
                                    .update("galleryPic", newGalleryPicName)
                                    .addOnSuccessListener {
                                        // --- THIS IS THE TRIGGER ---
                                        sharedViewModel.requestThumbnailRefresh(
                                            noteId,
                                            newGalleryPicName
                                        )
                                        Toast.makeText(
                                            requireContext(),
                                            "Gallery picture updated!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        dialog.dismiss()
                                    }
                            }
                            .setPositiveButton("Delete") { dialog, _ ->
                                // Delete the document
                                // val fileCache = FileCache(requireContext())
                                // fileCache.deleteFileFromCache(qrNote?.documentId!!, fileName)
                                try {
                                    //storageRef.child(qrNote!!.documentId!!).child(fileName).delete()
                                    // remove file entry from ui

                                    imageAdapter.imageItems.removeAt(position)
                                    imageAdapter.notifyItemRemoved(position)

                                    cachedFileHandler.deleteFileFromBoth(
                                        qrNote!!.documentId!!,
                                        CachedFileHandler.Category.Images,
                                        imageItem.file.name
                                    )
                                    dialog.dismiss()
                                } catch (e: Exception) {
                                    Snackbar.make(
                                        requireView(),
                                        "Delete fail: " + e.message,
                                        Snackbar.LENGTH_LONG
                                    ).show()
                                }
                                dialog.dismiss()
                            }
                            .setNegativeButton("Cancel") { dialog, _ ->
                                dialog.dismiss()
                            }
                            .show()
                    }

                    is ImageItem.ResourceImage -> {
                    }
                }
            }
        }
    }

    private fun assertQrNoteIsInStorageRef() {
        storageRef.listAll()
            .addOnSuccessListener { listResult ->
                val childExists = listResult.prefixes.any { it.name == qrNote!!.documentId }

                if (!childExists) {
                    // Child doesn't exist, add it (example: create an empty folder)
                    storageRef.child("${qrNote!!.documentId}/.emptyfile").putBytes(ByteArray(0))
                } else {
                    // Child exists
                    println("Child 'yourChildName' exists.")
                }
            }
            .addOnFailureListener { exception ->
                // Handle errors (e.g., network issues, permissions)
                println("Error checking child: ${exception.message}")
            }
    }

    private fun setupFilesRecycler() {
        assertQrNoteIsInStorageRef()
        // Capture fragment reference as 'fragment' to avoid confusion with coroutine 'this'

        val fragment = this@SecondFragment


        lifecycleScope.launch {  // Using Fragment's lifecycleScope
            try {
                val listResult =
                    cachedFileHandler.getFileNamesFromCloud(
                        qrNote!!,
                        CachedFileHandler.Category.Documents
                    )

                val recyclerViewFiles: RecyclerView = binding.recyclerViewFiles

                // Set up the RecyclerView with a horizontal LinearLayoutManager
                recyclerViewFiles.layoutManager =
                    LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

                val stringList = listResult.map { it }.toMutableList()
                var stringAdapter = DocumentAdapter(stringList)
                if (stringAdapter.itemCount == 0)
                    stringAdapter = DocumentAdapter(mutableListOf("No documents found"))
                recyclerViewFiles.adapter = stringAdapter

                // Commenting out statistics part as properties don't exist on QrNote yet.
                /*
                for (doc in stringList) {
                    qrNote!!.documentsCount++;
                    if (cachedFileHandler.fileExists(qrNote!!, doc, CachedFileHandler.Category.Documents))
                        qrNote?.docsLoadedFromCache++;
                    Log.d("SecondFragment", "${qrNote!!.documentId}: doc found: ${doc}")
                }
                */
                stringAdapter.onItemClick = { fileName ->
                    lifecycleScope.launch {
                        val fileCache = CachedFileHandler(storageRef, requireContext())
                        val (file, _) = fileCache.getFileFromCacheOrCloud(
                            qrNote!!.documentId!!,
                            CachedFileHandler.Category.Documents, fileName
                        )
                        if (file == null) {
                            Toast.makeText(
                                requireContext(),
                                "Failed to load or download file $fileName.",
                                Toast.LENGTH_SHORT
                            ).show()

                            // This is how you return from a lambda.
                            // 'return@launch' exits the coroutine block.
                            return@launch
                        }
                        OpenFile(fragment).openFileWithAssociatedApp(file, requireContext())

                    }
                }
                stringAdapter.onItemLongClick = { fileName, position ->
                    val builder = android.app.AlertDialog.Builder(requireContext())
                    builder.setTitle("QrNote Options")
                        .setMessage("What do you want to do with doc $fileName ?")
                        .setPositiveButton("Delete") { dialog, _ ->
                            // Delete the document
                            val fileCache = CachedFileHandler(storageRef, requireContext())
                            fileCache.deleteFileFromBoth(
                                qrNote?.documentId!!,
                                CachedFileHandler.Category.Documents,
                                fileName
                            )
                            try {
                                storageRef.child(qrNote!!.documentId!!)
                                    .child(CachedFileHandler.Category.Documents.name)
                                    .child(fileName).delete()
                                // remove file entry from ui
                                stringList.removeAt(position)
                                stringAdapter.notifyDataSetChanged()
                                dialog.dismiss()
                            } catch (e: Exception) {
                                Snackbar.make(
                                    requireView(),
                                    "Delete fail: " + e.message,
                                    Snackbar.LENGTH_LONG
                                ).show()
                            }
                            dialog.dismiss()
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
            } catch (e: Exception) {
                Log.e("SecondFragment", "Error setting up files recycler: ", e)
            }
        }
    }

    private var photoFile: File? = null

    private fun createImageFile(): File {
        val subfolderDir = qrNote?.ImageSubfolder()

        if (subfolderDir == null) {
            _binding!!.recyclerViewImages.post {
                Snackbar.make(
                    requireView(),
                    "subfolderDir == null!",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            return File("")
        }

        // Create the file in the subfolder
        return File.createTempFile(
            "JPEG_${System.currentTimeMillis()}_", // Prefix
            ".jpg", // Suffix
            subfolderDir // Directory
        ).apply {
            photoFile = this
        }
    }

    private val REQUEST_CODE_WRITE_EXTERNAL_STORAGE = 100

    private fun checkStoragePermission(openGallery: Boolean) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            // Use READ_EXTERNAL_STORAGE for older versions
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(), permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request the permission
            ActivityCompat.requestPermissions(
                requireActivity(), arrayOf(permission), REQUEST_CODE_WRITE_EXTERNAL_STORAGE
            )
            if (openGallery)
                openGallery()
        } else {
            // Permission already granted, open the gallery
            if (openGallery)
                openGallery()
        }
    }

    private val REQUEST_CODE_CAMERA_PERMISSION = 200

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request the permission
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CODE_CAMERA_PERMISSION
            )
            openCamera()
        } else {
            // Permission already granted, open the camera
            openCamera()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, open the gallery
                openGallery()
            } else {
                // Permission denied, show a message
                _binding!!.recyclerViewImages.post {
                    Snackbar.make(requireView(), "Storage permission denied", Snackbar.LENGTH_SHORT)
                        .show()
                }
            }
        }
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, open the camera
                openCamera()
            } else {
                // Permission denied, show a message
                _binding!!.recyclerViewImages.post {
                    Snackbar.make(requireView(), "Camera permission denied", Snackbar.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    private val REQUEST_CODE_CAMERA = 201

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        try {
            photoFile = createImageFile()
        } catch (ex: IOException) {
            // Log the error and show a message to the user
            Log.e("SecondFragment", "Error creating image file", ex)
            Snackbar.make(requireView(), "Could not create image file.", Snackbar.LENGTH_LONG)
                .show()
            return // Stop the process if the file can't be created
        }
        // Continue only if photoFile was created successfully
        photoFile?.let { file ->
            val photoURI: Uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)

            // It's good practice to check if a camera app is available
            if (intent.resolveActivity(requireActivity().packageManager) != null) {
                // 2. Use the new launcher to start the activity
                takePictureLauncher.launch(intent)
            } else {
                Snackbar.make(requireView(), "No camera app found", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // This is the callback that replaces onActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            val imageFile = photoFile
            if (imageFile != null && imageFile.exists() && imageFile.length() > 0) {

                // --- THIS IS THE CORRECT PLACE FOR YOUR LOGIC ---

                // 1. Add the file to your UI adapter
                imageAdapter.imageItems.add(0, ImageItem.FileImage(imageFile))
                imageAdapter.notifyItemInserted(0)

                // 2. Upload the file (which now has data) to Firebase
                cachedFileHandler.uploadToCloud(
                    qrNote!!,
                    imageFile,
                    CachedFileHandler.Category.Images
                )

                // 3. Put the file into your local cache
                cachedFileHandler.copyFileToCache(
                    qrNote!!.documentId!!,
                    CachedFileHandler.Category.Images,
                    imageFile.name,
                    imageFile
                )

                // 4. Notify the MediaStore so the image appears in the gallery
                scanFile(imageFile)
            } else {
                Snackbar.make(requireView(), "Failed to save image.", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun scanFile(file: File) {
        MediaScannerConnection.scanFile(
            requireContext(), arrayOf(file.absolutePath), null
        ) { path, uri ->
            // File has been scanned and added to the MediaStore
            Log.d("MediaScan", "Scanned file: $path, URI: $uri")
        }
    }

    private val REQUEST_CODE_PICK_IMAGE = 101

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        getContext()?.let { context ->
            // This block will only execute if the context is not null
            // ... proceed with your logic using 'context'
            super.onActivityResult(requestCode, resultCode, data)
            if (requestCode == REQUEST_CODE_PICK_IMAGE && resultCode == Activity.RESULT_OK) {
                val selectedImageUri: Uri? = data?.data
                if (selectedImageUri != null) {
                    // Load the selected image into an ImageView or process it
                    loadSelectedImage(selectedImageUri)
                }
            }
            if (requestCode == REQUEST_CODE_CAMERA && resultCode == Activity.RESULT_OK) {
                val imageBitmap = data?.extras?.get("data") as? Bitmap
                if (imageBitmap != null) {
                    // Display the captured image
                    loadCapturedImage(imageBitmap)
                }
                val imageFile = photoFile
                if (imageFile != null && imageFile.exists()) {
                    // Notify the MediaStore about the new file
                    scanFile(imageFile)
                }
            }
            if (requestCode == REQUEST_CODE_PICK_DOCUMENT && resultCode == Activity.RESULT_OK) {
                val uri = data?.data // This is the selected file's URI
                if (uri != null && qrNote != null) {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    cursor?.moveToFirst()
                    val fileName = cursor?.getString(cursor.getColumnIndexOrThrow("_display_name"))
                    cursor?.close()

                    val fileCache = CachedFileHandler(storageRef, context)
                    val cachedFile = fileCache.storeFileInCache(
                        qrNote!!.documentId!!,
                        CachedFileHandler.Category.Documents,
                        fileName!!,
                        uri
                    )
                    if (!cachedFile.exists()) {
                        Toast.makeText(
                            context,
                            "Failed to save document locally.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return // Stop if local save failed
                    }

                    val documentRef = storageRef.child(qrNote!!.documentId + fileName)
                    val uploadTask = documentRef.putFile(uri)

                    uploadTask.addOnFailureListener { exception ->
                        Toast.makeText(
                            context,
                            "Upload failed: " + exception.message,
                            Toast.LENGTH_SHORT
                        ).show()
                    }.addOnSuccessListener { taskSnapshot ->
                        Toast.makeText(context, "Upload successful", Toast.LENGTH_SHORT).show()
                        if (::stringAdapter.isInitialized) {
                            stringAdapter.stringList.add(fileName)
                            stringAdapter.notifyDataSetChanged()
                        } else {
                            Toast.makeText(
                                context,
                                "stringAdapter not initialized",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    private fun loadCapturedImage(bitmap: Bitmap) {
        // Example: Load the captured image into an ImageView
        val imageView: ImageView = requireView().findViewById(R.id.imageView)
        imageView.setImageBitmap(bitmap)

        // Optionally, save the bitmap or process it further
        Snackbar.make(requireView(), "Image captured from camera", Snackbar.LENGTH_SHORT).show()
    }

    private fun loadSelectedImage(imageUri: Uri) {
        // Example: Load the image into an ImageView
        val imageView: ImageView = requireView().findViewById(R.id.imageView)
        imageView.setImageURI(imageUri)

        requireContext().contentResolver.openInputStream(imageUri)?.use { inputStream ->
            val outputFile = File(qrNote!!.ImageSubfolder(), "${System.currentTimeMillis()}.jpg")
            outputFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        // Optionally, save the URI or process the image further
        Snackbar.make(requireView(), "Image selected: $imageUri", Snackbar.LENGTH_SHORT).show()
    }

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            Toast.makeText(requireContext(), "Scan cancelled", Toast.LENGTH_SHORT).show()
        } else {
            val qrCode = result.contents // Get the scanned QR code data
            Toast.makeText(requireContext(), "Scanned: $qrCode", Toast.LENGTH_SHORT).show()
            Firebase.firestore.collection("qrNotes").whereEqualTo("qrCode", qrCode)
                .get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        binding.qrCode.text = qrCode
                        Firebase.firestore.collection("qrNotes").document(qrNote!!.documentId!!)
                            .update(qrNote!!::qrCode.name, qrCode)
                    } else {
                        Toast.makeText(
                            requireContext(),
                            "Scanned: $qrCode ALREADY EXISTS",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }
    }

    private fun launchQRCodeScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(
                listOf(
                    ScanOptions.EAN_13,
                    ScanOptions.EAN_8,
                    ScanOptions.QR_CODE
                )
            ) // Specify QR code format
            setPrompt("Scan a QR code") // Set a prompt
            setCameraId(0) // Use the default camera
            setBeepEnabled(true) // Play a beep sound
            setBarcodeImageEnabled(true) // Enable saving the barcode image
        }
        scanLauncher.launch(options) // Launch the scanner
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        stringAdapter = DocumentAdapter(
            ArrayList() // Start with an empty list
        )

        val tileTextView: TextView = binding.tileText

        tileTextView.setOnClickListener {

            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("Change Title")
            val input = EditText(requireContext()).apply {
                setText(qrNote!!.title)
            }
            builder.setView(input)
            builder.setPositiveButton("Change") { dialog, _ ->
                val title = input.text.toString()
                if (title.isNotEmpty()) {
                    tileTextView.text = title
                    tileTextView.requestFocus()
                    Firebase.firestore.collection("qrNotes").document(qrNote!!.documentId!!)
                        .update(qrNote!!::title.name, title)
                } else {
                    Snackbar.make(
                        requireView(), "Title cannot be empty", Toast.LENGTH_SHORT
                    ).show()
                }
                dialog.dismiss()
            }

            builder.setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }
            builder.show()
        }
        binding.buttonScanQr.setOnClickListener {
            launchQRCodeScanner()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun saveText() {
        Firebase.firestore.collection("qrNotes").document(qrNote!!.documentId!!)
            .update(qrNote!!::content.name, _binding!!.textviewSecond.text.toString())
    }

    companion object {
        const val REQUEST_CODE_PICK_FILE = 1

        // Request code for selecting a PDF document.
        const val REQUEST_CODE_PICK_DOCUMENT = 2
    }
}