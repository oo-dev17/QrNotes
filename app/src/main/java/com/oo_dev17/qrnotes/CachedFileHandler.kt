package com.oo_dev17.qrnotes

import android.content.Context
import androidx.core.net.toUri
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import java.io.File

class CachedFileHandler(private val storageRef: StorageReference, val context: Context) {
    val fileCache = FileCache(context)

    fun getFileNamesFromCloud(qrNote: QrNote, category: Category): List<String> {
        val all = storageRef.child(qrNote!!.documentId!!).child(category.name).listAll()
        var allDocuments: List<String> = listOf()
        all.addOnSuccessListener { listResult ->
            run { allDocuments = listResult.items.map { it.name } }
        }
        return allDocuments
    }

    suspend fun getFileFromCacheOrCloud(
        qrNote: QrNote,
        category: Category,
        filename: String
    ): File? {
        if (fileCache.FileExists(Category.Images, filename))
            return fileCache.getFileFromCache(qrNote.documentId!!, category, filename)
        else {
            val file = fileCache.createFileInCache(qrNote.documentId!!, category, filename)
            storageRef.child(qrNote.documentId!!).child(category.name).child(filename).getFile(file)
                .await()
            return file
        }
    }

    fun fileExists(qrNote: QrNote, file: File, category: Category): Boolean {
        val all = storageRef.child(qrNote!!.documentId!!).child(category.name).listAll()
        var allDocuments: List<String> = listOf()
        all.addOnSuccessListener { listResult ->
            run { allDocuments = listResult.items.map { it.name } }
        }
        return file.name in allDocuments
    }

    fun uploadToCloud(qrNote: QrNote, file: File, category: Category) {
        storageRef.child(qrNote.documentId!!).child(category.name).child(file.name)
            .putFile(file.toUri()).addOnSuccessListener {
                println("Upload successful: "+file.name)
            }.addOnFailureListener {fail->
                println("Upload failed: "+fail.message)
            }
    }

    public enum class Category {
        Images, Documents
    }
}
