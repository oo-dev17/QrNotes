package com.oo_dev17.qrnotes

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import com.oo_dev17.qrnotes.databinding.ActivitySelectNoteBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.reflect.KFunction1

class SelectNoteActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySelectNoteBinding
    private lateinit var itemAdapter: ItemAdapter
    private var sharedFileUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectNoteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarSelectNote)

        // 1. Get the shared file URI from the intent
        handleIncomingIntent()

        // 2. Set up the RecyclerView to show the notes
        setupRecyclerView()

        // 3. Load the notes from Firestore
        loadNotes()
    }

    private fun handleIncomingIntent() {
        if (intent?.action == Intent.ACTION_SEND) {
            // It's safe to cast here because of the intent filter in the manifest
            sharedFileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        }
        // Handle SEND_MULTIPLE if you need to support sharing multiple files at once
        // else if (intent?.action == Intent.ACTION_SEND_MULTIPLE) { ... }

        if (sharedFileUri == null) {
            Toast.makeText(this, "No file to share.", Toast.LENGTH_SHORT).show()
            finish() // Close the activity if no file was received
        }
    }

    private fun onNoteClicked(selectedQrNote: QrNote) {
        // A note has been chosen! Now process the file.
        processSharedFile(selectedQrNote)
    }

    fun itemAdapterOnItemClick(selectedQrNote: QrNote) = {
        // A note has been chosen! Now process the file.
        processSharedFile(selectedQrNote)
    }

    private fun setupRecyclerView() {
        itemAdapter = ItemAdapter(ArrayList(), null, null, null) // Start with an empty list
        itemAdapter.shortClickListener = this::onNoteClicked
        binding.recyclerViewNoteSelection.adapter = itemAdapter
        binding.recyclerViewNoteSelection.layoutManager = LinearLayoutManager(this)
    }

    // In SelectNoteActivity.kt
    private fun loadNotes() {
        lifecycleScope.launch {
            try {
                // Use the singleton to get the collection reference
                val notesCollection = FirestoreManager.getUserNotesCollection()

                if (notesCollection == null) {
                    Toast.makeText(
                        this@SelectNoteActivity,
                        "User not logged in.",
                        Toast.LENGTH_SHORT
                    ).show()
                    itemAdapter.updateList(emptyList()) // Clear the list
                    return@launch
                }

                val snapshot = notesCollection.get().await()

                val notes = snapshot.documents.map { document ->
                    val note = document.toObject(QrNote::class.java)
                    note?.documentId = document.id
                    note
                }.filterNotNull()

                itemAdapter.updateList(notes)
            } catch (e: Exception) {
                Toast.makeText(
                    this@SelectNoteActivity,
                    "Failed to load notes: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun processSharedFile(targetNote: QrNote) {
        Toast.makeText(this, "Adding file to '${targetNote.title}'...", Toast.LENGTH_LONG).show()

        var cachedFileHandler = CachedFileHandler(Firebase.storage.reference, this)

        if (sharedFileUri.toString().uppercase().endsWith("PDF")) {
            val file = cachedFileHandler.storeFileInCache(
                targetNote.documentId!!,
                CachedFileHandler.Category.Documents,
                sharedFileUri.toString().substringAfterLast("/"),
                sharedFileUri!!
            )
            cachedFileHandler.uploadToCloud(targetNote, file, CachedFileHandler.Category.Documents)
            Toast.makeText(this, "PDF added to '${targetNote.title}'", Toast.LENGTH_LONG)
                .show()
            setResult(Activity.RESULT_OK)
        } else {
            if (cachedFileHandler.storeSelectedImageInCloudAndCache(
                    sharedFileUri!!,
                    targetNote,
                    null
                )
            ) {
                Toast.makeText(this, "Image added to '${targetNote.title}'", Toast.LENGTH_LONG)
                    .show()
                setResult(Activity.RESULT_OK)
            } else {
                Toast.makeText(
                    this,
                    "Failed to add image to '${targetNote.title}'",
                    Toast.LENGTH_LONG
                )
                    .show()
                setResult(Activity.RESULT_CANCELED)
            }
        }
        // HERE: You would reuse your existing file handling logic.
        // You'll need instances of CachedFileHandler, FileCache, etc.
        // This is a simplified example.

        // 1. Get filename from URI
        // 2. Use FileCache to copy the file to your app's local storage
        // 3. Use CachedFileHandler to upload the file to Firebase Storage
        // 4. (Optional) You could navigate to SecondFragment for that note

        // After processing, finish this activity
        finish()
    }

    override fun onBackPressed() {
        setResult(Activity.RESULT_CANCELED)
        super.onBackPressed()
    }
}
