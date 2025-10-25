package com.oo_dev17.qrnotes

import android.content.Context
import android.util.Log
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
    ): Pair<File?, Boolean> {
        if (fileCache.FileExists(Category.Images, filename)) {
            return Pair(fileCache.getFileFromCache(qrNote.documentId!!, category, filename), true)
        } else {
            return try {
                val file = fileCache.createFileInCache(qrNote.documentId!!, category, filename)
                if (file.exists()) return Pair(file, true)

                // Ensure the parent directory exists before attempting to download.
                file.parentFile?.mkdirs()

                storageRef.child(qrNote.documentId!!).child(category.name).child(filename)
                    .getFile(file)
                    .await()
                Pair(file, false)
            } catch (exception: Exception) {
                println("Error getting for docID:'${qrNote.documentId}' cat: ${category.name} '$filename' from cloud: " + exception.message)
                Pair(null, false)
            }
        }
    }

    suspend fun fileExists(qrNote: QrNote, file: File, category: Category): Boolean {
        return fileExists(qrNote, file.name, category)
    }

    suspend fun fileExists(qrNote: QrNote, fileName: String, category: Category): Boolean {
        return try {
            val listResult = storageRef
                .child(qrNote.documentId!!)
                .child(category.name)
                .listAll()
                .await() // Wait for the async operation

            fileName in listResult.items.map { it.name }
        } catch (e: Exception) {
            false // Return false if there's any error (or throw to propagate)
        }
    }

    fun uploadToCloud(qrNote: QrNote, file: File, category: Category) {
        storageRef.child(qrNote.documentId!!).child(category.name).child(file.name)
            .putFile(file.toUri()).addOnSuccessListener {
                println("Upload successful: " + file.name)
            }.addOnFailureListener { fail ->
                println("Upload failed: " + fail.message)
            }
    }

    fun deleteFileFromCloud(qrNote: QrNote, name: String, category: Category) {

        storageRef.child(qrNote.documentId!!).child(category.name).child(name).delete()
            .addOnFailureListener { fail -> }
        Log.d(
            "CachedFileHandler",
            "Failed to delete file: deleteFileFromCloud: docId: '${qrNote.documentId}' cat:${category.name} name: '$name'"
        )
    }

    public enum class Category {
        Images, Documents
    }
}
