package app.inknotes.appfunctions

import app.inknotes.appfunctions.NoteMatching.Match
import app.inknotes.data.NoteSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteMatchingTest {

    private fun summary(id: String, title: String) = NoteSummary(id, null, title, "", "", false, 0, null)

    private val notes = listOf(
        summary("1", "Groceries for the party"),
        summary("2", "Groceries"),
        summary("3", "Lista de la compra"),
        summary("4", "Packing list"),
    )

    @Test
    fun exactTitleWinsOverPartialMatches() {
        assertEquals(Match.Found(notes[1]), NoteMatching.byTitle(notes, "groceries"))
    }

    @Test
    fun ignoresAccentsCaseAndExtraSpaces() {
        assertEquals(Match.Found(notes[2]), NoteMatching.byTitle(notes, "  LISTA de la  cómpra "))
    }

    @Test
    fun singlePartialMatchIsFound() {
        assertEquals(Match.Found(notes[3]), NoteMatching.byTitle(notes, "packing"))
    }

    @Test
    fun severalPartialMatchesAreAmbiguous() {
        assertEquals(Match.Ambiguous(listOf(notes[2], notes[3])), NoteMatching.byTitle(notes, "list"))
    }

    @Test
    fun noMatch() {
        assertEquals(Match.None, NoteMatching.byTitle(notes, "todo"))
        assertEquals(Match.None, NoteMatching.byTitle(notes, "  "))
    }
}
