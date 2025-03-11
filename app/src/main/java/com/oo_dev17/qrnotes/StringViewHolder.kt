package com.oo_dev17.qrnotes

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StringViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val stringTextView: TextView = itemView.findViewById(R.id.stringTextView)
}