package com.oo_dev17.qrnotes

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.content.FileProvider
import com.google.android.material.snackbar.Snackbar
import java.io.File

class OpenFile {
    constructor(secondFragment: SecondFragment) {
        this.secondFragment = secondFragment
    }

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
        // Basic MIME type detection (can be improved)
        return when {
            file.name.endsWith(".pdf") -> "application/pdf"
            file.name.endsWith(".jpg") || file.name.endsWith(".jpeg") -> "image/jpeg"
            file.name.endsWith(".png") -> "image/png"
            file.name.endsWith(".txt") -> "text/plain"
            file.name.endsWith(".doc") || file.name.endsWith(".docx") -> "application/msword"
            // Add more cases as needed
            else -> "application/octet-stream" // Default fallback
        }
    }

    private lateinit var secondFragment: SecondFragment

    fun selectDocToAdd(pickerInitialUri: Uri, allTypes: Boolean) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (allTypes) "*/*" else "application/pdf"

            // Optionally, specify a URI for the file that should appear in the
            // system file picker when it loads.
            if (allTypes)
                putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            else
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
        }

        secondFragment.startActivityForResult(intent, SecondFragment.REQUEST_CODE_PICK_DOCUMENT)
    }

    val mimeTypes = arrayOf(
        "application/pdf",
        "image/jpeg",
        "image/png",
        "text/plain",
        "application/msword", // .doc files
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" // .docx files
    )
}
