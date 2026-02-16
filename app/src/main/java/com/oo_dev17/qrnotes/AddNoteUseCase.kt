package com.oo_dev17.qrnotes

/**
 * Pure Kotlin business logic for creating a note and assigning the generated documentId.
 *
 * This is the part you unit-test (no Firebase, no Android).
 */
class AddNoteUseCase(
    private val repo: NotesRepository
) {
    suspend operator fun invoke(
        title: String,
        qrCode: String = ""
    ): QrNote {
        require(title.isNotBlank()) { "Title cannot be empty" }

        val note = QrNote(title = title, content = "", qrCode = qrCode)
        val id = repo.addNote(note)
        note.documentId = id
        return note
    }
}
