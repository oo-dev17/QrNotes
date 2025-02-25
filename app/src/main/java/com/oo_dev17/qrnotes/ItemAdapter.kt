package com.oo_dev17.qrnotes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView

class ItemAdapter(
    private val items: List<QrNote>
) : RecyclerView.Adapter<ItemAdapter.ItemViewHolder>() {

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.item_title)
        val descriptionTextView: TextView = itemView.findViewById(R.id.item_description)
        val uidTextView: TextView = itemView.findViewById(R.id.item_uid)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.item_layout, parent, false)

        return ItemViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val qrNote = items[position]
        holder.titleTextView.text = qrNote.title
        holder.descriptionTextView.text =String.format("%20.20s",  qrNote.content)
        holder.uidTextView.text = qrNote.getShortHash()

        // Set the click listener on the item view
        holder.itemView.setOnClickListener {
            // Create a Bundle
            val bundle = Bundle()
            // Put the QrNote into the Bundle
            bundle.putParcelable("qrNote", qrNote)
          //  (requireActivity() as MainActivity).sharedQrNote = item
            // Navigate to SecondFragment with the Bundle
            holder.itemView.findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment, bundle)
        }

        // Load image using a library like Glide or Coil
        // Glide.with(holder.itemView.context).load(currentItem.imageUrl).into(holder.itemImage)
    }

    override fun getItemCount(): Int {
        return items.size
    }
}