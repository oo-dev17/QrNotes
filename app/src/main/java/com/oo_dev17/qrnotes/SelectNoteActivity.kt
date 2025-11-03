package com.oo_dev17.qrnotes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.oo_dev17.qrnotes.databinding.ActivitySelectNoteBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SelectNoteActivity : AppCompatActivity(), ItemClickListener {

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
    fun itemAdapterOnItemClick (selectedQrNote: QrNote) = {
        // A note has been chosen! Now process the file.
        processSharedFile(selectedQrNote)
    }
    private fun setupRecyclerView() {
        itemAdapter = ItemAdapter(ArrayList(), this, null, null) // Start with an empty list
        binding.recyclerViewNoteSelection.adapter = itemAdapter
        binding.recyclerViewNoteSelection.layoutManager = LinearLayoutManager(this)


    }

    private fun loadNotes() {
        lifecycleScope.launch {
            try {
                val snapshot = Firebase.firestore.collection("qrNotes").get().await()
                val notes = snapshot.toObjects(QrNote::class.java)
                itemAdapter.updateList(notes) // Update the adapter with the fetched notes
            } catch (e: Exception) {
                Toast.makeText(this@SelectNoteActivity, "Failed to load notes: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processSharedFile(targetNote: QrNote) {
        Toast.makeText(this, "Adding file to '${targetNote.title}'...", Toast.LENGTH_LONG).show()

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
    override fun onItemClicked(qrNote: QrNote) {
        // A note has been chosen! Now process the file.
        processSharedFile(qrNote)
    }


    override fun showQrNoteOptions(qrNote: QrNote) {
        TODO("Not yet implemented")
    }

    override fun deleteQrNote(qrNote: QrNote) {
        TODO("Not yet implemented")
    }
}
