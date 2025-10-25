import android.app.Dialog
import com.oo_dev17.qrnotes.R

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import java.io.File


class FullscreenImageDialog(context: Context, private val imageFile: File) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_fullscreen_image)

        // Make the dialog window fullscreen
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window?.setBackgroundDrawableResource(android.R.color.transparent)

        val fullscreenImageView: ImageView = findViewById(R.id.fullscreenImageView)!!
        val fullscreenImageViewText: TextView = findViewById(R.id.fullscreenImageViewText)!!
        fullscreenImageViewText.text = imageFile.name

        // Load the image from the file into the ImageView using Glide
        Glide.with(this.context)
            .load(imageFile)
            .into(fullscreenImageView)

        // Close the dialog when the image is clicked
        fullscreenImageView.setOnClickListener {
            dismiss()
        }
    }
}