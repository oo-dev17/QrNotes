package com.oo_dev17.qrnotes

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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

    fun getFileFromCache(category: String, fileName: String): ByteArray? {
        // Get the cache directory and the subfolder for the category
        val categoryDir = File(context.cacheDir, category)
        val cacheFile = File(categoryDir, fileName)

        // Check if the file exists within the category subfolder
        if (cacheFile.exists()) {
            try {
                return cacheFile.readBytes()
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

    fun FileExists(category: String, fileName: String): Boolean {
        // Get the cache directory and the subfolder for the category
        val categoryDir = File(context.cacheDir, category)
        val cacheFile = File(categoryDir, fileName)
        return cacheFile.exists()
    }
}
