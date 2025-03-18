package com.oo_dev17.qrnotes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class NotesStringAdapter(
    public var items: List<QrNote>,
    private val itemSelectListener: ItemSelectListener
) : RecyclerView.Adapter<NotesStringAdapter.ViewHolder>() {

    private var itemView: View? = null
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.recycler_view_noteString)
    }


    // Inflate the item layout and create the ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_layout, parent, false)
        return ViewHolder(view)
    }

    // Bind data to the ViewHolder
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = items[position].title
    }

    // Return the size of the dataset
    override fun getItemCount(): Int {
        return items.size
    }
}