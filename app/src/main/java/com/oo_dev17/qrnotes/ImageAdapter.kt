package com.oo_dev17.qrnotes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class ImageAdapter(private val imageResIds: List<Int>) :
    RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    // Listener for item clicks
    var onItemClick: ((Int) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageResId = imageResIds[position]
        holder.imageView.setImageResource(imageResId)

        // Set click listener for the item
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(imageResId)
        }
    }

    override fun getItemCount(): Int {
        return imageResIds.size
    }

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
    }
}