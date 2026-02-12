package com.oo_dev17.qrnotes

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FloatingAddImage(context: Context) : Dialog(context), ItemSelectListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.floating_image_add)

        // Set up RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view_noteString)
        recyclerView.layoutManager = LinearLayoutManager(context)
        val notesCollection = FirestoreManager.getUserNotesCollection()
        if (notesCollection == null) {
            recyclerView.adapter = NotesStringAdapter(emptyList(), this)
            return
        }

        notesCollection.get().addOnSuccessListener { result ->
            // IMPORTANT:
            // Firestore document id is NOT included in toObject(QrNote::class.java) unless you store
            // it explicitly as a field. Copy snapshot.id into the model so downstream code can use it.
            val notes = result.documents.map { doc ->
                doc.toObject(QrNote::class.java)?.apply { documentId = doc.id }
            }.filterNotNull().toMutableList()

            recyclerView.adapter = NotesStringAdapter(notes, this)
        }

        // Add a close button to dismiss the dialog
        findViewById<Button>(R.id.button_cancel).setOnClickListener {
            dismiss()
        }
    }

    override fun onItemClicked(item: QrNote) {
        TODO("Not yet implemented")
    }
}

interface ItemSelectListener {
    fun onItemClicked(item: QrNote)
}
