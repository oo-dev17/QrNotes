package com.oo_dev17.qrnotes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class DocumentAdapter( val stringList: MutableList<String>) :
    RecyclerView.Adapter<DocumentViewHolder>() {

    var onItemClick: ((String) -> Unit)? = null
    var onItemLongClick: ((String, Int) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocumentViewHolder {
        val documentView =
            LayoutInflater.from(parent.context).inflate(R.layout.item_document, parent, false)
        return DocumentViewHolder(documentView)
    }

    override fun onBindViewHolder(holder: DocumentViewHolder, position: Int) {
        val stringEntry = stringList[position]
        holder.stringTextView.text = stringEntry
        holder.logoTextView.text = stringEntry.substring(stringEntry.length -3, stringEntry.length).uppercase()

        // Set click listener for the item
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(stringEntry)
        }
        holder.itemView.setOnLongClickListener {
            onItemLongClick?.invoke(stringEntry, position)
            true
        }
    }

    override fun getItemCount(): Int {
        return stringList.size
    }
}