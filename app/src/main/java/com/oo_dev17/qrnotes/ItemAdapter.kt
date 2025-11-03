package com.oo_dev17.qrnotes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ItemAdapter(
    var allQrNotes: List<QrNote>,
    private val itemLongClickListener: ItemClickListener,
    private val coroutineScope: CoroutineScope?,
    private val cachedFileHandler: CachedFileHandler?
) : RecyclerView.Adapter<ItemAdapter.ItemViewHolder>() {

    private var itemView: View? = null

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.item_title)
        val uidTextView: TextView = itemView.findViewById(R.id.item_uid)
        val itemImage: ImageView = itemView.findViewById(R.id.item_image)
        val qrCodeTextView: TextView = itemView.findViewById(R.id.item_qrCode)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        itemView =
            LayoutInflater.from(parent.context).inflate(R.layout.item_layout, parent, false)

        return ItemViewHolder(itemView!!)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val qrNote = allQrNotes[position]
        holder.titleTextView.text = qrNote.title
        //holder.descriptionTextView.text = String.format("%20.20s", qrNote.content)
        holder.uidTextView.text = qrNote.documentId
        holder.qrCodeTextView.text = qrNote.qrCode

        if (position % 2 == 1) {
            holder.itemView.setBackgroundColor("#FF222222".toColorInt());
            //  holder.imageView.setBackgroundColor(Color.parseColor("#FFFFFF"));
        } else {
            holder.itemView.setBackgroundColor("#FF000000".toColorInt());
            //  holder.imageView.setBackgroundColor(Color.parseColor("#FFFAF8FD"));
        }

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
        coroutineScope.launch {
            val firstPics = withContext(Dispatchers.IO) {
                qrNote.retrieveImageFiles(cachedFileHandler) // Perform network/disk operation on IO thread
            }
            withContext(Dispatchers.Main) { //go back to main thread
                // Find the picture with the specified name. Returns null if not found.
                val specificGalleryPic = firstPics.firstOrNull { it.name == qrNote.galleryPic }
                val galleryPic = specificGalleryPic ?: firstPics.firstOrNull()
                if (galleryPic != null) {
                    Glide.with(holder.itemView.context).load(galleryPic)
                        .into(holder.itemImage)
                }
            }
        }
    }


override fun getItemCount(): Int {
    return allQrNotes.size
}
}