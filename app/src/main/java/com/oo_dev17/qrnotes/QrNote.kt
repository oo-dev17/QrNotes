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
    var documentId: String? = null,

    // Runtime statistics, excluded from Firestore
    @get:Exclude var pictureCount: Int = 0,
    @get:Exclude var documentsCount: Int = 0,
    @get:Exclude var picsLoadedFromCache: Int = 0,
    @get:Exclude var docsLoadedFromCache: Int = 0,
    @get:Exclude var picsLoadedFromFirestore: Int = 0,
    @get:Exclude var docsLoadedFromFirestore: Int = 0
) : Parcelable {
    // Constructor to create a QrNote from a Parcel
    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readLong(),
        parcel.readString()!!,
        parcel.readString()!!
    )

    constructor() : this("", "", System.currentTimeMillis(), "", "")

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
        val images = cachedFileHandler.getFileNamesFromCloud(this, CachedFileHandler.Category.Images)

        // Reset stats before retrieving
        pictureCount = 0
        picsLoadedFromCache = 0
        picsLoadedFromFirestore = 0

        return images.mapNotNull { imageName ->
            try {
                val (file, fromCache) = cachedFileHandler.getFileFromCacheOrCloud(
                    this,
                    CachedFileHandler.Category.Images,
                    imageName
                )

                if (file != null) {
                    pictureCount++
                    if (fromCache) {
                        picsLoadedFromCache++
                    } else {
                        picsLoadedFromFirestore++
                    }
                }
                file
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

    suspend internal fun retrieveDocumentFiles(cachedFileHandler: CachedFileHandler): List<File> {
        val documentFileNames = cachedFileHandler.getFileNamesFromCloud(this, CachedFileHandler.Category.Documents)

        // Reset stats before retrieving
        docsLoadedFromCache = 0
        docsLoadedFromFirestore = 0

        return documentFileNames.mapNotNull { document ->
            try {
                val (file, fromCache) = cachedFileHandler.getFileFromCacheOrCloud(
                    this,
                    CachedFileHandler.Category.Documents,
                    document
                )

                if (file != null) {

                    if (fromCache) {
                        docsLoadedFromCache++
                    } else {
                        docsLoadedFromFirestore++
                    }
                }
                file
            } catch (e: IOException) {
                Log.e(
                    "QrNote",
                    "Failed to retrieve document file '$document' for note '$documentId' at QrNote.retrieveDocumentFiles",
                    e
                )
                null
            }
        }
    }
}