package com.oo_dev17.qrnotes

import ImageAdapter
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.google.android.material.snackbar.Snackbar
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

        val file = makeFile(documentId, category, filename)
        if (file.exists()) {
            return Pair(file, true)
        } else {
            return try {

                storageRef.child(documentId).child(category.name).child(filename)
                    .getFile(file)
                    .await()
                // now it's already saved in cache!
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
    public fun storeSelectedImageInCloudAndCache(imageUri: Uri, qrNote: QrNote, imageAdapter: ImageAdapter?): Boolean {
        val outputFile: File? = try {
            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                val file = File(qrNote!!.ImageSubfolder(), "${System.currentTimeMillis()}.jpg")
                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                file // Return the newly created file
            }
        } catch (e: IOException) {
            Log.e("StoreImage", "Failed to copy image to cache", e)
            null // Return null if there was an error
        }

        // Check if the file was created successfully
        if (outputFile != null && outputFile.exists()) {
            // Update UI by adding the new image to the adapter
            imageAdapter?.imageItems?.add( ImageItem.FileImage(outputFile))
            imageAdapter?.notifyItemInserted(imageAdapter.imageItems.size - 1)

            // Upload the file to Firebase Cloud Storage
            uploadToCloud(
                qrNote!!,
                outputFile,
                CachedFileHandler.Category.Images
            )
return true
            // Notify the user of success

        } else {
            return false
            // Notify the user of failure

        }
    }

    fun copyFileToCache(
        documentId: String,
        category: Category,
        fileName: String,
        file: File
    ) {
        val cacheFile = makeFile(documentId, category, fileName)
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
        val cacheFile = makeFile(documentId, category, fileName)
        try {
            // see https://stackoverflow.com/questions/10301674/save-file-in-android-with-spaces-in-file-name
            context.contentResolver.openInputStream(fileUri)?.use { inputStream ->
                // 2. Create a FileOutputStream using a FileDescriptor to avoid the space bug.
                val parcelFileDescriptor =
                    context.contentResolver.openFileDescriptor(Uri.fromFile(cacheFile), "w")
                parcelFileDescriptor?.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).use { outputStream ->
                        // 3. Copy the data from the input stream to the output stream.
                        inputStream.copyTo(outputStream)
                    }
                }
            }

            // --- END: THE FIX ---
        } catch (e: IOException) {
            Log.e("CachedFileHandler", "Failed to store file in cache: ${e.message}", e)
            // Optionally, re-throw or handle the error more gracefully.
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

        val cacheFile = makeFile(documentId, category, fileName)
        try {
            cacheFile.delete()
        } catch (e: Exception) {
            Log.e(
                "CachedFileHandler",
                "Failed to delete file: deleteFileFromCache: docId: '${documentId}' cat:${category.name} name: '$fileName': $e"
            )
        }
    }

    fun makeFile(
        documentId: String,
        category: Category,
        fileName: String
    ): File {
        val folder = File(context.cacheDir, documentId)
        if (!folder.exists()) folder.mkdir()
        val folder2 = File(folder, category.name)
        if (!folder2.exists()) folder2.mkdir()
        val file= File(folder2, fileName)
        val name= file.name
        return file
    }

    enum class Category {
        Images, Documents
    }
}
