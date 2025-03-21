package com.oo_dev17.qrnotes
// ItemClickListener.kt


interface ItemClickListener {

    fun onItemClicked(item: QrNote)
    fun showQrNoteOptions(qrNote: QrNote)
    fun deleteQrNote(qrNote: QrNote)
}