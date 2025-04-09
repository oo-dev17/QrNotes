package com.oo_dev17.qrnotes

import android.content.Context
import androidx.core.net.toUri
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import java.io.File

class CachedFileHandler(private val storageRef: StorageReference, val context: Context) {
    val fileCache = FileCache(context)

    suspend fun getFileNamesFromCloud(qrNote: QrNote, category: Category): List<String> {
        return try {
            val listResult = storageRef
                .child(qrNote.documentId!!)
                .child(category.name)
                .listAll()
                .await() // Suspends coroutine until complete

            listResult.items.map { it.name }
        } catch (e: Exception) {
            emptyList() // Or throw to propagate error
        }
    }

    suspend fun getFileFromCacheOrCloud(
        qrNote: QrNote,
        category: Category,
        filename: String
    ): File? {
        if (fileCache.FileExists(Category.Images, filename))
            return fileCache.getFileFromCache(qrNote.documentId!!, category, filename)
        else {
            try {

            val file = fileCache.createFileInCache(qrNote.documentId!!, category, filename)
            if (file.exists()) return file
            storageRef.child(qrNote.documentId!!).child(category.name).child(filename).getFile(file)
                .await()
            return file
            }
            catch (exception: Exception) {
                println("Error getting file from cloud: "+exception.message)
                return null
            }
        }
    }

    suspend fun fileExists(qrNote: QrNote, file: File, category: Category): Boolean {
        return try {
            val listResult = storageRef
                .child(qrNote.documentId!!)
                .child(category.name)
                .listAll()
                .await() // Wait for the async operation

            file.name in listResult.items.map { it.name }
        } catch (e: Exception) {
            false // Return false if there's any error (or throw to propagate)
        }
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
