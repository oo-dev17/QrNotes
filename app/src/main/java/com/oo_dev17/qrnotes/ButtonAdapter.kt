package com.oo_dev17.qrnotes

// In ButtonAdapter.kt
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.oo_dev17.qrnotes.R
import android.widget.ImageView
class ButtonAdapter : RecyclerView.Adapter<ButtonAdapter.ButtonViewHolder>() {

    // Listeners for the button clicks
    var onAddCameraClick: (() -> Unit)? = null
    var onAddGalleryClick: (() -> Unit)? = null

    // This adapter will always have exactly two items: Camera and Gallery
    private val buttonTypes = listOf("camera", "gallery")

    inner class ButtonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonViewHolder {
        // We can use the same simple layout for both buttons, or different ones.
        // Let's use a generic layout and change the icon in onBindViewHolder.
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_add_button, parent, false)
        return ButtonViewHolder(view)
    }

    override fun onBindViewHolder(holder: ButtonViewHolder, position: Int) {
        val buttonType = buttonTypes[position]
        val imageView = holder.itemView.findViewById<ImageView>(R.id.button_image)

        if (buttonType == "camera") {
            imageView.setImageResource(R.drawable.ic_menu_camera)
            holder.itemView.setOnClickListener {
                onAddCameraClick?.invoke()
            }
        } else { // "gallery"
            imageView.setImageResource(R.drawable.item_menu_gallery)
            holder.itemView.setOnClickListener {
                onAddGalleryClick?.invoke()
            }
        }
    }

    override fun getItemCount(): Int {
        // This adapter always displays exactly two items.
        return buttonTypes.size
    }
}