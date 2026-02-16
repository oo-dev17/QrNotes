package com.oo_dev17.qrnotes

/**
 * Abstraction for adding notes.
 *
 * Keep this interface free of Firebase/Android types so it can be unit-tested on the JVM.
 */
interface NotesRepository {
    /** @return the generated documentId */
    suspend fun addNote(note: QrNote): String
}
