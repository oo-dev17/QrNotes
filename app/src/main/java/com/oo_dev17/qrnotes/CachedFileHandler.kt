package com.oo_dev17.qrnotes

import ImageAdapter
import android.R
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.google.android.gms.tasks.Tasks
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class CachedFileHandler(private val storageRef: StorageReference, val context: Context) {

    suspend fun getFileNamesFromCloud(noteId: String, category: Category): List<String> {
        if (noteId.isBlank()) return emptyList()

        return try {
            val listResult = storageRef
                .child(noteId)
                .child(category.name)
                .listAll()
                .await() // Suspends coroutine until complete

            listResult.items.map { it.name }
        } catch (e: Exception) {
            Log.e("CachedFileHandler", "getFileNamesFromCloud failed (noteId=$noteId, category=${category.name}): ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getFileFromCacheOrCloud(
        noteId: String,
        category: Category,
        filename: String
    ): Pair<File?, Boolean> {
        require(noteId.isNotBlank()) { "noteId must not be blank" }

        val file = makeFile(noteId, category, filename)
        if (file.exists()) {
            return Pair(file, true)
        }

        return try {
            storageRef.child(noteId).child(category.name).child(filename)
                .getFile(file)
                .await()
            // now it's already saved in cache!
            Pair(file, false)
        } catch (exception: Exception) {
            Log.e(
                "CachedFileHandler",
                "Error downloading (noteId='$noteId' category=${category.name} filename='$filename'): ${exception.message}",
                exception
            )
            Pair(null, false)
        }
    }

    fun uploadToCloud(qrNote: QrNote, file: File, category: Category) {
        try {
            val noteId = qrNote.documentId!!
            val child1 = storageRef.child(noteId)
            val child2 = child1.child(category.name)
            val child = child2.child(file.name)
            child.putFile(file.toUri()).addOnSuccessListener { s ->
                println("Upload successful: " + file.name)
                Log.i(
                    "CachedFileHandler",
                    """uploadToCloud success (${file.length() / 1000.0} kb):${file.name} transferred: ${s.bytesTransferred}"""
                )
            }.addOnFailureListener { fail ->
                println("Upload failed: " + fail.message)
                Log.e("CachedFileHandler", "uploadToCloud failed: " + fail.message)
            }
        } catch (e: Exception) {
            Log.e("CachedFileHandler", "uploadToCloud failed: " + e.message)
        }
    }

    fun storeSelectedImageInCloudAndCache(
        imageUri: Uri,
        qrNote: QrNote,
        imageAdapter: ImageAdapter?
    ): Boolean {
        val outputFile: File? = try {
            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                val file = File(qrNote.ImageSubfolder(), "${System.currentTimeMillis()}.jpg")
                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                file
            }
        } catch (e: IOException) {
            Log.e("StoreImage", "Failed to copy image to cache", e)
            null
        }

        if (outputFile != null && outputFile.exists()) {
            imageAdapter?.imageItems?.add(ImageItem.FileImage(outputFile))
            imageAdapter?.notifyItemInserted(imageAdapter.imageItems.size - 1)

            uploadToCloud(qrNote, outputFile, CachedFileHandler.Category.Images)
            return true
        }

        return false
    }

    fun copyFileToCache(
        noteId: String,
        category: Category,
        fileName: String,
        file: File
    ) {
        val cacheFile = makeFile(noteId, category, fileName)
        try {
            file.copyTo(cacheFile)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun storeFileInCache(
        noteId: String,
        category: Category,
        fileName: String,
        fileUri: Uri
    ): File {
        val cacheFile = makeFile(noteId, category, fileName)
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
        } catch (e: IOException) {
            Log.e("CachedFileHandler", "Failed to store file in cache: ${e.message}", e)
        }
        return cacheFile
    }

    fun deleteFileFromBoth(
        noteId: String,
        category: Category,
        fileName: String
    ) {
        deleteFileFromCache(noteId, category, fileName)
        deleteFileFromCloud(noteId, category, fileName)
    }

    fun deleteFileFromCloud(noteId: String, category: Category, name: String) {
        storageRef.child(noteId).child(category.name).child(name).delete()
            .addOnFailureListener { fail ->
                Log.e(
                    "CachedFileHandler",
                    "Failed to delete file from cloud: noteId='$noteId' cat=${category.name} name='$name': ${fail.message}"
                )
            }
    }

    fun deleteFileFromCache(
        noteId: String,
        category: Category,
        fileName: String
    ) {
        val cacheFile = makeFile(noteId, category, fileName)
        try {
            cacheFile.delete()
        } catch (e: Exception) {
            Log.e(
                "CachedFileHandler",
                "Failed to delete file from cache: noteId='$noteId' cat=${category.name} name='$fileName': $e"
            )
        }
    }

    fun makeFile(
        noteId: String,
        category: Category,
        fileName: String
    ): File {
        val folder = File(context.cacheDir, noteId)
        if (!folder.exists()) folder.mkdir()
        val folder2 = File(folder, category.name)
        if (!folder2.exists()) folder2.mkdir()
        return File(folder2, fileName)
    }

    enum class Category {
        Images, Documents
    }
}
