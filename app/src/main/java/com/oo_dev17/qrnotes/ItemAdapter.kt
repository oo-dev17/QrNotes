package com.oo_dev17.qrnotes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ItemAdapter(
    private val items: List<QrNote>, private val listener: ItemClickListener
) : RecyclerView.Adapter<ItemAdapter.ItemViewHolder>() {

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.item_title)
        val descriptionTextView: TextView = itemView.findViewById(R.id.item_description)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.item_layout, parent, false)

        return ItemViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val currentItem = items[position]
        holder.titleTextView.text = currentItem.title
        holder.descriptionTextView.text = currentItem.content

        // Set the click listener on the item view
        holder.itemView.setOnClickListener {
            listener.onItemClicked(currentItem)
        }

        // Load image using a library like Glide or Coil
        // Glide.with(holder.itemView.context).load(currentItem.imageUrl).into(holder.itemImage)
    }

    override fun getItemCount(): Int {
        return items.size
    }
}