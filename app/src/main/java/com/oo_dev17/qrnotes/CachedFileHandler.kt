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
        } catch (e: Exception) {
            emptyList() // Or throw to propagate error
        }
    }

    suspend fun getFileFromCacheOrCloud(
        documentId: String,
        category: Category,
        filename: String
    ): Pair<File?, Boolean> {
        assert(documentId.isNotEmpty())

        if (FileExists(documentId, Category.Images, filename)) {
            return Pair(getFileFromCache(documentId, category, filename), true)
        } else {
            return try {
                val file = MakeFile(documentId, category, filename)
                if (file.exists())
                    return Pair(file, true)

                // Ensure the parent directory exists before attempting to download.
                file.parentFile?.mkdirs()

                storageRef.child(documentId).child(category.name).child(filename)
                    .getFile(file)
                    .await()
                storeFileInCache(documentId, category, filename, file)
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

    fun putToCache(qrNote: QrNote, file: File, category: Category) {
        storeFileInCache(qrNote.documentId!!, category, file.name, file)
    }

    fun storeFileInCache(
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

    fun getFileFromCache(
        documentId: String,
        category: Category,
        fileName: String
    ): File? {
        // Get the cache directory and the subfolder for the category
        val cacheFile = MakeFile(documentId, category, fileName)

        // Check if the file exists within the category subfolder
        if (cacheFile.exists()) {
            try {
                return cacheFile
            } catch (e: IOException) {
                // Handle the exception
                e.printStackTrace()
            }
        }
        return null // File doesn't exist or an error occurred
    }

    fun getPathForFileFromCache(
        documentId: String,
        category: Category,
        fileName: String
    ): Pair<File, Boolean> {
        val file = MakeFile(documentId, category, fileName)
        return Pair(file, file.exists())
    }

    fun clearCache() {
        val cacheDir = context.cacheDir
        cacheDir.deleteRecursively()
    }

    fun FileExists(
        documentId: String,
        category: Category,
        fileName: String
    ): Boolean {
        // Get the cache directory and the subfolder for the category
        val file = MakeFile(documentId, category, fileName)
        return file.exists()
    }

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
