package com.oo_dev17.qrnotes

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.storage.storage
import com.journeyapps.barcodescanner.ScanContract
import com.oo_dev17.qrnotes.databinding.FragmentFirstBinding
import kotlinx.coroutines.CoroutineScope
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment(), ItemClickListener, NewQrNoteListener {

    private lateinit var auth: FirebaseAuth
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    private lateinit var itemAdapter: ItemAdapter
    private lateinit var qrNotes: MutableList<QrNote>
    private var _binding: FragmentFirstBinding? = null
    private lateinit var coroutineScope: CoroutineScope

    private var notesCollection = FirestoreManager.getUserNotesCollection()

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private val readImagesPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Toast.makeText(
                    requireContext(),
                    "Storage permission denied. Some features may not work.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        auth = Firebase.auth
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        checkStoragePermission()

        if (notesCollection == null) {
            Toast.makeText(requireContext(), "Login required to access notes.", Toast.LENGTH_LONG)
                .show()
            // Optionally, you could disable UI elements here if you don't navigate away.
            binding.fabNew.isEnabled = false
            binding.fabScanQr.isEnabled = false
        }

        binding.fabNew.setOnClickListener { _ ->
            showTitleInputDialog()
        }
        binding.fabScanQr.setOnClickListener { _ ->
            launchQRCodeScanner()
        }
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // Start listening when the fragment becomes visible
        authStateListener?.let { auth.addAuthStateListener(it) }
    }

    override fun onStop() {
        super.onStop()
        // Stop listening when the fragment is no longer visible to prevent memory leaks
        authStateListener?.let { auth.removeAuthStateListener(it) }
    }

    private fun refreshDataForCurrentUser() {
        // If the view is already destroyed (or not created yet), binding is null.
        // This can happen if the auth state callback fires after onDestroyView.
        if (_binding == null) return

        // Step 1: Re-evaluate the user's login status to get the correct collection reference.
        notesCollection = FirestoreManager.getUserNotesCollection()

        // Step 2: Check if the user is now logged in.
        if (notesCollection != null) {
            // User is logged in, enable UI and fetch their notes from Firestore.
            binding.fabNew.isEnabled = true
            binding.fabScanQr.isEnabled = true
            binding.searchText.isEnabled = true
            getAllQrNotes() // This will now use the correct user-specific collection
        } else {

            // User is not logged in (or logged out), show a guest state.
            Toast.makeText(requireContext(), "Please log in to see your notes.", Toast.LENGTH_SHORT)
                .show()
            itemAdapter.updateList(emptyList()) // Clear the list
            binding.fabNew.isEnabled = false
            binding.fabScanQr.isEnabled = false
            binding.searchText.isEnabled = false
        }
    }

    private fun showTitleInputDialog(newQrCode: String? = null) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
        builder.setTitle("Create New Note")
        builder.setMessage("Enter a title for the note")

        // Set up the input field
        val input = EditText(requireContext())
        input.hint = "Title"
        builder.setView(input)

        // Set up the buttons
        builder.setPositiveButton("Create") { dialog, _ ->
            val title = input.text.toString()
            if (title.isNotEmpty()) {
                val note = QrNote(title, "", qrCode = newQrCode ?: "")

                try {
                    notesCollection?.add(note)?.addOnSuccessListener { docRef ->
                        Log.d("FirestoreAccess", "Note added with ID: ${docRef.id}")
                        onNewQrNote(note)

                        val bundle = Bundle().apply {
                            putParcelable("qrNote", note)
                        }
                        findNavController().navigate(
                            R.id.action_FirstFragment_to_SecondFragment,
                            bundle
                        )

                    }?.addOnFailureListener { e ->
                        Log.w("Firestore", "Error adding note", e)
                    }
                } catch (e: Exception) {
                    Log.e("Firestore", "Error adding note", e)
                }
            } else {
                Toast.makeText(requireContext(), "Title cannot be empty", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun checkStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            @Suppress("DEPRECATION") Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED) {
            readImagesPermissionLauncher.launch(permission)
        }
    }

    private fun launchQRCodeScanner() {
        scanLauncher1.launch(buildQrScanOptions("Scan a QR code"))
    }

    private val scanLauncher1 = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val scannedData = result.contents
            try {
                val note = qrNotes.firstOrNull { it.qrCode == scannedData }
                if (note == null) {
                    Toast.makeText(
                        requireContext(),
                        "QR code not found: $scannedData",
                        Toast.LENGTH_SHORT
                    ).show()

                    AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                        .setTitle("QrNote Info")
                        .setMessage("QR code $scannedData not found, do you want to create a new note?")
                        .setPositiveButton("YES") { dialog, _ ->
                            dialog.dismiss()
                            showTitleInputDialog(scannedData)
                        }
                        .setNegativeButton("NO") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .show()

                    return@registerForActivityResult
                }

                val bundle = Bundle().apply {
                    putParcelable("qrNote", note)
                }
                findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment, bundle)
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "QR code not found: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                Log.d("AUTH_STATE", "User logged in: ${user.uid}")
                refreshDataForCurrentUser()
            } else {
                Log.d("AUTH_STATE", "User logged out.")
                refreshDataForCurrentUser()
            }
        }

        val recyclerView = binding.myRecyclerView
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        coroutineScope = viewLifecycleOwner.lifecycleScope
        val storageReference = Firebase.storage.reference
        val cachedFileHandler = CachedFileHandler(storageReference, requireContext())

        qrNotes = mutableListOf()
        itemAdapter = ItemAdapter(qrNotes, this, coroutineScope, cachedFileHandler)

        binding.myRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.myRecyclerView.adapter = itemAdapter

        binding.searchText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(needle: CharSequence?, start: Int, before: Int, count: Int) {
                val query = needle.toString().trim()
                itemAdapter.allQrNotes = if (query.isEmpty()) qrNotes else filterQrNotes(query)
                itemAdapter.notifyDataSetChanged()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterQrNotes(query: String): MutableList<QrNote> {

        return qrNotes.filter { qrNote ->
            qrNote.title?.contains(query, ignoreCase = true) == true || qrNote.content?.contains(
                query,
                ignoreCase = true
            ) == true || qrNote.qrCode.contains(
                query,
                ignoreCase = true
            ) || qrNote.documentId?.contains(
                query, ignoreCase = true
            ) == true
        }.toMutableList()
    }

    private fun getAllQrNotes() {
        notesCollection?.get()?.addOnSuccessListener { result ->
            Log.w("FirestoreAccess", "Successful getting QrNotes")
            try {
                val notes = result.map { documentSnapshot ->
                    val qrNote = documentSnapshot.toObject(QrNote::class.java)
                    qrNote.documentId = documentSnapshot.id
                    qrNote
                }.toMutableList()

                qrNotes.clear()
                qrNotes.addAll(notes)

                itemAdapter.updateList(notes)
                try {
                    itemAdapter.allQrNotes = qrNotes
                } catch (_: Exception) {
                    // ignore
                }
                itemAdapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Log.e("Firestore", "Error converting Firestore documents to QrNote objects", e)
            }

        }?.addOnFailureListener { exception ->
            Log.w("Firestore", "Error getting QrNotes", exception)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onItemClicked(item: QrNote) {
        val bundle = Bundle().apply {
            putParcelable("qrNote", item)
        }
        findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment, bundle)
    }

    override fun showQrNoteOptions(qrNote: QrNote) {
        val simpleDateFormat = SimpleDateFormat("dd.MM.yyyy - HH:mm:ss", Locale.getDefault())
        val dateString = simpleDateFormat.format(qrNote.creationDate)
        val info =
            "Doc.Id:${qrNote.documentId}\n GalleryPic:${qrNote.galleryPic}\n Created:${dateString}\nPictures: ${qrNote.pictureCount}\n  from cache: ${qrNote.picsLoadedFromCache}\n  from firestore: ${qrNote.picsLoadedFromFirestore}\n" +
                "Documents: ${qrNote.documentsCount}\n from cache ${qrNote.docsLoadedFromCache}\n from firestore ${qrNote.docsLoadedFromFirestore}"

        val builder = androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
        builder.setTitle("QrNote Options (FirstFragment)")
            .setMessage("$info\n\n\nWhat do you want to do with this QrNote?")
            .setPositiveButton("Delete") { dialog, _ ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Delete QrNote")
                    .setMessage("Are you sure you want to delete this QrNote?")
                    .setPositiveButton("Yes") { d, _ ->
                        deleteQrNote(qrNote)
                        d.dismiss()
                    }
                    .setNegativeButton("No") { d, _ -> d.dismiss() }
                    .show()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun deleteQrNote(qrNote: QrNote) {
        val position = qrNotes.indexOf(qrNote)
        if (position != -1) {
            qrNotes.removeAt(position)
            val notesCollection = FirestoreManager.getUserNotesCollection()
            if (notesCollection == null) {
                Toast.makeText(
                    requireContext(),
                    "User not logged in? Notes empty",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            notesCollection.document(qrNote.documentId!!).delete()
            itemAdapter.notifyItemRemoved(position)
            Toast.makeText(
                requireContext(),
                "QrNote ${qrNote.title} deleted",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onNewQrNote(qrNote: QrNote) {
        qrNotes.add(0, qrNote)
        itemAdapter.notifyItemInserted(0)
    }
}
