package com.oo_dev17.qrnotes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class StringAdapter(private val stringList: List<String>) :
    RecyclerView.Adapter<StringViewHolder>() {

    var onItemClick: ((String) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StringViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.fragment_second, parent, false)
        return StringViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: StringViewHolder, position: Int) {
        val stringEntry = stringList[position]
        holder.stringTextView.text = stringEntry

        // Set click listener for the item
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(stringEntry)
        }
    }

    override fun getItemCount(): Int {
        return stringList.size
    }
}