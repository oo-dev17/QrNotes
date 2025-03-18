package com.oo_dev17.qrnotes

import FullscreenImageDialog
import ImageAdapter
import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
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
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.oo_dev17.qrnotes.databinding.FragmentSecondBinding
import java.io.File

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

    private lateinit var imageAdapter: ImageAdapter
    private lateinit var stringAdapter: DocumentAdapter
    private var qrNote: QrNote? = null
    private var _binding: FragmentSecondBinding? = null

    // Create a storage reference from our app
    private val storage = Firebase.storage
    private var storageRef = storage.reference

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onAttach(context: Context) {
        super.onAttach(context)
    }

    override fun onDetach() {
        super.onDetach()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        saveText()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecondBinding.inflate(inflater, container, false)

        binding.textviewSecond.setTextIsSelectable(true)
        val textviewSecond: EditText = binding.textviewSecond
        textviewSecond.setTextIsSelectable(true)
        textviewSecond.autoLinkMask = Linkify.WEB_URLS

        val titleText = binding.tileText

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
                binding.qrCode.text = qrNote!!.qrCode
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
            SetupImagesRecycler()
            SetupFilesRecycler()

// Handle item clicks
            imageAdapter.onItemClick = { imageItem ->
                when (imageItem) {
                    is ImageItem.FileImage -> {
                        _binding!!.recyclerViewImages.post {
                            Snackbar.make(
                                requireView(),
                                "Image clicked: ${imageItem.file}",
                                Snackbar.LENGTH_SHORT
                            )
                                .show()
                        }
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
                            .setPositiveButton("Delete") { dialog, _ ->
                                // Delete the document
                                // val fileCache = FileCache(requireContext())
                                // fileCache.deleteFileFromCache(qrNote?.documentId!!, fileName)
                                try {
                                    //storageRef.child(qrNote!!.documentId!!).child(fileName).delete()
                                    // remove file entry from ui

                                    imageAdapter.imageItems.removeAt(position)
                                    imageAdapter.notifyItemRemoved(position)

                                    val outputFile = File(qrNote!!.ImageSubfolder(), imageItem.file.name)
                                    deleteImageFromDcim(requireContext(), findAndGetMediaStoreUri(requireContext(), outputFile)!!)

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
        } catch (e: Exception) {
            Log.e("Firestore", "Error getting QrNotes", e)
        }

        return binding.root
    }

    private fun deleteImageFromDcim(context: Context, imageUri: Uri) {
        val contentResolver: ContentResolver = context.contentResolver

        try {
            // Delete using the Media Store URI
            val rowsDeleted = contentResolver.delete(imageUri, null, null)

            if (rowsDeleted > 0) {
                println("Image deleted successfully.")
            } else {
                println("Image deletion failed or image not found.")
            }
        } catch (e: SecurityException) {
            println("Permission error: " + e.message)
        } catch (e: Exception){
            println("Error deleting image: " + e.message)
        }
    }

    fun findAndGetMediaStoreUri(context:Context, imageFile:File) : Uri?{
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
        )
        val selection = "${MediaStore.Images.Media.DATA} = ?"
        val selectionArgs = arrayOf(imageFile.absolutePath)

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val id = cursor.getLong(idColumn)
                return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            }
        }
        return null
    }

    private fun SetupImagesRecycler() {
        var (images, error) = qrNote!!.getImageFiles()
        if (error != "") {
            _binding!!.recyclerViewImages.post {
                Snackbar.make(
                    requireView(),
                    error,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            return
        }

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
    }

    private fun SetupFilesRecycler() {
        val allDocumentReferences = storageRef.child(qrNote!!.documentId!!).listAll()
        allDocumentReferences.addOnSuccessListener { listResult ->
            run {
                qrNote!!.allDocuments = listResult.items.map { it.name }
                val recyclerViewFiles: RecyclerView = binding.recyclerViewFiles

                // Set up the RecyclerView with a horizontal LinearLayoutManager
                recyclerViewFiles.layoutManager =
                    LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

                val stringList = listResult.items.map { it.name }.toMutableList()
                val stringAdapter = DocumentAdapter(stringList)
                recyclerViewFiles.adapter = stringAdapter

                // Set the layout manager
                recyclerViewFiles.layoutManager = LinearLayoutManager(requireContext())

                stringAdapter.onItemClick = { fileName ->
                    val fileCache = FileCache(requireContext())
                    // either for using a cached file or for downloading to it
                    val (localFile, exists) = fileCache.getPathForFileFromCache(
                        qrNote?.documentId!!,
                        fileName
                    )

                    if (exists) {
                        OpenFile(this).openFileWithAssociatedApp(localFile, requireContext())
                    } else {
                        val downloadRef = storageRef.child(qrNote!!.documentId!!).child(fileName)
                        downloadRef.getFile(localFile).addOnSuccessListener {
                            // Local temp file has been created
                            OpenFile(this).openFileWithAssociatedApp(localFile!!, requireContext())
                        }.addOnFailureListener { fail ->
                            Toast.makeText(
                                requireContext(),
                                "Download failed: " + fail.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
                stringAdapter.onItemLongClick = { fileName, position ->
                    val builder = android.app.AlertDialog.Builder(requireContext())
                    builder.setTitle("QrNote Options")
                        .setMessage("What do you want to do with doc ${fileName} ?")
                        .setPositiveButton("Delete") { dialog, _ ->
                            // Delete the document
                            val fileCache = FileCache(requireContext())
                            fileCache.deleteFileFromCache(qrNote?.documentId!!, fileName)
                            try {
                                storageRef.child(qrNote!!.documentId!!).child(fileName).delete()
                                // remove file entry from ui
                                stringList.removeAt(position)
                                stringAdapter.notifyDataSetChanged()
                            } catch (e: Exception) {
                                Snackbar.make(
                                    requireView(),
                                    "Delete in cloud failed: " + e.message,
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                            dialog.dismiss()
                        }
                        .setNegativeButton("Cancel") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()
                }
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

    private val REQUEST_CODE_READ_EXTERNAL_STORAGE = 100

    private fun checkStoragePermission(openGallery: Boolean) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Use READ_MEDIA_IMAGES for Android 13 and above
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            // Use READ_EXTERNAL_STORAGE for older versions
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(
                requireContext(), permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request the permission
            ActivityCompat.requestPermissions(
                requireActivity(), arrayOf(permission), REQUEST_CODE_READ_EXTERNAL_STORAGE
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
        if (requestCode == REQUEST_CODE_READ_EXTERNAL_STORAGE) {
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

        photoFile = createImageFile()
        val photoURI: Uri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.fileprovider", photoFile!!
        )
        intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            startActivityForResult(intent, REQUEST_CODE_CAMERA)
            imageAdapter.imageItems.add(0, ImageItem.FileImage(photoFile!!))
            imageAdapter.notifyItemInserted(0)
        } else {
            Snackbar.make(requireView(), "No camera app found", Snackbar.LENGTH_SHORT).show()
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
                val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
                cursor?.moveToFirst()
                val fileName = cursor?.getString(cursor.getColumnIndexOrThrow("_display_name"))
                cursor?.close()

                val fileCache = FileCache(requireContext())
                fileCache.storeFileInCache(qrNote?.documentId!!, fileName!!, uri)
                val documentRef = storageRef.child(qrNote!!.documentId + "/" + fileName)
                val uploadTask = documentRef.putFile(uri)
                uploadTask.addOnFailureListener { exception ->
                    Toast.makeText(
                        requireContext(),
                        "Upload failed: " + exception.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }.addOnSuccessListener { taskSnapshot ->
                    Toast.makeText(requireContext(), "Upload successful", Toast.LENGTH_SHORT).show()
                    stringAdapter.stringList.add(fileName)
                    stringAdapter.notifyDataSetChanged()
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
                            .update("qrCode", qrCode)
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
                        .update("${qrNote!!::title.name}", title)
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
        binding.buttonSecond.setOnClickListener {
            launchQRCodeScanner()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun saveText() {
        Firebase.firestore.collection("qrNotes").document(qrNote!!.documentId!!)
            .update("content", _binding!!.textviewSecond.text.toString())
        var textSaved = true
    }

    companion object {
        const val REQUEST_CODE_PICK_FILE = 1

        // Request code for selecting a PDF document.
        const val REQUEST_CODE_PICK_DOCUMENT = 2
    }
}