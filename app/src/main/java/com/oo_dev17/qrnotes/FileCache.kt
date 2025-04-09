package com.oo_dev17.qrnotes

import android.content.Context
import android.net.Uri
import com.google.android.material.snackbar.Snackbar
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Paths

class FileCache(val context: Context) {

    fun storeFileInCache(
        category: String,
        fileName: String,
        fileContent: ByteArray
    ) {
        // Get the cache directory and create a subfolder for the category
        val categoryDir = File(context.cacheDir, category)
        if (!categoryDir.exists()) {
            categoryDir.mkdirs() // Create the category directory if it doesn't exist
        }

        // Create the file within the category subfolder
        val cacheFile = File(categoryDir, fileName)
        try {
            FileOutputStream(cacheFile).use { fos ->
                fos.write(fileContent)
            }
        } catch (e: IOException) {
            // Handle the exception (e.g., log, show an error message)
            e.printStackTrace()
        }
    }

    fun storeFileInCache(category: String, fileName: String, fileUri: Uri) {
        val categoryDir = File(context.cacheDir, category)
        if (!categoryDir.exists()) {
            categoryDir.mkdirs()
        }

        val cacheFile = File(categoryDir, fileName)
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
        noteId: String,
        category: CachedFileHandler.Category,
        fileName: String
    ): File? {
        // Get the cache directory and the subfolder for the category
        val cacheFile = File(combinePaths(noteId, category.name), fileName)

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

    fun getPathForFileFromCache(category: String, fileName: String): Pair<File, Boolean> {
        val categoryDir = File(context.cacheDir, category)
        if (!categoryDir.exists()) {
            categoryDir.mkdirs()
        }
        val cacheFile = File(categoryDir, fileName)
        val exists = cacheFile.exists()
        if (!exists) {
            cacheFile.createNewFile()
        }
        return Pair(cacheFile, exists)
    }

    fun clearCache() {
        val cacheDir = context.cacheDir
        cacheDir.deleteRecursively()
    }

    fun FileExists(category: CachedFileHandler.Category, fileName: String): Boolean {
        // Get the cache directory and the subfolder for the category
        val categoryDir = File(context.cacheDir, category.name)
        val cacheFile = File(categoryDir, fileName)
        return cacheFile.exists()
    }

    fun deleteFileFromCache(documentId: String, fileName: String) {
        val categoryDir = File(context.cacheDir, documentId)
        val cacheFile = File(categoryDir, fileName)
        try {
            cacheFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun combinePaths(vararg paths: String): String {
        return paths.filter { it.isNotBlank() }.joinToString("/") { it.trim('/') }
    }

    fun createFileInCache(
        documentId: String,
        category: CachedFileHandler.Category,
        filename: String
    ): File {
        val folder = File(context.cacheDir,combinePaths( documentId, category.name))
        if (!folder.exists())
            folder.mkdir()
        return File(folder, filename)
    }
}
