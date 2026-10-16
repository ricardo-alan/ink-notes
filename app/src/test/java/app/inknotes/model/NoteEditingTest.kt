package app.inknotes.model

import app.inknotes.model.LineStyle.Body
import app.inknotes.model.LineStyle.Checklist
import app.inknotes.model.LineStyle.Title
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteEditingTest {

    private fun note(text: String, vararg lines: LineFormat, spans: List<InlineSpan> = emptyList()) =
        NoteContent(text = text, lines = lines.toList(), spans = spans)

    private fun type(content: NoteContent, at: Int, inserted: String): NoteContent {
        val text = content.text.substring(0, at) + inserted + content.text.substring(at)
        return NoteEditing.applyTextChange(content, text, at + inserted.length, at + inserted.length).content
    }

    @Test
    fun enterAfterTitleStartsBody() {
        val result = type(note("Groceries", LineFormat(Title)), 9, "\n")
        assertEquals(listOf(LineFormat(Title), LineFormat(Body)), result.lines)
    }

    @Test
    fun enterInChecklistContinuesUnchecked() {
        val result = type(note("milk", LineFormat(Checklist, checked = true)), 4, "\n")
        assertEquals(listOf(LineFormat(Checklist, true), LineFormat(Checklist, false)), result.lines)
    }

    @Test
    fun enterOnEmptyListItemEndsList() {
        val content = note("milk\n", LineFormat(Checklist), LineFormat(Checklist))
        val result = NoteEditing.applyTextChange(content, "milk\n\n", 6, 6)
        assertEquals("milk\n", result.content.text)
        assertEquals(listOf(LineFormat(Checklist), LineFormat(Body)), result.content.lines)
        assertEquals(5, result.selectionStart)
    }

    @Test
    fun enterAtStartOfLineKeepsFormatWithContent() {
        val content = note("A\nmilk", LineFormat(Title), LineFormat(Checklist, checked = true))
        val result = type(content, 2, "\n")
        assertEquals("A\n\nmilk", result.text)
        assertEquals(LineFormat(Checklist, true), result.lines[2])
        assertEquals(LineFormat(Checklist, false), result.lines[1])
    }

    @Test
    fun backspaceJoiningLinesKeepsUpperFormat() {
        val content = note("A\nB", LineFormat(Title), LineFormat(Checklist))
        val result = NoteEditing.applyTextChange(content, "AB", 1, 1).content
        assertEquals(listOf(LineFormat(Title)), result.lines)
    }

    @Test
    fun pastingMultipleLinesKeepsLineCountInSync() {
        val content = note("Title\nbody", LineFormat(Title), LineFormat(Body))
        val result = type(content, 10, "\none\ntwo")
        assertEquals(result.text.count { it == '\n' } + 1, result.lines.size)
    }

    @Test
    fun typingAfterBoldContinuesBoldButNotBefore() {
        val content = note("hello", LineFormat(Body), spans = listOf(InlineSpan(InlineStyle.Bold, 0, 5)))
        assertEquals(listOf(InlineSpan(InlineStyle.Bold, 0, 6)), type(content, 5, "!").spans)
        assertEquals(listOf(InlineSpan(InlineStyle.Bold, 1, 6)), type(content, 0, ">").spans)
    }

    @Test
    fun deletingInsideSpanShrinksIt() {
        val content = note("hello world", LineFormat(Body), spans = listOf(InlineSpan(InlineStyle.Italic, 0, 11)))
        val result = NoteEditing.applyTextChange(content, "hello", 5, 5).content
        assertEquals(listOf(InlineSpan(InlineStyle.Italic, 0, 5)), result.spans)
    }

    @Test
    fun toggleInlineAddsAndRemoves() {
        val content = note("hello world", LineFormat(Body))
        val bold = NoteEditing.toggleInline(content, 0, 5, InlineStyle.Bold)
        assertTrue(NoteEditing.isInlineActive(bold, 0, 5, InlineStyle.Bold))
        val partial = NoteEditing.toggleInline(bold, 1, 3, InlineStyle.Bold)
        assertEquals(listOf(InlineSpan(InlineStyle.Bold, 0, 1), InlineSpan(InlineStyle.Bold, 3, 5)), partial.spans)
        assertFalse(NoteEditing.isInlineActive(partial, 0, 5, InlineStyle.Bold))
    }

    @Test
    fun toggleInlineWithoutSelectionUsesWord() {
        val content = note("hello world", LineFormat(Body))
        assertEquals(listOf(InlineSpan(InlineStyle.Bold, 6, 11)), NoteEditing.toggleInline(content, 8, 8, InlineStyle.Bold).spans)
    }

    @Test
    fun setLineStyleTogglesBackToBody() {
        val content = note("a\nb", LineFormat(Body), LineFormat(Body))
        val list = NoteEditing.setLineStyle(content, 0, 3, Checklist)
        assertEquals(listOf(LineFormat(Checklist), LineFormat(Checklist)), list.lines)
        assertEquals(listOf(LineFormat(Body), LineFormat(Body)), NoteEditing.setLineStyle(list, 0, 3, Checklist).lines)
    }

    @Test
    fun titleAndPreviewSkipBlankLines() {
        val content = note("\n  Groceries \n\nmilk", LineFormat(), LineFormat(), LineFormat(), LineFormat())
        assertEquals("Groceries", content.title)
        assertEquals("milk", content.preview)
    }
}
