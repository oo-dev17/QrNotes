package com.oo_dev17.qrnotes

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DocumentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val stringTextView: TextView = itemView.findViewById(R.id.stringTextView)
    val logoTextView: TextView = itemView.findViewById(R.id.logoTextView)
}