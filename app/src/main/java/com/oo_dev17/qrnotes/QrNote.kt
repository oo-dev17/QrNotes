package com.oo_dev17.qrnotes

import android.os.Environment
import android.os.Parcel
import android.os.Parcelable
import android.util.Log
import com.google.firebase.firestore.Exclude
import java.io.File
import java.io.IOException

data class QrNote(
    val title: String? = null,
    val content: String? = null,
    val creationDate: Long = System.currentTimeMillis(),
    val qrCode: String = "",
    var documentId: String? = null

) : Parcelable {
    // Constructor to create a QrNote from a Parcel
    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readLong(),
        parcel.readString()!!,
        parcel.readString()!!
    )

    constructor() : this("", "", System.currentTimeMillis(), "")

    @Exclude
    @Transient
    lateinit var allDocuments: List<String>

    init {
        allDocuments = listOf("", "")
    }

    // Describe the kinds of special objects contained in this Parcelable instance's marshaled representation.
    override fun describeContents(): Int {
        return 0
    }

    // Flatten this object in to a Parcel.
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(title)
        parcel.writeString(content)
        parcel.writeLong(creationDate)
        parcel.writeString(documentId)
    }

    // Companion object with CREATOR to create instances of QrNote from a Parcel
    companion object CREATOR : Parcelable.Creator<QrNote> {
        override fun createFromParcel(parcel: Parcel): QrNote {
            return QrNote(parcel)
        }

        override fun newArray(size: Int): Array<QrNote?> {
            return arrayOfNulls(size)
        }
    }

    fun ImageSubfolder(): File {
        val storageDir: File? =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val subfolder = "QrNotes/" + documentId
        val subfolderDir = File(storageDir, subfolder)
        if (!subfolderDir.exists())
            subfolderDir.mkdirs()
        return subfolderDir
    }

    suspend internal fun retrieveImageFiles(cachedFileHandler: CachedFileHandler): List<File> {

        val images =
            cachedFileHandler.getFileNamesFromCloud(this, CachedFileHandler.Category.Images)

        return images.mapNotNull { imageName ->
            try {
                cachedFileHandler.getFileFromCacheOrCloud(
                    this,
                    CachedFileHandler.Category.Images,
                    imageName
                )
            } catch (e: IOException) {
                Log.e(
                    "QrNote",
                    "Failed to retrieve image file '$imageName' for note '$documentId' at QrNote.retrieveImageFiles",
                    e
                )
                null
            }
        }
    }

    internal fun retrieveImageFilesOld(): List<File> {
        val subfolderDir = ImageSubfolder()

        if (!subfolderDir.exists()) {
            return emptyList()
        }
        val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
        // Filter files in the subfolder by image extensions
        return try {
            val files = subfolderDir.listFiles()
            files?.filter { file ->
                file.isFile && imageExtensions.any { ext ->
                    file.name.endsWith(".$ext", ignoreCase = true)
                }
            } ?: emptyList()
        } catch (e: Exception) {
             Log.e("QrNote", "Error accessing files in ${subfolderDir.absolutePath} for note '$documentId' in retrieveImageFilesOld.", e)
            emptyList()
        }
    }
}