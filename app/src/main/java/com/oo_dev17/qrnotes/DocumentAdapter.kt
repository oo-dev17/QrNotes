package com.oo_dev17.qrnotes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView

class DocumentAdapter(val stringList: MutableList<String>) :
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
        val charPosition = stringEntry.lastIndexOf('.')

        if (position % 2 == 1) {
            holder.itemView.setBackgroundColor("#FF222222".toColorInt());
            //  holder.imageView.setBackgroundColor(Color.parseColor("#FFFFFF"));
        } else {
            holder.itemView.setBackgroundColor("#FF333333".toColorInt());
            //  holder.imageView.setBackgroundColor(Color.parseColor("#FFFAF8FD"));
        }

        holder.logoTextView.text = if (charPosition == -1) "-" else stringEntry.substring(
            charPosition + 1, stringEntry.length
        ).uppercase()

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
