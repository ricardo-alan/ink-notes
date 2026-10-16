package app.inknotes.ui.editor

import androidx.compose.ui.graphics.Color
import app.inknotes.model.LineFormat
import app.inknotes.model.LineStyle
import app.inknotes.model.NoteContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NoteDisplayTest {

    private fun paragraphIndentAt(content: NoteContent, line: Int): Boolean {
        val display = buildNoteDisplay(content, Color.Gray)
        val lineStart = (0 until line).fold(0) { pos, _ -> content.text.indexOf('\n', pos) + 1 }
        val at = display.mapping.originalToTransformed(lineStart)
        val paragraph = display.text.paragraphStyles.firstOrNull { at >= it.start && at < it.end }
        assertNotNull("line $line has no paragraph at display offset $at", paragraph)
        return paragraph!!.item.textIndent != null
    }

    @Test
    fun emptyLastListItemKeepsItsIndent() {
        // What Enter at the end of a checklist item leaves behind.
        val content = NoteContent(
            text = "List\nOne\n",
            lines = listOf(LineFormat(LineStyle.Title), LineFormat(LineStyle.Checklist), LineFormat(LineStyle.Checklist)),
        )
        assertEquals(true, paragraphIndentAt(content, 2))
    }

    @Test
    fun emptyMiddleListItemKeepsItsIndent() {
        val content = NoteContent(
            text = "List\n\nTwo",
            lines = listOf(LineFormat(LineStyle.Title), LineFormat(LineStyle.Bullet), LineFormat(LineStyle.Bullet)),
        )
        assertEquals(true, paragraphIndentAt(content, 1))
        assertEquals(true, paragraphIndentAt(content, 2))
    }

    @Test
    fun paragraphsDoNotOverlap() {
        val content = NoteContent(
            text = "T\n\nA\n\n",
            lines = listOf(LineFormat(LineStyle.Title), LineFormat(LineStyle.Body), LineFormat(LineStyle.Checklist), LineFormat(LineStyle.Checklist), LineFormat(LineStyle.Body)),
        )
        val ranges = buildNoteDisplay(content, Color.Gray).text.paragraphStyles.map { it.start until it.end }.sortedBy { it.first }
        ranges.zipWithNext().forEach { (a, b) -> assert(a.last < b.first) { "$a overlaps $b" } }
    }
}
