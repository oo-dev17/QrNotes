package com.oo_dev17.qrnotes

import FullscreenImageDialog
import ImageAdapter
import android.Manifest
import android.app.Activity
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
import android.text.TextWatcher
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
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.oo_dev17.qrnotes.databinding.FragmentSecondBinding
import java.io.File

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

    private var qrNote: QrNote? = null
    private var _binding: FragmentSecondBinding? = null

    private var fabVisibilityListener: FabVisibilityListener? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // Check if the activity implements the interface
        if (context is FabVisibilityListener) {
            fabVisibilityListener = context
        } else {
            throw RuntimeException("$context must implement FabVisibilityListener")
        }

    }

    override fun onDetach() {
        super.onDetach()
        fabVisibilityListener = null
    }

    override fun onResume() {
        super.onResume()
        // Hide the FAB when the fragment is visible
        fabVisibilityListener?.hideFab()
    }

    override fun onPause() {
        super.onPause()
        SaveText()
        // Show the FAB when the fragment is no longer visible
        fabVisibilityListener?.showFab()
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
            }
        }
        // If the edit text contains previous text with potential links
        Linkify.addLinks(textviewSecond, Linkify.WEB_URLS)
        try {
            val imagePath = qrNote?.ImageSubfolder()?.absolutePath
            if (imagePath == null) {
                _binding!!.recyclerView.post {
                    Snackbar.make(
                        requireView(),
                        "imagePath == null!",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
            var (images, error) = qrNote!!.getImageFiles()
            if (error !="") {
                _binding!!.recyclerView.post {
                    Snackbar.make(
                        requireView(),
                        error,
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
                return binding.root
            }

            // Get the RecyclerView
            val recyclerView: RecyclerView = binding.recyclerView
            val pictures = images.map { ImageItem.FileImage(it) }
            _binding!!.recyclerView.post {
                Snackbar.make(
                    requireView(),
                    "Number of images: ${pictures.size}",
                    Snackbar.LENGTH_SHORT
                ).show()
            }

            // Set up the RecyclerView with a horizontal LinearLayoutManager
            recyclerView.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            // Create and set the adapter
            val imagesItems = pictures +
                    ImageItem.ResourceImage(R.drawable.plus_sign) +
                    ImageItem.ResourceImage(android.R.drawable.ic_menu_camera)
            val adapter = ImageAdapter(imagesItems)
            recyclerView.adapter = adapter

// Handle item clicks
            adapter.onItemClick = { imageItem ->
                when (imageItem) {
                    is ImageItem.FileImage -> {
                        _binding!!.recyclerView.post {
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
                            _binding!!.recyclerView.post {
                                Snackbar.make(
                                    requireView(),
                                    "Book details loaded!",
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                            checkStoragePermission()
                        } else if (imageItem.resId == android.R.drawable.ic_menu_camera) {
                            // Check camera permission and open the camera
                            checkCameraPermission()
                        }
                    }
                }


            }
        } catch (e: Exception) {
            Log.e("Firestore", "Error getting QrNotes", e)
        }
        return binding.root
    }

    private var photoFile: File? = null

    private fun createImageFile(): File {

        val subfolderDir = qrNote?.ImageSubfolder()

        if (subfolderDir == null) {
            _binding!!.recyclerView.post {
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

    private fun checkStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Use READ_MEDIA_IMAGES for Android 13 and above
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            // Use READ_EXTERNAL_STORAGE for older versions
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        _binding!!.recyclerView.post {
            Snackbar.make(requireView(), "1", Snackbar.LENGTH_SHORT).show()
        }
        if (ContextCompat.checkSelfPermission(
                requireContext(), permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request the permission
            ActivityCompat.requestPermissions(
                requireActivity(), arrayOf(permission), REQUEST_CODE_READ_EXTERNAL_STORAGE
            )
        } else {
            // Permission already granted, open the gallery
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
                _binding!!.recyclerView.post {
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
                _binding!!.recyclerView.post {
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

        // Optionally, save the URI or process the image further
        Snackbar.make(requireView(), "Image selected: $imageUri", Snackbar.LENGTH_SHORT).show()
    }


    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            Toast.makeText(requireContext(), "Scan cancelled", Toast.LENGTH_SHORT).show()
        } else {
            val scannedData = result.contents // Get the scanned QR code data
            Toast.makeText(requireContext(), "Scanned: $scannedData", Toast.LENGTH_SHORT).show()
            binding.qrCode.text = scannedData
        }
    }

    private fun launchQRCodeScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE) // Specify QR code format
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

            // Set up the buttons
            builder.setPositiveButton("Change") { dialog, _ ->
                val title = input.text.toString()
                if (title.isNotEmpty()) {

                    tileTextView.text = title
                    tileTextView.requestFocus()
                    val updates = hashMapOf<String, Any>(
                        "${qrNote!!::title.name}" to title
                    )

                    Firebase.firestore.collection("qrNotes").document(qrNote!!.documentId!!)
                        .update(updates).addOnSuccessListener {
                            Log.d("Firestore", "Note updated with ID: ${qrNote!!.documentId}")
                        }.addOnFailureListener { e ->
                            Log.w("Firestore", "Error adding note", e)
                        }
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

            // Show the dialog
            builder.show()


        }

        binding.buttonSecond.setOnClickListener {
            launchQRCodeScanner()
            //findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    interface FabVisibilityListener {
        fun showFab()
        fun hideFab()
    }


    fun SaveText() {
        Firebase.firestore.collection("qrNotes").document(qrNote!!.documentId!!)
            .update("content", _binding!!.textviewSecond.text.toString())
        var textSaved = true
    }
}