package com.oo_dev17.qrnotes

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeNotesRepository : NotesRepository {
    val addedNotes = mutableListOf<QrNote>()
    var nextId: String = "fakeId"

    override suspend fun addNote(note: QrNote): String {
        addedNotes += note
        return nextId
    }
}

class AddNoteUseCaseTest {

    @Test
    fun `adds note and assigns documentId`() = runTest {
        val repo = FakeNotesRepository().apply { nextId = "abc123" }
        val useCase = AddNoteUseCase(repo)

        val note = useCase(title = "My Title", qrCode = "QR_1")

        assertEquals("abc123", note.documentId)
        assertEquals("My Title", note.title)
        assertEquals("QR_1", note.qrCode)

        assertEquals(1, repo.addedNotes.size)
        assertEquals("My Title", repo.addedNotes.first().title)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank title throws`() = runTest {
        val repo = FakeNotesRepository()
        val useCase = AddNoteUseCase(repo)

        useCase(title = "   ")
    }
}
