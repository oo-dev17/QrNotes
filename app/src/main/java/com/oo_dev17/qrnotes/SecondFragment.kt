package com.oo_dev17.qrnotes

import FullscreenImageDialog
import ImageAdapter
import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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

    // --- Permission launchers (replaces requestPermissions + onRequestPermissionsResult) ---

    private var pendingOpenGalleryAfterPermission = false
    private val readImagesPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                if (pendingOpenGalleryAfterPermission) {
                    pendingOpenGalleryAfterPermission = false
                    selectImageFromGallery()
                }
            } else {
                pendingOpenGalleryAfterPermission = false
                Snackbar.make(requireView(), "Storage permission denied", Snackbar.LENGTH_SHORT)
                    .show()
            }
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                openCamera()
            } else {
                Snackbar.make(requireView(), "Camera permission denied", Snackbar.LENGTH_SHORT)
                    .show()
            }
        }

    // --- Activity Result API launchers ---

    // Gallery image picker
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null || qrNote == null) return@registerForActivityResult

            val cachedFileHandler = CachedFileHandler(storageRef, requireContext())
            if (cachedFileHandler.storeSelectedImageInCloudAndCache(uri, qrNote!!, imageAdapter)) {
                Snackbar.make(requireView(), "Image loaded!", Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(requireView(), "Image failed to load!", Snackbar.LENGTH_SHORT).show()
            }
        }

    // Document picker
    private val openDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null || qrNote == null) return@registerForActivityResult

            val context = requireContext()
            val contentResolver = context.contentResolver
            var fileName = "unknown_document"
            val mimeType = contentResolver.getType(uri)

            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val displayName = it.getString(it.getColumnIndexOrThrow("_display_name"))
                    fileName = if (!displayName.contains(".")) {
                        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                        if (extension.isNullOrBlank()) displayName else "$displayName.$extension"
                    } else {
                        displayName
                    }
                }
            }

            val cachedFileHandler = CachedFileHandler(storageRef, context)
            val cachedFile = cachedFileHandler.storeFileInCache(
                qrNote!!.documentId!!,
                CachedFileHandler.Category.Documents,
                fileName,
                uri
            )
            if (!cachedFile.exists()) {
                Toast.makeText(context, "Failed to save document locally.", Toast.LENGTH_SHORT)
                    .show()
                return@registerForActivityResult
            }

            val documentRef = storageRef.child(qrNote!!.documentId!!)
                .child(CachedFileHandler.Category.Documents.name)
                .child(fileName)

            documentRef.putFile(uri)
                .addOnFailureListener { exception ->
                    Toast.makeText(
                        context,
                        "Upload failed: " + exception.message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .addOnSuccessListener {
                    Toast.makeText(context, "Upload successful of $fileName", Toast.LENGTH_SHORT)
                        .show()

                    val wasEmpty = stringAdapter.itemCount == 0
                    stringAdapter.stringList.add(fileName)
                    stringAdapter.notifyItemInserted(stringAdapter.itemCount - 1)

                    if (wasEmpty) {
                        binding.recyclerViewFiles.visibility = View.VISIBLE
                        binding.textViewEmptyDocuments.visibility = View.GONE
                    }
                }
        }

    private var pendingPhotoUri: Uri? = null

    // Camera capture
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success: Boolean ->
            if (!success) {
                Snackbar.make(requireView(), "Failed to take picture.", Snackbar.LENGTH_SHORT).show()
                return@registerForActivityResult
            }

            val uri = pendingPhotoUri ?: return@registerForActivityResult

            try {
                val cachedFileHandler = CachedFileHandler(storageRef, requireContext())
                if (cachedFileHandler.storeSelectedImageInCloudAndCache(uri, qrNote!!, imageAdapter)) {
                    Snackbar.make(requireView(), "Image captured!", Snackbar.LENGTH_SHORT).show()
                } else {
                    Snackbar.make(requireView(), "Captured image failed to load.", Snackbar.LENGTH_SHORT)
                        .show()
                }
            } catch (e: Exception) {
                Snackbar.make(requireView(), "Captured image error: ${e.message}", Snackbar.LENGTH_LONG)
                    .show()
            }
        }

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

        val bundle = arguments
        if (bundle != null && bundle.containsKey("qrNote")) {
            qrNote = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getParcelable("qrNote", QrNote::class.java)
            } else {
                @Suppress("DEPRECATION") bundle.getParcelable("qrNote")
            }

            if (qrNote != null) {
                binding.edittextSecond.text =
                    Editable.Factory.getInstance().newEditable(qrNote!!.content)
                titleText.text = qrNote?.title ?: "No title"
                binding.qrCode.text = "QR: ${qrNote!!.qrCode}"
            }
        }

        // OPEN documents
        binding.fabAddDoc.setOnClickListener {
            openDocumentLauncher.launch(allowedDocumentMimeTypesAll)
        }

        // OPEN PDF
        binding.fabAddPdf.setOnClickListener {
            openDocumentLauncher.launch(arrayOf("application/pdf"))
        }

        Linkify.addLinks(textviewSecond, Linkify.WEB_URLS)
        try {
            // Ask once during view creation
            checkStoragePermission(openGallery = false)
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
            val imageItems = imageFiles.map { ImageItem.FileImage(it) }.toMutableList()

            imageAdapter = ImageAdapter(imageItems)
            buttonAdapter = ButtonAdapter()
            val concatAdapter = ConcatAdapter(imageAdapter, buttonAdapter)

            binding.recyclerViewImages.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

            binding.recyclerViewImages.adapter = concatAdapter

            buttonAdapter.onAddCameraClick = {
                checkCameraPermissionAndOpen()
            }

            buttonAdapter.onAddGalleryClick = {
                checkStoragePermission(openGallery = true)
            }

            imageAdapter.onItemClick = { imageItem ->
                when (imageItem) {
                    is ImageItem.FileImage -> {
                        val dialog = FullscreenImageDialog(requireContext(), imageItem.file)
                        dialog.show()
                    }

                    is ImageItem.ResourceImage -> {
                        if (imageItem.resId == R.drawable.plus_sign) {
                            checkStoragePermission(openGallery = true)
                        } else if (imageItem.resId == android.R.drawable.ic_menu_camera) {
                            checkCameraPermissionAndOpen()
                        }
                    }
                }
            }
            imageAdapter.onItemLongClick = { imageItem, position ->
                when (imageItem) {
                    is ImageItem.FileImage -> {
                        val builder = AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                        builder.setTitle("QrNote Image Menu")
                            .setMessage("What to do with img ${imageItem.file.name} ?")
                            .setNeutralButton("Set as gallery picture") { dialog, _ ->
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
                                    .update("galleryPic", newGalleryPicName)
                                    .addOnSuccessListener {
                                        sharedViewModel.requestThumbnailRefresh(
                                            qrNoteDocumentId,
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
                                AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                                    .setTitle("Delete Image")
                                    .setMessage("Are you sure you want to delete this image?")
                                    .setPositiveButton("Yes") { d, _ ->
                                        try {
                                            imageAdapter.imageItems.removeAt(position)
                                            imageAdapter.notifyItemRemoved(position)

                                            cachedFileHandler.deleteFileFromBoth(
                                                qrNote!!.documentId!!,
                                                CachedFileHandler.Category.Images,
                                                imageItem.file.name
                                            )
                                            d.dismiss()
                                        } catch (e: Exception) {
                                            Snackbar.make(
                                                requireView(),
                                                "Delete fail: " + e.message,
                                                Snackbar.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                    .setNegativeButton("Cancel") { d, _ ->
                                        d.dismiss()
                                    }
                                    .show()
                                dialog.dismiss()
                            }
                            .setNegativeButton("Cancel") { dialog, _ ->
                                dialog.dismiss()
                            }
                            .show()
                    }

                    is ImageItem.ResourceImage -> Unit
                }
            }
        }
    }

    private fun assertQrNoteIsInStorageRef() {
        if (qrNote?.documentId.isNullOrBlank()) {
            android.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                .setTitle("QrNote Image Menu")
                .setMessage("We have no QrNote to show ERROR 4711")
                .show()

            Log.e("SecondFragment", "assertQrNoteIsInStorageRef: qrNote or documentId is null.")
            return
        }

        storageRef.listAll().addOnSuccessListener { listResult ->
            val childExists = listResult.prefixes.any { it.name == qrNote!!.documentId }

            if (!childExists) {
                storageRef.child("${qrNote!!.documentId}/.emptyfile").putBytes(ByteArray(0))
            }
        }.addOnFailureListener { exception ->
            println("Error checking child: ${exception.message}")
        }
    }

    private fun setupFilesRecycler() {
        val documentId = qrNote?.documentId
        if (documentId.isNullOrBlank()) {
            Log.e(
                "SecondFragment",
                "setupFilesRecycler: qrNote or documentId is null. Did you forget to pass qrNote in arguments?"
            )
            binding.recyclerViewFiles.visibility = View.GONE
            binding.textViewEmptyDocuments.visibility = View.VISIBLE
            return
        }

        assertQrNoteIsInStorageRef()
        val fragment = this@SecondFragment

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val listResult = cachedFileHandler.getFileNamesFromCloud(
                    documentId,
                    CachedFileHandler.Category.Documents
                )

                val stringList = listResult.toMutableList()
                if (stringList.isEmpty()) {
                    binding.recyclerViewFiles.visibility = View.GONE
                    binding.textViewEmptyDocuments.visibility = View.VISIBLE
                } else {
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
                            documentId,
                            CachedFileHandler.Category.Documents,
                            fileName
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
                    AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                        .setTitle("QrNote Doc Options")
                        .setMessage("What do you want to do with doc $fileName ?")
                        .setPositiveButton("Delete") { optionsDialog, _ ->
                            AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                                .setTitle("Delete Document")
                                .setMessage("Are you sure you want to delete this document?")
                                .setPositiveButton("Yes") { confirmDialog, _ ->
                                    val cachedFileHandler =
                                        CachedFileHandler(storageRef, requireContext())
                                    cachedFileHandler.deleteFileFromBoth(
                                        documentId,
                                        CachedFileHandler.Category.Documents,
                                        fileName
                                    )
                                    try {
                                        storageRef.child(documentId)
                                            .child(CachedFileHandler.Category.Documents.name)
                                            .child(fileName)
                                            .delete()

                                        stringList.removeAt(position)
                                        stringAdapter.notifyDataSetChanged()
                                    } catch (e: Exception) {
                                        Snackbar.make(
                                            requireView(),
                                            "Delete fail: " + e.message,
                                            Snackbar.LENGTH_LONG
                                        ).show()
                                    }
                                    confirmDialog.dismiss()
                                }
                                .setNegativeButton("Cancel") { confirmDialog, _ ->
                                    confirmDialog.dismiss()
                                }
                                .show()

                            optionsDialog.dismiss()
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

    private fun createImageFile(): File {
        val subfolderDir = qrNote?.ImageSubfolder()

        if (subfolderDir == null) {
            binding.recyclerViewImages.post {
                Snackbar.make(
                    requireView(),
                    "subfolderDir == null!",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            return File("")
        }

        return File.createTempFile(
            "JPEG_${System.currentTimeMillis()}_",
            ".jpg",
            subfolderDir
        )
    }

    private fun checkStoragePermission(openGallery: Boolean) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            @Suppress("DEPRECATION") Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            if (openGallery) selectImageFromGallery()
            return
        }

        pendingOpenGalleryAfterPermission = openGallery
        readImagesPermissionLauncher.launch(permission)
    }

    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchQRCodeScanner() {
        scanLauncher.launch(buildQrScanOptions("Scan a new QR code"))
    }

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            Snackbar.make(requireView(), "Scan cancelled", Snackbar.LENGTH_SHORT).show()
        } else {
            val scannedQrCode = result.contents
            Snackbar.make(requireView(), "Scanned: $scannedQrCode", Snackbar.LENGTH_SHORT).show()
            val notesCollection = FirestoreManager.getUserNotesCollection()
            if (notesCollection == null) {
                Snackbar.make(
                    requireView(),
                    "User not logged in? Notes empty",
                    Snackbar.LENGTH_SHORT
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
                            .update(QrNote::qrCode.name, scannedQrCode)
                            .addOnSuccessListener {
                                showTallSnackbar(
                                    requireView(),
                                    "QR code updated successfully.",
                                    Snackbar.LENGTH_SHORT
                                )
                            }
                            .addOnFailureListener { e ->
                                showTallSnackbar(
                                    requireView(),
                                    "Failed to update QR code: ${e.message}",
                                    Snackbar.LENGTH_LONG
                                )
                            }
                    }
                }
                .addOnFailureListener { e ->
                    showTallSnackbar(
                        requireView(),
                        "Failed to query for QR code: ${e.message}",
                        Snackbar.LENGTH_LONG
                    )
                }
        }
    }

    private fun openCamera() {
        try {
            val photoFile = createImageFile()
            pendingPhotoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )
            takePictureLauncher.launch(pendingPhotoUri)
        } catch (ex: IOException) {
            Log.e("SecondFragment", "Error creating image file", ex)
            Snackbar.make(requireView(), "Could not create image file.", Snackbar.LENGTH_LONG)
                .show()
        }
    }

    private fun scanFile(file: File) {
        MediaScannerConnection.scanFile(
            requireContext(),
            arrayOf(file.absolutePath),
            null
        ) { path, uri ->
            Log.d("MediaScan", "Scanned file: $path, URI: $uri")
        }
    }

    private fun selectImageFromGallery() {
        pickImageLauncher.launch("image/*")
    }

    private fun showTallSnackbar(
        anchor: View,
        message: String,
        duration: Int = Snackbar.LENGTH_LONG
    ) {
        val snackbar = Snackbar.make(anchor, message, duration)

        val snackView: View = snackbar.view

        val textView = snackView.findViewById<TextView>(R.id.snackbar_text)

        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        textView.maxLines = 5
        textView.textAlignment = View.TEXT_ALIGNMENT_CENTER

        val padH = dpToPx(24)
        val padV = dpToPx(18)
        textView.setPadding(padH, padV, padH, padV)
        snackView.minimumHeight = dpToPx(96)

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
                            requireContext(),
                            "User not logged in? Notes empty",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setPositiveButton
                    }
                    notesCollection.document(qrNote!!.documentId!!)
                        .update(qrNote!!::title.name, title)
                } else {
                    Snackbar.make(
                        requireView(),
                        "Title cannot be empty",
                        Toast.LENGTH_SHORT
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
                requireContext(),
                "User not logged in? Notes empty",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val docRef = notesCollection.document(qrNote!!.documentId!!)
        firestoreListener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("FirestoreListener", "Listen failed.", error)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                qrNote = snapshot.toObject(QrNote::class.java)
                qrNote?.documentId = snapshot.id
                updateUiWithNewData()
            } else {
                Log.d("FirestoreListener", "Current data: null")
            }
        }

        secondSharedViewModel.requestedAction.observe(viewLifecycleOwner) { action ->
            when (action) {
                NoteAction.SCAN_NEW_QR_CODE -> {
                    launchQRCodeScanner()
                    secondSharedViewModel.onActionHandled()
                }

                NoteAction.NONE -> Unit
                else -> Unit
            }
        }
    }

    private fun updateUiWithNewData() {
        try {
            binding.tileText.text = qrNote?.title
            binding.tileText.requestFocus()
            binding.qrCode.text = "QR: " + qrNote?.qrCode
        } catch (_: Exception) {
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        firestoreListener?.remove()
        firestoreListener = null
        _binding = null
    }

    private fun saveText() {
        val notesCollection = FirestoreManager.getUserNotesCollection()
        if (notesCollection == null) {
            Toast.makeText(
                requireContext(),
                "User not logged in? Notes empty",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        notesCollection.document(qrNote!!.documentId!!)
            .update(qrNote!!::content.name, _binding!!.edittextSecond.text.toString())
    }

    private val allowedDocumentMimeTypesAll = arrayOf(
        "application/pdf",
        "image/jpeg",
        "image/png",
        "text/plain",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    )
}
