package com.oo_dev17.qrnotes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.google.android.material.snackbar.Snackbar
import java.io.File

class OpenFile(private val secondFragment: SecondFragment) {

    fun openFileWithAssociatedApp(file: File, context: Context) {
        if (!file.exists()) {
            Log.e("OpenFile", "File does not exist: ${file.absolutePath}")
            return
        }
        try {
            val fileUri: Uri =
                FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", file
                )

            val mimeType = getMimeType(file)

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(fileUri, mimeType)
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK

            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Log.e("OpenFile", "No application found to handle the file type", e)
                Snackbar.make(
                    secondFragment.requireView(),
                    "No application found to handle the file type: " + e.message,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Snackbar.make(
                secondFragment.requireView(),
                "Error opening file: ${e.message}",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun getMimeType(file: File): String {
        return when {
            file.name.endsWith(".pdf") -> "application/pdf"
            file.name.endsWith(".jpg") || file.name.endsWith(".jpeg") -> "image/jpeg"
            file.name.endsWith(".png") -> "image/png"
            file.name.endsWith(".txt") -> "text/plain"
            file.name.endsWith(".doc") || file.name.endsWith(".docx") -> "application/msword"
            else -> "application/octet-stream"
        }
    }
}
