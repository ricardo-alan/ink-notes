package app.inknotes.model

import app.inknotes.model.LineStyle.Body
import app.inknotes.model.LineStyle.Checklist
import app.inknotes.model.LineStyle.Title
import org.junit.Assert.assertEquals
import org.junit.Test

class ChecklistOrderTest {

    private fun list(vararg items: Pair<String, Boolean>, title: String = "Super") = NoteContent(
        text = (listOf(title) + items.map { it.first } + "fin").joinToString("\n"),
        lines = listOf(LineFormat(Title)) + items.map { LineFormat(Checklist, it.second) } + LineFormat(Body),
    )

    private fun items(content: NoteContent) =
        content.text.lines().zip(content.lines).filter { it.second.style == Checklist }.map { it.first to it.second.checked }

    @Test
    fun checkingMovesItemToEndOfItsList() {
        val content = list("leche" to false, "pan" to false, "huevos" to false)
        val result = NoteEditing.toggleChecked(content, 1, moveChecked = true)
        assertEquals(listOf("pan" to false, "huevos" to false, "leche" to true), items(result))
        assertEquals("Super", result.text.lines().first())
        assertEquals("fin", result.text.lines().last())
    }

    @Test
    fun uncheckingMovesItemAboveCheckedItems() {
        val content = list("pan" to false, "huevos" to true, "leche" to true)
        val result = NoteEditing.toggleChecked(content, 3, moveChecked = true)
        assertEquals(listOf("pan" to false, "leche" to false, "huevos" to true), items(result))
    }

    @Test
    fun withoutSortingItemStaysInPlace() {
        val content = list("leche" to false, "pan" to false)
        val result = NoteEditing.toggleChecked(content, 1, moveChecked = false)
        assertEquals(listOf("leche" to true, "pan" to false), items(result))
    }

    @Test
    fun moveLineCarriesInlineStyles() {
        val content = NoteContent(
            text = "a\nbold\nc",
            lines = listOf(LineFormat(Checklist), LineFormat(Checklist), LineFormat(Checklist)),
            spans = listOf(InlineSpan(InlineStyle.Bold, 2, 6)),
        )
        val result = NoteEditing.moveLine(content, 1, 2)
        assertEquals("a\nc\nbold", result.text)
        assertEquals(listOf(InlineSpan(InlineStyle.Bold, 4, 8)), result.spans)
    }

    @Test
    fun moveLastLineUp() {
        val content = NoteContent(text = "a\nb\nc", lines = List(3) { LineFormat(Checklist) })
        assertEquals("c\na\nb", NoteEditing.moveLine(content, 2, 0).text)
    }

    @Test
    fun listRunStopsAtOtherStyles() {
        val content = list("x" to false, "y" to false)
        assertEquals(1..2, NoteEditing.listRun(content, 2))
    }
}
