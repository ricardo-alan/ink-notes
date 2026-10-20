package app.inknotes.model

import app.inknotes.model.LineStyle.Body
import app.inknotes.model.LineStyle.Checklist
import app.inknotes.model.LineStyle.Title
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class NoteChecklistsTest {

    private fun note(vararg lines: Pair<String, LineFormat>, tables: List<NoteTable> = emptyList(), spans: List<InlineSpan> = emptyList()) =
        NoteContent(text = lines.joinToString("\n") { it.first }, lines = lines.map { it.second }, tables = tables, spans = spans)

    private val title = LineFormat(Title)
    private val body = LineFormat(Body)
    private val open = LineFormat(Checklist)
    private val done = LineFormat(Checklist, checked = true)

    private fun rows(content: NoteContent) = content.text.split('\n').zip(content.lines)

    @Test
    fun checklistHasTitleAndUncheckedItems() {
        val content = NoteChecklists.checklist("Groceries", listOf("Milk", " ", "Bread\nrolls"))
        assertEquals("Groceries\nMilk\nBread rolls", content.text)
        assertEquals(listOf(title, open, open), content.lines)
    }

    @Test
    fun noteKeepsBodyLines() {
        val content = NoteChecklists.note("Idea", "first\nsecond\n")
        assertEquals("Idea\nfirst\nsecond", content.text)
        assertEquals(listOf(title, body, body), content.lines)
        assertEquals("Idea", content.title)
    }

    @Test
    fun titleOnlyNote() {
        val content = NoteChecklists.note("Idea", "")
        assertEquals("Idea", content.text)
        assertEquals(listOf(title), content.lines)
    }

    @Test
    fun appendsAtEndOfLastChecklist() {
        val content = note("Super" to title, "milk" to open, "bread" to open, "fin" to body)
        val result = NoteChecklists.append(content, listOf("eggs", "jam"), keepCheckedLast = true)
        assertEquals(
            listOf("Super" to title, "milk" to open, "bread" to open, "eggs" to open, "jam" to open, "fin" to body),
            rows(result),
        )
    }

    @Test
    fun appendsAboveCheckedItemsWhenSorting() {
        val content = note("Super" to title, "milk" to open, "bread" to done)
        val result = NoteChecklists.append(content, listOf("eggs"), keepCheckedLast = true)
        assertEquals(listOf("Super" to title, "milk" to open, "eggs" to open, "bread" to done), rows(result))
    }

    @Test
    fun appendsAfterCheckedItemsWithoutSorting() {
        val content = note("Super" to title, "milk" to open, "bread" to done)
        val result = NoteChecklists.append(content, listOf("eggs"), keepCheckedLast = false)
        assertEquals(listOf("Super" to title, "milk" to open, "bread" to done, "eggs" to open), rows(result))
    }

    @Test
    fun allCheckedListGetsItemsAtItsStartWhenSorting() {
        val content = note("Super" to title, "milk" to done, "bread" to done)
        val result = NoteChecklists.append(content, listOf("eggs"), keepCheckedLast = true)
        assertEquals(listOf("Super" to title, "eggs" to open, "milk" to done, "bread" to done), rows(result))
    }

    @Test
    fun usesLastOfSeveralChecklists() {
        val content = note("T" to title, "a" to open, "middle" to body, "b" to open, "end" to body)
        val result = NoteChecklists.append(content, listOf("c"), keepCheckedLast = true)
        assertEquals(listOf("T" to title, "a" to open, "middle" to body, "b" to open, "c" to open, "end" to body), rows(result))
    }

    @Test
    fun noteWithoutChecklistGetsOneAtTheEnd() {
        val content = note("Ideas" to title, "some text" to body)
        val result = NoteChecklists.append(content, listOf("one", "two"), keepCheckedLast = true)
        assertEquals(listOf("Ideas" to title, "some text" to body, "one" to open, "two" to open), rows(result))
    }

    @Test
    fun emptyLastLineIsReused() {
        val content = note("Ideas" to title, "" to body)
        val result = NoteChecklists.append(content, listOf("one"), keepCheckedLast = true)
        assertEquals(listOf("Ideas" to title, "one" to open), rows(result))
    }

    @Test
    fun titleOnlyNoteKeepsItsTitle() {
        val content = note("Ideas" to title)
        val result = NoteChecklists.append(content, listOf("one"), keepCheckedLast = true)
        assertEquals(listOf("Ideas" to title, "one" to open), rows(result))
    }

    @Test
    fun blankItemsChangeNothing() {
        val content = note("Super" to title, "milk" to open)
        assertSame(content, NoteChecklists.append(content, listOf("", "  "), keepCheckedLast = true))
    }

    @Test
    fun laterSpansAndTablesShift() {
        // "Super\nmilk\nfin": "fin" is bold (11..14) and a table sits before it (line 2).
        val content = note(
            "Super" to title, "milk" to open, "fin" to body,
            tables = listOf(NoteTable("t", beforeLine = 2, rows = listOf(listOf("x")))),
            spans = listOf(InlineSpan(InlineStyle.Bold, 6, 10), InlineSpan(InlineStyle.Italic, 11, 14)),
        )
        val result = NoteChecklists.append(content, listOf("eggs"), keepCheckedLast = true)
        assertEquals("Super\nmilk\neggs\nfin", result.text)
        assertEquals(3, result.tables.single().beforeLine)
        assertEquals(listOf(InlineSpan(InlineStyle.Bold, 6, 10), InlineSpan(InlineStyle.Italic, 16, 19)), result.spans)
        assertEquals(result, NoteTables.normalize(result))
    }

    @Test
    fun itemsListsChecklistLines() {
        val content = note("Super" to title, " milk " to open, "note" to body, "bread" to done)
        assertEquals(
            listOf(NoteChecklists.Item("milk", false), NoteChecklists.Item("bread", true)),
            NoteChecklists.items(content),
        )
    }
}
