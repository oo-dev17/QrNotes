package com.oo_dev17.qrnotes

import android.os.Environment
import android.os.Parcel
import android.os.Parcelable
import java.io.File
import java.util.UUID


data class QrNote(
    val title: String? = null,
    val content: String? = null,
    val uid: String = UUID.randomUUID().toString(),
    val creationDate: Long = System.currentTimeMillis(),
    val documentId: String? = null
) : Parcelable {


    // Constructor to create a QrNote from a Parcel
    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readLong()
    )

    // Describe the kinds of special objects contained in this Parcelable instance's marshaled representation.
    override fun describeContents(): Int {
        return 0
    }

    // Flatten this object in to a Parcel.
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(title)
        parcel.writeString(content)
        parcel.writeString(uid)
        parcel.writeLong(creationDate)
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

    fun getShortHash(): String {
        return uid.substring(0, uid.indexOf('-'))
    }

    fun ImageSubfolder(): File {
        val storageDir: File? =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val subfolder = "QrNotes/" + getShortHash()
        val subfolderDir = File(storageDir, subfolder)
        if (!subfolderDir.exists())
            subfolderDir.mkdirs()
        return subfolderDir

    }
}