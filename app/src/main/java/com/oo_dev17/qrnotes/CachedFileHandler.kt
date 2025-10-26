package com.oo_dev17.qrnotes

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class CachedFileHandler(private val storageRef: StorageReference, val context: Context) {

    suspend fun getFileNamesFromCloud(qrNote: QrNote, category: Category): List<String> {
        return try {
            val listResult = storageRef
                .child(qrNote.documentId!!)
                .child(category.name)
                .listAll()
                .await() // Suspends coroutine until complete

            listResult.items.map { it.name }
        } catch (_: Exception) {
            emptyList() // Or throw to propagate error
        }
    }

    suspend fun getFileFromCacheOrCloud(
        documentId: String,
        category: Category,
        filename: String
    ): Pair<File?, Boolean> {
        assert(documentId.isNotEmpty())

                val file = MakeFile(documentId, category, filename)
        if (file.exists()) {
            return Pair(file, true)
        } else {
            return try {
                if (file.exists())
                    return Pair(file, true)

                // Ensure the parent directory exists before attempting to download.
                file.parentFile?.mkdirs()

                storageRef.child(documentId).child(category.name).child(filename)
                    .getFile(file)
                    .await()
                copyFileToCache(documentId, category, filename, file)
                Pair(file, false)
            } catch (exception: Exception) {
                println("Error getting for docID:'${documentId}' cat: ${category.name} '$filename' from cloud: " + exception.message)
                Pair(null, false)
            }
        }
    }

    fun uploadToCloud(qrNote: QrNote, file: File, category: Category) {
        storageRef.child(qrNote.documentId!!).child(category.name).child(file.name)
            .putFile(file.toUri()).addOnSuccessListener { s ->
                println("Upload successful: " + file.name)
                Log.i(
                    "CachedFileHandler",
                    """uploadToCloud success (${file.length() / 1000.0} kb):${file.name} transferred: ${s.bytesTransferred}"""
                )
            }.addOnFailureListener { fail ->
                println("Upload failed: " + fail.message)
                Log.e("CachedFileHandler", "uploadToCloud failed: " + fail.message)
            }
    }

    fun copyFileToCache(
        documentId: String,
        category: Category,
        fileName: String,
        file: File
    ) {
        val cacheFile = MakeFile(documentId, category, fileName)
        try {
            file.copyTo(cacheFile)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun storeFileInCache(
        documentId: String,
        category: Category,
        fileName: String,
        fileUri: Uri
    ): File {
        val cacheFile = MakeFile(documentId, category, fileName)
        try {
            context.contentResolver.openInputStream(fileUri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return cacheFile
    }




/*
    fun clearCache() {
        val cacheDir = context.cacheDir
        cacheDir.deleteRecursively()
    }
*/


    fun deleteFileFromBoth(
        documentId: String,
        category: Category,
        fileName: String
    ) {
        deleteFileFromCache(documentId, category, fileName)
        deleteFileFromCloud(documentId, category, fileName)
    }

    fun deleteFileFromCloud(documentId: String, category: Category, name: String) {

        storageRef.child(documentId).child(category.name).child(name).delete()
            .addOnFailureListener { fail ->

        Log.e(
            "CachedFileHandler",
            "Failed to delete file: deleteFileFromCloud: docId: '${documentId}' cat:${category.name} name: '$name': ${fail.message}"
        )
            }
    }

    fun deleteFileFromCache(
        documentId: String,
        category: Category,
        fileName: String
    ) {

        val cacheFile = MakeFile(documentId, category, fileName)
        try {
            cacheFile.delete()
        } catch (e: Exception) {
            Log.e(
                "CachedFileHandler",
                "Failed to delete file: deleteFileFromCache: docId: '${documentId}' cat:${category.name} name: '$fileName': $e"
            )
        }
    }

    fun MakeFile(
        documentId: String,
        category: Category,
        fileName: String
    ): File {
        val folder = File(context.cacheDir, documentId)
        if (!folder.exists()) folder.mkdir()
        val folder2 = File(folder, category.name)
        if (!folder2.exists()) folder2.mkdir()
        return File(folder2, fileName)
    }

    enum class Category {
        Images, Documents
    }
}
