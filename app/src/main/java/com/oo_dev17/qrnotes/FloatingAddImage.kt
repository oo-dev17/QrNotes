package com.oo_dev17.qrnotes

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Button
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

class FloatingAddImage(context: Context) : Dialog(context),  ItemSelectListener{

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.floating_image_add)

        // Set up RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view_noteString)
        recyclerView.layoutManager = LinearLayoutManager(context)
        Firebase.firestore.collection("qrNotes").get().addOnSuccessListener { result ->

        recyclerView.adapter = NotesStringAdapter(result.map { it.toObject(QrNote::class.java) }.toMutableList(),  this)}

        // Add a close button to dismiss the dialog
        findViewById<Button>(R.id.button_cancel).setOnClickListener {
            dismiss()
        }
    }

    override fun onItemClicked(item: QrNote) {
        TODO("Not yet implemented")
    }
}

open interface ItemSelectListener {

    fun onItemClicked(item: QrNote)
}
