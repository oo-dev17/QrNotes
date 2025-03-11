import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.oo_dev17.qrnotes.ImageItem
import com.oo_dev17.qrnotes.R
import java.io.File

class ImageAdapter(public val imageItems: MutableList<ImageItem>) :
    RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    var onItemClick: ((ImageItem) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageItem = imageItems[position]

        when (imageItem) {
            is ImageItem.FileImage -> {
                // Load image from file using Glide
                Glide.with(holder.itemView.context)
                    .load(imageItem.file)
                    .into(holder.imageView)
            }

            is ImageItem.ResourceImage -> {
                // Load image from resource ID
                holder.imageView.setImageResource(imageItem.resId)
            }

            is ImageItem.FileString -> {
                // Load image from file using Glide
                Glide.with(holder.itemView.context)
                    .load(File(imageItem.resId))
                    .into(holder.imageView)
            }
    }

            // Set click listener for the item
            holder.itemView.setOnClickListener {
                onItemClick?.invoke(imageItem)
            }
        }

    override fun getItemCount(): Int {
        return imageItems.size
    }

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
    }
}