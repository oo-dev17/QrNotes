package com.oo_dev17.qrnotes

import FullscreenImageDialog
import ImageAdapter
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.util.Linkify
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.storage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.oo_dev17.qrnotes.databinding.FragmentSecondBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

    private lateinit var cachedFileHandler: CachedFileHandler
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var buttonAdapter: ButtonAdapter
    private lateinit var stringAdapter: DocumentAdapter
    private var qrNote: QrNote? = null
    private var _binding: FragmentSecondBinding? = null

    private var firestoreListener: ListenerRegistration? = null

    // Create a storage reference from our app
    private val storageRef = Firebase.storage.reference
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val secondSharedViewModel: SecondSharedViewModel by activityViewModels()

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onPause() {
        super.onPause()
        saveText()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        _binding = FragmentSecondBinding.inflate(inflater, container, false)

        binding.edittextSecond.setTextIsSelectable(true)
        val textviewSecond: EditText = binding.edittextSecond
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
                @Suppress("DEPRECATION") bundle.getParcelable("qrNote")
            }
            // Use the QrNote
            if (qrNote != null) {
                binding.edittextSecond.text =
                    Editable.Factory.getInstance().newEditable(qrNote!!.content)
                titleText.text = qrNote?.title ?: "No title"
                binding.qrCode.text = "QR: ${qrNote!!.qrCode}"
            }
        }

        // OPEN documents
        binding.fabAddDoc.setOnClickListener { _ ->
            OpenFile(this).selectDocToAdd(Uri.EMPTY, true)
        }

        // OPEN PDF
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

    private fun setupImagesRecycler() {
        assertQrNoteIsInStorageRef()

        viewLifecycleOwner.lifecycleScope.launch {
            val imageFiles =
                withContext(Dispatchers.IO) { qrNote!!.retrieveImageFiles(cachedFileHandler) }
            // Get the RecyclerView for images
            val imageItems = imageFiles.map { ImageItem.FileImage(it) }.toMutableList()

            imageAdapter = ImageAdapter(imageItems) // Handles the images
            buttonAdapter = ButtonAdapter()
            val concatAdapter = ConcatAdapter(imageAdapter, buttonAdapter)
            // Set up the RecyclerView with a horizontal LinearLayoutManager
            binding.recyclerViewImages.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            // Create and set the adapter

            binding.recyclerViewImages.adapter = concatAdapter

            buttonAdapter.onAddCameraClick = {
                openCamera() // Your function to open the camera
            }

            buttonAdapter.onAddGalleryClick = {
                selectImageFromGallery() // Your function to open the gallery
            }

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
                                    requireView(), "Book details loaded!", Snackbar.LENGTH_SHORT
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
                        val builder =
                            AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                        builder.setTitle("QrNote Image Menu")
                            .setMessage("What do you want to do with img ${imageItem.file.name} ?")
                            .setNeutralButton("Make gallery picture") { dialog, _ ->
                                val qrNoteDocumentId = qrNote!!.documentId!!
                                val newGalleryPicName = imageItem.file.name
                                val notesCollection = FirestoreManager.getUserNotesCollection()
                                if (notesCollection == null) {
                                    Toast.makeText(
                                        requireContext(),
                                        "User not logged in? Notes empty",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@setNeutralButton
                                }
                                notesCollection.document(qrNoteDocumentId)
                                    .update("galleryPic", newGalleryPicName).addOnSuccessListener {
                                        // --- THIS IS THE TRIGGER ---
                                        sharedViewModel.requestThumbnailRefresh(
                                            qrNoteDocumentId, newGalleryPicName
                                        )
                                        Toast.makeText(
                                            requireContext(),
                                            "Gallery picture updated!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        dialog.dismiss()
                                    }
                            }.setPositiveButton("Delete") { dialog, _ ->
                                AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                                    .setTitle("Delete Image")
                                    .setMessage("Are you sure you want to delete this image?")
                                    .setPositiveButton("Yes") { dialog, _ ->
                                        // Delete the document
                                        try {
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
                                    }.setNegativeButton("Cancel") { dialog, _ ->
                                        dialog.dismiss()
                                    }
                                dialog.dismiss()
                            }.setNegativeButton("Cancel") { dialog, _ ->
                                dialog.dismiss()
                            }.show()
                    }

                    is ImageItem.ResourceImage -> {
                    }
                }
            }
        }
    }

    private fun assertQrNoteIsInStorageRef() {
        if (qrNote?.documentId.isNullOrBlank()) {
            val builder =
                android.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            builder.setTitle("QrNote Image Menu").setMessage("We have no QrNote to show ERROR 4711")
                .show()

            Log.e("SecondFragment", "assertQrNoteIsInStorageRef: qrNote or documentId is null.")
            return
        }

        storageRef.listAll().addOnSuccessListener { listResult ->
            val childExists = listResult.prefixes.any { it.name == qrNote!!.documentId }

            if (!childExists) {
                // Child doesn't exist, add it (example: create an empty folder)
                storageRef.child("${qrNote!!.documentId}/.emptyfile").putBytes(ByteArray(0))
            } else {
                // Child exists
                println("Child 'yourChildName' exists.")
            }
        }.addOnFailureListener { exception ->
            // Handle errors (e.g., network issues, permissions)
            println("Error checking child: ${exception.message}")
        }
    }

    private fun setupFilesRecycler() {
        // NPE root cause: qrNote/documentId can be null when this is called.
        val documentId = qrNote?.documentId
        if (documentId.isNullOrBlank()) {
            Log.e(
                "SecondFragment",
                "setupFilesRecycler: qrNote or documentId is null. Did you forget to pass qrNote in arguments?"
            )
            // Keep UI consistent
            binding.recyclerViewFiles.visibility = View.GONE
            binding.textViewEmptyDocuments.visibility = View.VISIBLE
            return
        }

        assertQrNoteIsInStorageRef()
        // Capture fragment reference as 'fragment' to avoid confusion with coroutine 'this'
        val fragment = this@SecondFragment

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val listResult = cachedFileHandler.getFileNamesFromCloud(
                    documentId, CachedFileHandler.Category.Documents
                )

                val stringList = listResult.toMutableList()
                if (stringList.isEmpty()) {
                    // If the list is empty, hide the RecyclerView and show the placeholder text.
                    binding.recyclerViewFiles.visibility = View.GONE
                    binding.textViewEmptyDocuments.visibility = View.VISIBLE
                } else {
                    // If the list has items, show the RecyclerView and hide the placeholder.
                    binding.recyclerViewFiles.visibility = View.VISIBLE
                    binding.textViewEmptyDocuments.visibility = View.GONE
                }
                stringAdapter = DocumentAdapter(stringList)
                binding.recyclerViewFiles.adapter = stringAdapter
                binding.recyclerViewFiles.layoutManager =
                    LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

                stringAdapter.onItemClick = { fileName ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        val cachedFileHandler = CachedFileHandler(storageRef, requireContext())
                        val (file, _) = cachedFileHandler.getFileFromCacheOrCloud(
                            documentId, CachedFileHandler.Category.Documents, fileName
                        )
                        if (file == null) {
                            Toast.makeText(
                                requireContext(),
                                "Failed to load or download file $fileName.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@launch
                        }
                        OpenFile(fragment).openFileWithAssociatedApp(file, requireContext())

                    }
                }
                stringAdapter.onItemLongClick = { fileName, position ->
                    val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                    builder.setTitle("QrNote Doc Options")
                        .setMessage("What do you want to do with doc $fileName ?")
                        .setPositiveButton("Delete") { dialog, _ ->
                            // Delete the document
                            val cachedFileHandler = CachedFileHandler(storageRef, requireContext())
                            cachedFileHandler.deleteFileFromBoth(
                                documentId, CachedFileHandler.Category.Documents, fileName
                            )
                            try {
                                storageRef.child(documentId)
                                    .child(CachedFileHandler.Category.Documents.name)
                                    .child(fileName).delete()
                                // remove file entry from ui
                                stringList.removeAt(position)
                                stringAdapter.notifyDataSetChanged()
                                dialog.dismiss()
                            } catch (e: Exception) {
                                Snackbar.make(
                                    requireView(), "Delete fail: " + e.message, Snackbar.LENGTH_LONG
                                ).show()
                            }
                            dialog.dismiss()
                        }.setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }.show()
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
                    requireView(), "subfolderDir == null!", Snackbar.LENGTH_SHORT
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
            if (openGallery) selectImageFromGallery()
        } else {
            // Permission already granted, open the gallery
            if (openGallery) selectImageFromGallery()
        }
    }

    private val REQUEST_CODE_CAMERA_PERMISSION = 200

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.CAMERA
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
                selectImageFromGallery()
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
                requireContext(), "${requireContext().packageName}.fileprovider", file
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
                imageAdapter.imageItems.add(ImageItem.FileImage(imageFile))
                imageAdapter.notifyItemInserted(imageAdapter.imageItems.size - 1)

                // 2. Upload the file (which now has data) to Firebase
                cachedFileHandler.uploadToCloud(
                    qrNote!!, imageFile, CachedFileHandler.Category.Images
                )
                // The file IS already in cache!

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

    private fun selectImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        @Suppress("DEPRECATION") startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE)
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
                    val cachedFileHandler = CachedFileHandler(storageRef, context)
                    if (cachedFileHandler.storeSelectedImageInCloudAndCache(
                            selectedImageUri, qrNote!!, imageAdapter
                        )
                    ) Snackbar.make(requireView(), "Image loaded!", Snackbar.LENGTH_SHORT).show()
                    else Snackbar.make(
                        requireView(), "Image failed to load!", Snackbar.LENGTH_SHORT
                    ).show()
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
                    val contentResolver = context.contentResolver
                    var fileName = "unknown_document"
                    val mimeType = contentResolver.getType(uri)
                    val cursor = contentResolver.query(uri, null, null, null, null)
                    cursor?.moveToFirst()
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val displayName =
                                it.getString(it.getColumnIndexOrThrow("_display_name"))

                            // Check if the display name already has an extension
                            if (!displayName.contains(".")) {
                                // If not, get the extension from the MIME type
                                val extension =
                                    MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                                // Append the extension to the display name
                                fileName = "$displayName.$extension"
                            } else {
                                // If it already has an extension, use it directly
                                fileName = displayName
                            }
                        }
                    }
                    val cachedFileHandler = CachedFileHandler(storageRef, context)
                    val cachedFile = cachedFileHandler.storeFileInCache(
                        qrNote!!.documentId!!, CachedFileHandler.Category.Documents, fileName, uri
                    )
                    if (!cachedFile.exists()) {
                        Toast.makeText(
                            context, "Failed to save document locally.", Toast.LENGTH_SHORT
                        ).show()
                        return // Stop if local save failed
                    }

                    val documentRef = storageRef.child(qrNote!!.documentId!!)
                        .child(CachedFileHandler.Category.Documents.name).child(fileName)
                    val uploadTask = documentRef.putFile(uri)

                    uploadTask.addOnFailureListener { exception ->
                        Toast.makeText(
                            context, "Upload failed: " + exception.message, Toast.LENGTH_SHORT
                        ).show()
                    }.addOnSuccessListener {
                        Toast.makeText(
                            context, "Upload successful of $fileName", Toast.LENGTH_SHORT
                        ).show()
                        val wasEmpty = stringAdapter.itemCount == 0
                        stringAdapter.stringList.add(fileName)
                        stringAdapter.notifyItemInserted(stringAdapter.itemCount - 1)
                        if (wasEmpty) {
                            binding.recyclerViewFiles.visibility = View.VISIBLE
                            binding.textViewEmptyDocuments.visibility = View.GONE
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

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            Snackbar.make(requireView(), "Scan cancelled", Snackbar.LENGTH_SHORT).show()
        } else {
            val scannedQrCode = result.contents // Get the scanned QR code data
            Snackbar.make(requireView(), "Scanned: $scannedQrCode", Snackbar.LENGTH_SHORT).show()
            val notesCollection = FirestoreManager.getUserNotesCollection()
            if (notesCollection == null) {
                Snackbar.make(
                    requireView(), "User not logged in? Notes empty", Snackbar.LENGTH_SHORT
                ).show()
                return@registerForActivityResult
            }
            notesCollection.whereEqualTo(QrNote::qrCode.name, scannedQrCode).get()
                .addOnSuccessListener { documents ->
                    val otherDoc = documents.firstOrNull { it.id != qrNote!!.documentId }
                    if (otherDoc != null) {
                        val otherTitle = otherDoc.getString(QrNote::title.name) ?: "Untitled"

                        showTallSnackbar(
                            requireView(),
                            "QR code is already in use by note: $otherTitle (${otherDoc.id})",
                            Snackbar.LENGTH_LONG
                        )
                    } else {
                        val currentId = qrNote!!.documentId!!
                        notesCollection.document(currentId)
                            .update(QrNote::qrCode.name, scannedQrCode).addOnSuccessListener {
                                showTallSnackbar(
                                    requireView(),
                                    "QR code updated successfully.",
                                    Snackbar.LENGTH_SHORT
                                )
                            }.addOnFailureListener { e ->
                                showTallSnackbar(
                                    requireView(),
                                    "Failed to update QR code: ${e.message}",
                                    Snackbar.LENGTH_LONG
                                )
                            }
                    }
                }.addOnFailureListener { e ->
                    showTallSnackbar(
                        requireView(),
                        "Failed to query for QR code: ${e.message}",
                        Snackbar.LENGTH_LONG
                    )
                }
        }
    }

    private fun showTallSnackbar(
        anchor: View, message: String, duration: Int = Snackbar.LENGTH_LONG
    ) {
        val snackbar = Snackbar.make(anchor, message, duration)

        val snackView: View = snackbar.view // <-- DO NOT cast to SnackbarLayout

        // Use the app's R; library R classes are not accessible in some AGP versions.
        val textView = snackView.findViewById<TextView>(R.id.snackbar_text)

        // regular-ish text size + multiline
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        textView.maxLines = 5
        textView.textAlignment = View.TEXT_ALIGNMENT_CENTER

        // padding + taller
        val padH = dpToPx(24)
        val padV = dpToPx(18)
        textView.setPadding(padH, padV, padH, padV)
        snackView.minimumHeight = dpToPx(96)

        // center in parent (FrameLayout/CoordinatorLayout)
        val lp = snackView.layoutParams
        val newLp = when (lp) {
            is FrameLayout.LayoutParams -> lp
            is ViewGroup.MarginLayoutParams -> FrameLayout.LayoutParams(lp)
            else -> FrameLayout.LayoutParams(lp)
        }
        newLp.width = ViewGroup.LayoutParams.WRAP_CONTENT
        newLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        newLp.gravity = Gravity.CENTER
        snackView.layoutParams = newLp

        // optional colors
        ViewCompat.setBackgroundTintList(
            snackView,
            ColorStateList.valueOf(ContextCompat.getColor(anchor.context, android.R.color.black))
        )
        textView.setTextColor(ContextCompat.getColor(anchor.context, android.R.color.white))

        snackbar.show()
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun launchQRCodeScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(
                listOf(
                    ScanOptions.EAN_13, ScanOptions.EAN_8, ScanOptions.QR_CODE
                )
            ) // Specify QR code format
            setPrompt("Scan a new QR code") // Set a prompt
            setCameraId(0) // Use the default camera
            setBeepEnabled(true) // Play a beep sound
            setBarcodeImageEnabled(true) // Enable saving the barcode image
        }
        scanLauncher.launch(options) // Launch the scanner
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tileText.setOnClickListener {

            val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
            builder.setTitle("Change Title")
            val input = EditText(requireContext()).apply {
                setText(qrNote!!.title)
            }
            builder.setView(input)
            builder.setPositiveButton("Change") { dialog, _ ->
                val title = input.text.toString()
                if (title.isNotEmpty()) {
                    val notesCollection = FirestoreManager.getUserNotesCollection()
                    if (notesCollection == null) {
                        Toast.makeText(
                            requireContext(), "User not logged in? Notes empty", Toast.LENGTH_SHORT
                        ).show()
                        return@setPositiveButton
                    }
                    notesCollection.document(qrNote!!.documentId!!)
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
        val notesCollection = FirestoreManager.getUserNotesCollection()
        if (notesCollection == null) {
            Toast.makeText(
                requireContext(), "User not logged in? Notes empty", Toast.LENGTH_SHORT
            ).show()
            return
        }
        val docRef = notesCollection.document(qrNote!!.documentId!!)
        if (docRef == null) {
            Toast.makeText(
                requireContext(), "User not logged in? Notes empty", Toast.LENGTH_SHORT
            ).show()
        }
        firestoreListener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("FirestoreListener", "Listen failed.", error)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                // --- THIS IS THE MAGIC ---
                // Firestore has new data. Convert it to your QrNote object.
                qrNote = snapshot.toObject(QrNote::class.java)
                qrNote?.documentId = snapshot.id

                // Update your entire UI with the fresh data from the SSoT.
                updateUiWithNewData()
            } else {
                Log.d("FirestoreListener", "Current data: null")
            }
        }
        // MENU BUTTON "SCAN NEW QR CODE"
        secondSharedViewModel.requestedAction.observe(viewLifecycleOwner) { action ->
            // When the action is not NONE, it's a request for this fragment to act.
            when (action) {
                NoteAction.SCAN_NEW_QR_CODE -> {
                    // --- HANDLE THE ACTION HERE ---
                    // The user clicked the menu item, so launch the scanner.
                    launchQRCodeScanner()

                    // IMPORTANT: Reset the action so it doesn't run again
                    // if the user rotates the screen or navigates back.
                    secondSharedViewModel.onActionHandled()
                }

                NoteAction.NONE -> { /* Do nothing, this is the idle state */
                }

                else -> { /* Do nothing */
                }
            }
        }
    }

    private fun updateUiWithNewData() {
        try {
            // This function is now the single place where UI is updated.
            binding.tileText.text = qrNote?.title
            binding.tileText.requestFocus()
            binding.qrCode.text = "QR: " + qrNote?.qrCode
        } catch (e: Exception) {

        }

        // ... update all other UI elements from the 'qrNote' object
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun saveText() {
        val notesCollection = FirestoreManager.getUserNotesCollection()
        if (notesCollection == null) {
            Toast.makeText(
                requireContext(), "User not logged in? Notes empty", Toast.LENGTH_SHORT
            ).show()
            return
        }
        notesCollection.document(qrNote!!.documentId!!)
            .update(qrNote!!::content.name, _binding!!.edittextSecond.text.toString())
    }

    companion object {
        const val REQUEST_CODE_PICK_FILE = 1

        // Request code for selecting a PDF document.
        const val REQUEST_CODE_PICK_DOCUMENT = 2
    }
}
