package com.oo_dev17.qrnotes

import FullscreenImageDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.util.Linkify
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oo_dev17.qrnotes.databinding.FragmentSecondBinding
import com.google.android.material.snackbar.Snackbar
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.app.Activity
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

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
        // Show the FAB when the fragment is no longer visible
        fabVisibilityListener?.showFab()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentSecondBinding.inflate(inflater, container, false)

        binding.textviewSecond.setTextIsSelectable(true)
        val editText: EditText = binding.textviewSecond
        editText.setTextIsSelectable(true)
        editText.autoLinkMask = Linkify.WEB_URLS
// If the edit text contains previous text with potential links
        Linkify.addLinks(editText, Linkify.WEB_URLS)

        // List of image resource IDs
        val imageResIds = listOf(
            R.drawable.haus,
            R.drawable.haus,
            R.drawable.plus_sign,
            android.R.drawable.ic_menu_camera
        )
        println("Number of images: ${imageResIds.size}")

        // Get the RecyclerView
        val recyclerView: RecyclerView = binding.recyclerView

        // Set up the RecyclerView with a horizontal LinearLayoutManager
        recyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        // Create and set the adapter
        val adapter = ImageAdapter(imageResIds)
        recyclerView.adapter = adapter

// Handle item clicks
        adapter.onItemClick = { imageResId ->
            if (imageResId == R.drawable.plus_sign) {
                // Check storage permission and open the gallery
                Snackbar.make(requireView(), "Book details loaded!", Snackbar.LENGTH_SHORT).show()
                checkStoragePermission()
            } else if (imageResId == android.R.drawable.ic_menu_camera) {
                // Check camera permission and open the camera
                checkCameraPermission()
            } else {
                // Default action for other images
                Snackbar.make(requireView(), "Image clicked: $imageResId", Snackbar.LENGTH_SHORT)
                    .show()
                val dialog = FullscreenImageDialog(requireContext(), imageResId)
                dialog.show()
            }
        }
        return binding.root
    }
    private var photoFile: File? = null

    private fun createImageFile(subfolder : String): File {
        // Get the DCIM directory
        val storageDir: File? = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)

        // Create the dynamic subfolder
        val subfolderDir = File(storageDir, subfolder)
        if (!subfolderDir.exists()) {
            subfolderDir.mkdirs() // Create the subfolder if it doesn't exist
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
        Snackbar.make(requireView(), "1", Snackbar.LENGTH_SHORT).show()
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request the permission
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(permission),
                REQUEST_CODE_READ_EXTERNAL_STORAGE
            )
        } else {
            // Permission already granted, open the gallery
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
        } else {
            // Permission already granted, open the camera
            openCamera()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_READ_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, open the gallery
                openGallery()
            } else {
                // Permission denied, show a message
                Snackbar.make(requireView(), "Storage permission denied", Snackbar.LENGTH_SHORT)
                    .show()
            }
        }
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, open the camera
                openCamera()
            } else {
                // Permission denied, show a message
                Snackbar.make(requireView(), "Camera permission denied", Snackbar.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private val REQUEST_CODE_CAMERA = 201

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val subfolder = "QrNotes/ABC123"
        photoFile = createImageFile(subfolder)
        val photoURI: Uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            photoFile!!
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
            requireContext(),
            arrayOf(file.absolutePath),
            null
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonSecond.setOnClickListener {
            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
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
}
