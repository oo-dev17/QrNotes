import android.app.Dialog
import com.oo_dev17.qrnotes.R

import android.content.Context
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide // Use Glide for image loading
import java.io.File

class FullscreenImageDialog(context: Context, private val imageFile: File) : AlertDialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_fullscreen_image)

        val fullscreenImageView: ImageView = findViewById(R.id.fullscreenImageView)!!

        // Load the image from the file into the ImageView using Glide
        Glide.with(context)
            .load(imageFile)
            .into(fullscreenImageView)

        // Close the dialog when the image is clicked
        fullscreenImageView.setOnClickListener {
            dismiss()
        }
    }
}