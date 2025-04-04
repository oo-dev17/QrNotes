package com.oo_dev17.qrnotes

import android.content.Context
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

    public enum class Category {
        Images, Documents
    }
}
