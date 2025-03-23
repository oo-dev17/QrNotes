package com.oo_dev17.qrnotes

import android.os.Environment
import android.os.Parcel
import android.os.Parcelable
import java.io.File

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
    constructor() : this("","", System.currentTimeMillis(), "")


    lateinit var allDocuments: List<String>
    val fileNames: List<String> =
        getImageFiles().first.map { it.name }
        

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

    internal fun getImageFiles(): Pair<List<File>, String> {
        val subfolderPath = ImageSubfolder()?.absolutePath
        val subfolderDir = ImageSubfolder()


        if (subfolderDir == null || !subfolderDir.exists()) {
            return Pair(emptyList<File>(), "subfolderDir ${subfolderPath} does not exist!")
        }
        val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
        // Filter files in the subfolder by image extensions
        val subfolder = File(subfolderPath)
        val files = subfolder.listFiles()
        return Pair(
            files
                ?.filter { file ->
                    file.isFile && imageExtensions.any { ext ->
                        file.name.endsWith(".$ext", ignoreCase = true)
                    }
                }
                ?: emptyList(),
            "") // Return an empty list if the subfolder is empty or inaccessible
    }
}