package app.inknotes.model

import app.inknotes.model.LineStyle.Body
import app.inknotes.model.LineStyle.Bullet
import app.inknotes.model.LineStyle.Title
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTablesTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    private fun note(text: String, vararg lines: LineFormat, spans: List<InlineSpan> = emptyList(), tables: List<NoteTable> = emptyList()) =
        NoteContent(text = text, lines = lines.toList(), spans = spans, tables = tables)

    private fun table(id: String, beforeLine: Int, vararg rows: List<String>) = NoteTable(id, beforeLine, rows.toList())

    private fun texts(content: NoteContent) =
        NoteTables.blocks(content).filterIsInstance<NoteBlock.Text>().map { NoteTables.subContent(content, it).text }

    @Test
    fun version1JsonStillDecodes() {
        val old = """{"text":"Groceries\nmilk","lines":[{"style":"Title"},{"style":"Checklist","checked":true}]}"""
        val content = json.decodeFromString<NoteContent>(old)
        assertEquals(1, content.version)
        assertEquals("Groceries\nmilk", content.text)
        assertTrue(content.tables.isEmpty())
        assertEquals(content, NoteTables.normalize(content))
    }

    @Test
    fun tablesRoundTripThroughJson() {
        val content = note("Plan\n", LineFormat(Title), LineFormat(Body), tables = listOf(table("t", 1, listOf("a", "b"), listOf("c", "d"))))
            .copy(version = NoteContent.CURRENT_VERSION)
        val decoded = json.decodeFromString<NoteContent>(json.encodeToString(content))
        assertEquals(content, decoded)
    }

    @Test
    fun noteWithoutTablesEncodesWithoutTablesKey() {
        val encoded = json.encodeToString(note("Hi", LineFormat(Title)))
        assertFalse(encoded.contains("tables"))
    }

    @Test
    fun blocksAlternateTextAndTables() {
        val content = note("Title\nmiddle\nend", LineFormat(Title), LineFormat(Body), LineFormat(Body),
            tables = listOf(table("a", 1, listOf("x")), table("b", 2, listOf("y"))))
        val blocks = NoteTables.blocks(content)
        assertEquals(
            listOf(
                NoteBlock.Text("", 0, 1),
                NoteBlock.Table(content.tables[0]),
                NoteBlock.Text("a", 1, 2),
                NoteBlock.Table(content.tables[1]),
                NoteBlock.Text("b", 2, 3),
            ),
            blocks,
        )
        assertEquals(listOf("Title", "middle", "end"), texts(content))
    }

    @Test
    fun insertBelowLastLineAddsEmptyLineAfterTable() {
        val content = note("Groceries", LineFormat(Title))
        val block = NoteTables.blocks(content).first() as NoteBlock.Text
        val result = NoteTables.insertTable(content, block, caret = 9, id = "t").content
        assertEquals("Groceries\n", result.text)
        assertEquals(listOf(LineFormat(Title), LineFormat(Body)), result.lines)
        assertEquals(1, result.tables.single().beforeLine)
        assertEquals(3, result.tables.single().rowCount)
        assertEquals(3, result.tables.single().columnCount)
        assertEquals(listOf("Groceries", ""), texts(result))
    }

    @Test
    fun insertInMiddleSplitsText() {
        val content = note("Title\none\ntwo", LineFormat(Title), LineFormat(Body), LineFormat(Body))
        val block = NoteTables.blocks(content).first() as NoteBlock.Text
        val result = NoteTables.insertTable(content, block, caret = 7, id = "t").content
        assertEquals("Title\none\ntwo", result.text)
        assertEquals(listOf("Title\none", "two"), texts(result))
    }

    @Test
    fun insertOnEmptyLineReplacesIt() {
        val content = note("Title\n\nafter", LineFormat(Title), LineFormat(Body), LineFormat(Body))
        val block = NoteTables.blocks(content).first() as NoteBlock.Text
        val result = NoteTables.insertTable(content, block, caret = 6, id = "t").content
        assertEquals(1, result.tables.single().beforeLine)
        assertEquals(listOf("Title", "\nafter"), texts(result))
    }

    @Test
    fun insertInLaterBlockMovesFollowingTables() {
        val content = note("A\nB", LineFormat(Title), LineFormat(Body), tables = listOf(table("first", 1, listOf("x"))))
        val block = NoteTables.blocks(content).first() as NoteBlock.Text
        val result = NoteTables.insertTable(content, block, caret = 1, id = "second").content
        assertEquals("A\n\nB", result.text)
        assertEquals(listOf("second" to 1, "first" to 2), result.tables.map { it.id to it.beforeLine })
        assertEquals(listOf("A", "", "B"), texts(result))
    }

    @Test
    fun editingTextBeforeTableShiftsIt() {
        val content = note("Title\nbody\nend", LineFormat(Title), LineFormat(Body), LineFormat(Body),
            spans = listOf(InlineSpan(InlineStyle.Bold, 11, 14)),
            tables = listOf(table("t", 2, listOf("x"))))
        val block = NoteTables.blocks(content).first() as NoteBlock.Text
        val sub = NoteTables.subContent(content, block)
        val edited = NoteEditing.applyTextChange(sub, "Title\nbo\ndy", 9, 9).content
        val result = NoteTables.replaceText(content, block, edited)
        assertEquals("Title\nbo\ndy\nend", result.text)
        assertEquals(3, result.tables.single().beforeLine)
        assertEquals(4, result.lines.size)
        assertEquals(listOf(InlineSpan(InlineStyle.Bold, 12, 15)), result.spans)
        assertEquals("end", texts(result).last())
    }

    @Test
    fun editingTextAfterTableKeepsFormatsAndSpans() {
        val content = note("Title\n- item", LineFormat(Title), LineFormat(Bullet),
            spans = listOf(InlineSpan(InlineStyle.Italic, 0, 5)),
            tables = listOf(table("t", 1, listOf("x"))))
        val block = NoteTables.textBlock(content, "t")!!
        val sub = NoteTables.subContent(content, block)
        assertEquals(listOf(LineFormat(Bullet)), sub.lines)
        assertTrue(sub.spans.isEmpty())
        val edited = NoteEditing.applyTextChange(sub, "- item\n", 7, 7).content
        val result = NoteTables.replaceText(content, block, edited)
        assertEquals("Title\n- item\n", result.text)
        assertEquals(listOf(LineFormat(Title), LineFormat(Bullet), LineFormat(Bullet)), result.lines)
        assertEquals(listOf(InlineSpan(InlineStyle.Italic, 0, 5)), result.spans)
        assertEquals(1, result.tables.single().beforeLine)
    }

    @Test
    fun deletingTableMergesText() {
        val content = note("A\nB", LineFormat(Title), LineFormat(Body), tables = listOf(table("t", 1, listOf("x"))))
        val result = NoteTables.deleteTable(content, "t")
        assertEquals(listOf("A\nB"), texts(result))
    }

    @Test
    fun rowsAndColumnsCanBeAddedAndRemoved() {
        var content = note("A\n", LineFormat(Title), LineFormat(Body), tables = listOf(table("t", 1, listOf("a", "b"), listOf("c", "d"))))
        content = NoteTables.addRow(content, "t", 1)
        assertEquals(listOf(listOf("a", "b"), listOf("", ""), listOf("c", "d")), content.tables.single().rows)
        content = NoteTables.addColumn(content, "t", 2)
        assertEquals(listOf("a", "b", ""), content.tables.single().rows.first())
        content = NoteTables.removeColumn(content, "t", 0)
        assertEquals(listOf(listOf("b", ""), listOf("", ""), listOf("d", "")), content.tables.single().rows)
        content = NoteTables.removeRow(content, "t", 1)
        assertEquals(listOf(listOf("b", ""), listOf("d", "")), content.tables.single().rows)
        content = NoteTables.setCell(content, "t", 1, 1, "e\nf")
        assertEquals("e f", content.tables.single().rows[1][1])
    }

    @Test
    fun removingLastRowRemovesTable() {
        val content = note("A\n", LineFormat(Title), LineFormat(Body), tables = listOf(table("t", 1, listOf("a", "b"))))
        assertTrue(NoteTables.removeRow(content, "t", 0).tables.isEmpty())
    }

    @Test
    fun normalizeSeparatesAdjacentTablesAndAddsTrailingLine() {
        val content = note("A", LineFormat(Title),
            tables = listOf(table("b", 1, listOf("y")), table("a", 1, listOf("x", "z"), listOf("w"))))
        val result = NoteTables.normalize(content)
        assertEquals("A\n\n", result.text)
        assertEquals(3, result.lines.size)
        assertEquals(listOf(1, 2), result.tables.map { it.beforeLine })
        assertEquals(listOf("x", "z"), result.tables[1].rows[0])
        assertEquals(listOf("w", ""), result.tables[1].rows[1])
        assertEquals(listOf("A", "", ""), texts(result))
    }

    @Test
    fun titlePreviewAndPlainTextIncludeCells() {
        val content = note("\n", LineFormat(Title), LineFormat(Body), tables = listOf(table("t", 1, listOf("Budget", "Q1"), listOf("Rent", "900"))))
        assertEquals("Budget Q1", content.title)
        assertEquals("Rent 900", content.preview)
        assertEquals("\nBudget\tQ1\nRent\t900\n", content.plainText)
        assertFalse(content.isBlank)
    }

    @Test
    fun plainTextWithoutTablesIsText() {
        val content = note("A\nB", LineFormat(Title), LineFormat(Body))
        assertEquals("A\nB", content.plainText)
        assertEquals("A", content.title)
        assertEquals("B", content.preview)
    }
}
