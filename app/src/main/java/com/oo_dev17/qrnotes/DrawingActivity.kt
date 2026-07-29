package com.oo_dev17.qrnotes

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.File

class DrawingActivity : AppCompatActivity() {

    private var qrNote: QrNote? = null
    private lateinit var cachedFileHandler: CachedFileHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drawing)

        // Retrieve passed QrNote (if provided) so we can save into its image folder
        val bundle = intent.extras
        qrNote = if (bundle != null && bundle.containsKey("qrNote")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getParcelable("qrNote", QrNote::class.java)
            } else {
                @Suppress("DEPRECATION") bundle.getParcelable("qrNote")
            }
        } else null

        // create a CachedFileHandler locally (cannot pass non-parcelable handler via Intent)
        val storageRef: StorageReference = FirebaseStorage.getInstance().reference
        cachedFileHandler = CachedFileHandler(storageRef, this)

        val drawingView = findViewById<DrawingView>(R.id.drawingView)
        val btnClear = findViewById<Button>(R.id.btnClear)
        val btnColor = findViewById<com.google.android.material.button.MaterialButton>(R.id.btnColor)
        val btnGeometry = findViewById<Button>(R.id.btnGeometry)
        val btnUndo = findViewById<Button>(R.id.btnUndo)
        val btnRedo = findViewById<Button>(R.id.btnRedo)
        val btnToolMode = findViewById<Button>(R.id.btnToolMode)

        drawingView.attachEraseButton(btnToolMode)
        drawingView.attachColorButton(btnColor)
        drawingView.attachGeomertryButton(btnGeometry)

        btnClear.setOnClickListener {
            drawingView.clearCanvas()
        }

        btnColor.setOnClickListener {
            drawingView.changeColor()
        }

        btnGeometry.setOnClickListener {
            drawingView.geometryMode()
        }
        btnUndo.setOnClickListener {
            drawingView.undo()
        }
        btnRedo.setOnClickListener {
            drawingView.redo()
        }
        btnToolMode.setOnClickListener{
            drawingView.switchToolMode()
        }
    }

    override fun onPause() {
        super.onPause()

        // If we have a QrNote, save current drawing as PNG into its image folder and upload
        val safeNote = qrNote
        val drawingView = findViewById<DrawingView>(R.id.drawingView)
        if (safeNote != null) {
            try {
                val outFile = File(safeNote.ImageSubfolder(), "drawing_${System.currentTimeMillis()}.png")
                val saved = drawingView.saveAsPngToFile(outFile)
                if (saved) {
                    // store in cache and upload to cloud (uses CachedFileHandler which will upload)
                    // We don't have access to the ImageAdapter here, so pass null. The caller that launched
                    // this activity can add the new cached file to its adapter using the activity result.
                    cachedFileHandler.storeSelectedImageInCloudAndCache(Uri.fromFile(outFile), safeNote, null)

                    // Inform caller that a new image was created so it can update its adapter
                    val resultIntent = Intent().apply {
                        putExtra("newImageFileName", outFile.name)
                    }
                    setResult(Activity.RESULT_OK, resultIntent)

                    Toast.makeText(this, "Drawing saved to images.", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to save drawing.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
