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
import androidx.core.app.ActivityCompat
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

    private val sharedViewModel: SharedViewModel by activityViewModels()

    private var notesCollection = FirestoreManager.getUserNotesCollection()

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

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
            // You could show a "login to see notes" message or load local-only notes.
            Toast.makeText(requireContext(), "Please log in to see your notes.", Toast.LENGTH_SHORT)
                .show()
            itemAdapter.updateList(emptyList()) // Clear the list
            binding.fabNew.isEnabled = false
            binding.fabScanQr.isEnabled = false
            binding.searchText.isEnabled = false
        }
    }

    private fun showTitleInputDialog(newQrCode: String? = null) {
        // Create an AlertDialog.Builder using the Activity context
        val builder =
            androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
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
                // Create a new QrNote object
                val note = QrNote(title, "", qrCode = newQrCode ?: "") // Empty content for now

                try {
                    notesCollection?.add(note)?.addOnSuccessListener { docRef ->
                        Log.d("FirestoreAccess", "Note added with ID: ${docRef.id}")
                        onNewQrNote(note)
                        // Jump to second fragment
                        val bundle = Bundle()
                        // Put the QrNote into the Bundle
                        bundle.putParcelable("qrNote", note)
                        //  (requireActivity() as MainActivity).sharedQrNote = item
                        // Navigate to SecondFragment with the Bundle
                        val navController = findNavController()
                        navController.navigate(R.id.action_FirstFragment_to_SecondFragment, bundle)

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

        // Show the dialog
        builder.show()
    }

    private val REQUEST_CODE_READ_EXTERNAL_STORAGE = 100
    private fun checkStoragePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Use READ_MEDIA_IMAGES for Android 13 and above
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
                requireActivity(), arrayOf(permission), REQUEST_CODE_READ_EXTERNAL_STORAGE
            )
        }
    }

    private fun launchQRCodeScanner() {
        scanLauncher1.launch(buildQrScanOptions("Scan a QR code"))
    }

    private val scanLauncher1 = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val scannedData = result.contents // Get the scanned QR code data
            try {
                val note = qrNotes.firstOrNull() { note -> note.qrCode == scannedData }
                if (note == null) {
                    Toast.makeText(
                        requireContext(), "QR code not found: $scannedData", Toast.LENGTH_SHORT
                    ).show()
                    val builder =
                        android.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
                    builder.setTitle("QrNote Info")
                        .setMessage("QR code $scannedData not found, do you want to create a new note?")
                        .setPositiveButton("YES") { dialog, _ ->
                            dialog.dismiss()
                            showTitleInputDialog(scannedData)
                        }.setNegativeButton("NO") { dialog, _ ->
                            dialog.dismiss()
                        }.show()
                    return@registerForActivityResult
                }
                val bundle = Bundle()
                bundle.putParcelable("qrNote", note)
                findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment, bundle)
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(), "QR code not found: ${e.message}", Toast.LENGTH_SHORT
                ).show()
            }
        }

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Define what happens when the user's login state changes
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                // User is signed IN. Fetch their data.
                Log.d("AUTH_STATE", "User logged in: ${user.uid}")
                refreshDataForCurrentUser()
            } else {
                // User is signed OUT. Clear the data.
                Log.d("AUTH_STATE", "User logged out.")
                refreshDataForCurrentUser() // This will handle the logged-out state
            }
        }

        val recyclerView = binding.myRecyclerView // Assuming you have a RecyclerView in your layout
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        coroutineScope = viewLifecycleOwner.lifecycleScope
        val storageReference = Firebase.storage.reference
        val cachedFileHandler = CachedFileHandler(storageReference, requireContext())
        qrNotes = mutableListOf()

        itemAdapter = ItemAdapter(qrNotes, this, coroutineScope, cachedFileHandler)

        binding.myRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.myRecyclerView.adapter = itemAdapter

        // 3. Setup listeners that depend on the adapter

        binding.searchText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(needle: CharSequence?, start: Int, before: Int, count: Int) {

                val query = needle.toString().trim()
                if (query.isEmpty()) {
                    itemAdapter.allQrNotes = qrNotes
                } else {
                    itemAdapter.allQrNotes = filterQrNotes(query)
                }
                itemAdapter.run { notifyDataSetChanged() }
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
            ) == true //|| qrNote.allDocuments.any({ it.contains(query, ignoreCase = true) })
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

                // Keep the fragment's master list in sync with fetched notes so other
                // code (like the scanner) can find notes.
                qrNotes.clear()
                qrNotes.addAll(notes)

                // Update adapter and ensure its searchable list references the same data
                itemAdapter.updateList(notes)
                try {
                    // Some adapter implementations expose a mutable allQrNotes field.
                    // Keep it in sync if available.
                    itemAdapter.allQrNotes = qrNotes
                } catch (e: Exception) {
                    // ignore if adapter doesn't expose that field
                }
                itemAdapter.notifyDataSetChanged()
            } catch (e: Exception) {
                Log.e(
                    "Firestore", "Error converting Firestore documents to QrNote objects", e
                )
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
        //  (requireActivity() as MainActivity).sharedQrNote = item
        findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
    }

    override fun showQrNoteOptions(qrNote: QrNote) {
        val simpleDateFormat = SimpleDateFormat("dd.MM.yyyy - HH:mm:ss")
        val dateString = simpleDateFormat.format(qrNote.creationDate)
        val info =
            "Doc.Id:${qrNote.documentId}\n GalleryPic:${qrNote.galleryPic}\n Created:${dateString}\nPictures: ${qrNote.pictureCount}\n  from cache: ${qrNote.picsLoadedFromCache}\n  from firestore: ${qrNote.picsLoadedFromFirestore}\n" + "Documents: ${qrNote.documentsCount}\n from cache ${qrNote.docsLoadedFromCache}\n from firestore ${qrNote.docsLoadedFromFirestore}"

        val builder =
            androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.AlertDialogTheme)
        builder.setTitle("QrNote Options (FirstFragment)")
            .setMessage("$info\n\n\nWhat do you want to do with this QrNote?")
            .setPositiveButton("Delete") { dialog, _ ->
                AlertDialog.Builder(requireContext()).setTitle("Delete QrNote")
                    .setMessage("Are you sure you want to delete this QrNote?")
                    .setPositiveButton("Yes") { dialog, _ ->
                        deleteQrNote(qrNote)
                        dialog.dismiss()
                    }.setNegativeButton("No") { dialog, _ ->
                        dialog.dismiss()
                    }.show()
                dialog.dismiss()
            }

            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    override fun deleteQrNote(qrNote: QrNote) {
        // TODO Delete files of qrNote
        val position = qrNotes.indexOf(qrNote)
        if (position != -1) {
            qrNotes.removeAt(position)
            val notesCollection = FirestoreManager.getUserNotesCollection()
            if (notesCollection == null) {
                Toast.makeText(
                    requireContext(), "User not logged in? Notes empty", Toast.LENGTH_SHORT
                ).show()
                return
            }
            notesCollection.document(qrNote.documentId!!).delete()
            itemAdapter.notifyItemRemoved(position)
            Toast.makeText(
                requireContext(), "QrNote ${qrNote.title} deleted", Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onNewQrNote(qrNote: QrNote) {
        qrNotes.add(0, qrNote)
        itemAdapter.notifyItemInserted(0)
    }
}
