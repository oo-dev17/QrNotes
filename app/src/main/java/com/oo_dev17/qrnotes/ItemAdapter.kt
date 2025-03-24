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

class ItemAdapter(
    public var items: List<QrNote>,
    private val itemLongClickListener: ItemClickListener
) : RecyclerView.Adapter<ItemAdapter.ItemViewHolder>() {

    private var itemView: View? = null

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.item_title)
        val uidTextView: TextView = itemView.findViewById(R.id.item_uid)
        val item_image: ImageView = itemView.findViewById(R.id.item_image)
        val qrCodeTextView: TextView = itemView.findViewById(R.id.item_qrCode)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.item_layout, parent, false)

        return ItemViewHolder(itemView!!)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val qrNote = items[position]
        holder.titleTextView.text = qrNote.title
        //holder.descriptionTextView.text = String.format("%20.20s", qrNote.content)
        holder.uidTextView.text = qrNote.documentId
        holder.qrCodeTextView.text = qrNote.qrCode

        // Set the click listener on the item view
        holder.itemView.setOnClickListener {
            // Create a Bundle
            val bundle = Bundle()
            // Put the QrNote into the Bundle
            bundle.putParcelable("qrNote", qrNote)
            //  (requireActivity() as MainActivity).sharedQrNote = item
            // Navigate to SecondFragment with the Bundle
            holder.itemView.findNavController()
                .navigate(R.id.action_FirstFragment_to_SecondFragment, bundle)
        }
        holder.itemView.setOnLongClickListener {
            itemLongClickListener.showQrNoteOptions(qrNote)
            true // Consume the long click event
        }

        val firstPics = qrNote.retrieveImageFiles().first
        if (firstPics.isNotEmpty()) {
            // Load image using a library like Glide or Coil
            Glide.with(holder.itemView.context).load(firstPics.first())
                .into(holder.item_image)
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }
}