package com.oo_dev17.qrnotes

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class FileCache(val context: Context) {

    fun storeFileInCache(
        documentId: String,
        category: CachedFileHandler.Category,
        fileName: String,
        fileContent: ByteArray
    ) {

        // Create the file within the category subfolder
        val cacheFile = MakeFile(documentId, category, fileName)
        try {
            FileOutputStream(cacheFile).use { fos ->
                fos.write(fileContent)
            }
        } catch (e: IOException) {
            // Handle the exception (e.g., log, show an error message)
            e.printStackTrace()
        }
    }

    fun storeFileInCache(
        documentId: String,
        category: CachedFileHandler.Category,
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
        category: CachedFileHandler.Category,
        fileName: String,
        fileUri: Uri
    ) {
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
    }

    fun getFileFromCache(
        documentId: String,
        category: CachedFileHandler.Category,
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
        category: CachedFileHandler.Category,
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
        category: CachedFileHandler.Category,
        fileName: String
    ): Boolean {
        // Get the cache directory and the subfolder for the category
        val file = MakeFile(documentId, category, fileName)
        return file.exists()
    }

    fun deleteFileFromCache(
        documentId: String,
        category: CachedFileHandler.Category,
        fileName: String
    ) {

        val cacheFile = MakeFile(documentId, category, fileName)
        try {
            cacheFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun MakeFile(
        documentId: String,
        category: CachedFileHandler.Category,
        fileName: String
    ): File {
        val folder = File(context.cacheDir, documentId)
        if (!folder.exists()) folder.mkdir()
        val folder2 = File(folder, category.name)
        if (!folder2.exists()) folder2.mkdir()
        return File(folder2, fileName)
    }
}
