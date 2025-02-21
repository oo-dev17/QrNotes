package com.oo_dev17.qrnotes

import java.util.UUID

class QrNote( val title: String, val content: String) {
    val uid: String = UUID.randomUUID().toString()
    val creationDate = System.currentTimeMillis()
}
