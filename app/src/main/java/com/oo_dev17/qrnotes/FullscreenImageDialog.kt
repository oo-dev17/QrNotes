import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.ImageView
import com.oo_dev17.qrnotes.R

class FullscreenImageDialog(context: Context, private val imageResId: Int) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_fullscreen_image)

        val fullscreenImageView: ImageView = findViewById(R.id.fullscreenImageView)
        fullscreenImageView.setImageResource(imageResId)

        // Close the dialog when the image is clicked
        fullscreenImageView.setOnClickListener {
            dismiss()
        }
    }
}