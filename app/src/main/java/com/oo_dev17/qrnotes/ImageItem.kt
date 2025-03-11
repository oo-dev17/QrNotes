package com.oo_dev17.qrnotes

import java.io.File

sealed class ImageItem {
    data class FileImage(val file: File) : ImageItem()
    data class ResourceImage(val resId: Int) : ImageItem()
    data class FileString(val resId: String) : ImageItem()
}