package app.inknotes.model

import app.inknotes.model.LineStyle.Body
import app.inknotes.model.LineStyle.Checklist
import app.inknotes.model.LineStyle.Title
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteMergeTest {

    private fun note(vararg lines: Pair<String, LineFormat>, tables: List<NoteTable> = emptyList(), spans: List<InlineSpan> = emptyList()) =
        NoteContent(text = lines.joinToString("\n") { it.first }, lines = lines.map { it.second }, tables = tables, spans = spans)

    private val title = LineFormat(Title)
    private val body = LineFormat(Body)
    private val open = LineFormat(Checklist)
    private val done = LineFormat(Checklist, checked = true)

    private fun rows(content: NoteContent) = content.text.split('\n').zip(content.lines)

    private fun applied(outcome: NoteMerge.Outcome): NoteContent {
        assertTrue("expected Apply, got $outcome", outcome is NoteMerge.Outcome.Apply)
        return (outcome as NoteMerge.Outcome.Apply).content
    }

    private val groceries = note("Groceries" to title, "Milk" to open, "Eggs" to open, "Bread" to done)

    @Test
    fun noLocalEditsAdoptsRemote() {
        val remote = NoteChecklists.append(groceries, listOf("Butter"), keepCheckedLast = true)
        val outcome = NoteMerge.reconcile(groceries, groceries, remote)
        assertEquals(NoteMerge.Outcome.Apply(remote, keptLocalEdits = false), outcome)
    }

    @Test
    fun versionAloneIsNotAnEdit() {
        val stored = groceries.copy(version = NoteContent.CURRENT_VERSION)
        val outcome = NoteMerge.reconcile(groceries, groceries.copy(version = 1), stored)
        assertEquals(NoteMerge.Outcome.Apply(stored, keptLocalEdits = false), outcome)
    }

    @Test
    fun unchangedRemoteKeepsLocalEdits() {
        val local = note("Groceries" to title, "Milk" to open, "Eggs!" to open, "Bread" to done)
        assertEquals(NoteMerge.Outcome.Apply(local, keptLocalEdits = true), NoteMerge.reconcile(groceries, local, groceries))
    }

    @Test
    fun assistantItemsAreAddedOnTopOfLocalTyping() {
        // The user is typing a new item while an assistant adds two more.
        val local = note("Groceries" to title, "Milk" to open, "Eggs" to open, "Coff" to open, "Bread" to done)
        val remote = NoteChecklists.append(groceries, listOf("Butter", "Jam"), keepCheckedLast = true)
        val merged = applied(NoteMerge.reconcile(groceries, local, remote))
        assertEquals(
            listOf("Groceries" to title, "Milk" to open, "Eggs" to open, "Butter" to open, "Jam" to open, "Coff" to open, "Bread" to done),
            rows(merged),
        )
    }

    @Test
    fun insertionStaysAboveItemsCheckedLocally() {
        // Locally "Eggs" got checked and moved to the bottom; the assistant's item stays with the open ones.
        val local = note("Groceries" to title, "Milk" to open, "Bread" to done, "Eggs" to done)
        val remote = note("Groceries" to title, "Milk" to open, "Eggs" to open, "Butter" to open, "Bread" to done)
        val merged = applied(NoteMerge.reconcile(groceries, local, remote))
        assertEquals(
            listOf("Groceries" to title, "Milk" to open, "Butter" to open, "Bread" to done, "Eggs" to done),
            rows(merged),
        )
    }

    @Test
    fun insertionAfterDeletedLineUsesNextSurvivingNeighbour() {
        val local = note("Groceries" to title, "Milk" to open, "Bread" to done) // user deleted "Eggs"
        val remote = note("Groceries" to title, "Milk" to open, "Eggs" to open, "Butter" to open, "Bread" to done)
        val merged = applied(NoteMerge.reconcile(groceries, local, remote))
        assertEquals(listOf("Groceries" to title, "Milk" to open, "Butter" to open, "Bread" to done), rows(merged))
    }

    @Test
    fun appendAtEndWhenNothingAnchors() {
        val base = note("A" to title)
        val local = note("B" to title)
        val remote = note("A" to title, "new" to body)
        val merged = applied(NoteMerge.reconcile(base, local, remote))
        assertEquals(listOf("B" to title, "new" to body), rows(merged))
    }

    @Test
    fun insertedLinesKeepTheirSpansAndLocalSpansStay() {
        val base = note("T" to title, "one" to body)
        val local = note("T" to title, "one!" to body, spans = listOf(InlineSpan(InlineStyle.Italic, 2, 5)))
        val remote = note("T" to title, "bold" to body, "one" to body, spans = listOf(InlineSpan(InlineStyle.Bold, 2, 6)))
        val merged = applied(NoteMerge.reconcile(base, local, remote))
        assertEquals("T\nbold\none!", merged.text)
        assertEquals(listOf(InlineSpan(InlineStyle.Bold, 2, 6), InlineSpan(InlineStyle.Italic, 7, 10)), merged.spans)
    }

    @Test
    fun linesInsertedAroundTablesShiftThem() {
        val table = NoteTable("t", beforeLine = 2, rows = listOf(listOf("a", "b")))
        val base = note("T" to title, "x" to body, "y" to body, tables = listOf(table))
        val local = note("T" to title, "x" to body, "y edited" to body, tables = listOf(table))
        val remote = note("T" to title, "new" to body, "x" to body, "y" to body, tables = listOf(table.copy(beforeLine = 3)))
        val merged = applied(NoteMerge.reconcile(base, local, remote))
        assertEquals("T\nnew\nx\ny edited", merged.text)
        assertEquals(listOf(table.copy(beforeLine = 3)), merged.tables)
    }

    @Test
    fun tableAddedRemotelyIsKept() {
        val base = note("T" to title, "x" to body, "y" to body)
        val local = note("T" to title, "x" to body, "y!" to body)
        val table = NoteTable("t", beforeLine = 2, rows = listOf(listOf("a")))
        val remote = base.copy(tables = listOf(table))
        val merged = applied(NoteMerge.reconcile(base, local, remote))
        assertEquals("T\nx\ny!", merged.text)
        assertEquals(listOf(table), merged.tables)
    }

    @Test
    fun remoteEditOfExistingLineIsAConflict() {
        val local = note("Groceries" to title, "Milk" to open, "Eggs" to open, "Bread" to done, "Tea" to open)
        val remote = note("Groceries" to title, "Oat milk" to open, "Eggs" to open, "Bread" to done)
        val outcome = NoteMerge.reconcile(groceries, local, remote)
        assertEquals(NoteMerge.Outcome.Conflict(keep = local, copy = remote), outcome)
    }

    @Test
    fun remoteDeletionIsAConflict() {
        val local = note("Groceries" to title, "Milk" to open, "Eggs" to open, "Bread" to done, "Tea" to open)
        val remote = note("Groceries" to title, "Milk" to open, "Bread" to done)
        assertTrue(NoteMerge.reconcile(groceries, local, remote) is NoteMerge.Outcome.Conflict)
    }

    @Test
    fun remoteCheckIsAConflictNotADrop() {
        // A changed format is an edit, not an insertion: keep both versions.
        val local = note("Groceries" to title, "Milk" to open, "Eggs" to open, "Bread" to done, "Tea" to open)
        val remote = note("Groceries" to title, "Milk" to done, "Eggs" to open, "Bread" to done)
        assertTrue(NoteMerge.reconcile(groceries, local, remote) is NoteMerge.Outcome.Conflict)
    }

    @Test
    fun sameEditOnBothSidesIsNotAConflict() {
        val both = NoteChecklists.append(groceries, listOf("Butter"), keepCheckedLast = true)
        assertEquals(NoteMerge.Outcome.Apply(both, keptLocalEdits = false), NoteMerge.reconcile(groceries, both, both))
    }

    @Test
    fun applyInsertionsNeedsBaseInRemote() {
        assertNull(NoteMerge.applyInsertions(groceries, groceries, note("Other" to title)))
    }

    @Test
    fun titleSuffixGoesAfterTitleText() {
        val content = note("Groceries " to title, "Milk" to open, spans = listOf(InlineSpan(InlineStyle.Bold, 0, 9), InlineSpan(InlineStyle.Bold, 11, 15)))
        val copy = NoteMerge.withTitleSuffix(content, " (copy)")
        assertEquals("Groceries (copy) \nMilk", copy.text)
        assertEquals(listOf(InlineSpan(InlineStyle.Bold, 0, 9), InlineSpan(InlineStyle.Bold, 18, 22)), copy.spans)
        assertEquals("Groceries (copy)", copy.title)
    }

    @Test
    fun untitledCopyGetsSuffixAsTitle() {
        val table = NoteTable("t", beforeLine = 1, rows = listOf(listOf("a")))
        val content = note("" to title, "" to body, tables = listOf(table))
        val copy = NoteMerge.withTitleSuffix(content, " (copy)")
        assertEquals(listOf("(copy)" to title, "" to title, "" to body), rows(copy))
        assertEquals(2, copy.tables.single().beforeLine)
    }

    @Test
    fun caretFollowsTextAroundChange() {
        val old = "Milk\nEggs"
        val new = "Milk\nButter\nEggs"
        assertEquals(2, NoteMerge.mapOffset(old, new, 2)) // before the insertion
        assertEquals(new.length, NoteMerge.mapOffset(old, new, old.length)) // after it
        assertEquals(5, NoteMerge.mapOffset(old, new, 5))
        assertEquals(2, NoteMerge.mapOffset("abcdef", "ab", 5)) // inside a removed range
    }
}
