package app.inknotes.model

/**
 * A note laid out for editing: text blocks and tables, alternating, always starting and ending
 * with text. Each text block is a run of whole lines of [NoteContent.text].
 */
sealed interface NoteBlock {
    /**
     * Lines [firstLine] until [endLine] (exclusive), never empty.
     * [key] is the id of the table right before it ("" for the first block), stable while editing.
     */
    data class Text(val key: String, val firstLine: Int, val endLine: Int) : NoteBlock

    data class Table(val table: NoteTable) : NoteBlock
}

/**
 * Pure table operations on [NoteContent]. They keep these invariants, which [normalize] restores:
 * - tables are sorted and anchored in `1 until lines.size`, with distinct anchors,
 *   so there is always at least one text line before, between and after tables;
 * - every table has at least one row and one column and is rectangular.
 *
 * Tables live outside [NoteContent.text]: the text on both sides of a table is joined by a
 * plain '\n', so removing a table simply merges the text around it.
 */
object NoteTables {

    const val DEFAULT_ROWS = 3
    const val DEFAULT_COLUMNS = 3

    fun blocks(content: NoteContent): List<NoteBlock> = buildList {
        var start = 0
        var key = ""
        for (table in content.tables) {
            add(NoteBlock.Text(key, start, table.beforeLine))
            add(NoteBlock.Table(table))
            start = table.beforeLine
            key = table.id
        }
        add(NoteBlock.Text(key, start, content.lines.size))
    }

    fun textBlock(content: NoteContent, key: String): NoteBlock.Text? =
        blocks(content).firstOrNull { it is NoteBlock.Text && it.key == key } as NoteBlock.Text?

    /** Character range of [block] in the note text, end exclusive (without the line break that follows it). */
    fun charRange(content: NoteContent, block: NoteBlock.Text): IntRange {
        val start = NoteEditing.lineStart(content.text, block.firstLine)
        val end = if (block.endLine >= content.lines.size) content.text.length else NoteEditing.lineStart(content.text, block.endLine) - 1
        return start until end
    }

    /** The lines of [block] as a note of their own, for editing in a single text field. */
    fun subContent(content: NoteContent, block: NoteBlock.Text): NoteContent {
        val range = charRange(content, block)
        val start = range.first
        val end = range.last + 1
        return NoteContent(
            version = content.version,
            text = content.text.substring(start, end),
            lines = content.lines.subList(block.firstLine, block.endLine),
            spans = clip(content.spans, start, end).map { it.copy(start = it.start - start, end = it.end - start) },
        )
    }

    /** Puts an edited [sub] (from [subContent]) back in place of [block]. */
    fun replaceText(content: NoteContent, block: NoteBlock.Text, sub: NoteContent): NoteContent {
        val range = charRange(content, block)
        val start = range.first
        val end = range.last + 1
        val charDelta = sub.text.length - (end - start)
        val lineDelta = sub.lines.size - (block.endLine - block.firstLine)
        val spans = clip(content.spans, 0, start) +
            sub.spans.map { it.copy(start = it.start + start, end = it.end + start) } +
            clip(content.spans, end, content.text.length).map { it.copy(start = it.start + charDelta, end = it.end + charDelta) }
        return content.copy(
            text = content.text.substring(0, start) + sub.text + content.text.substring(end),
            lines = content.lines.subList(0, block.firstLine) + sub.lines + content.lines.subList(block.endLine, content.lines.size),
            spans = NoteEditing.normalize(spans),
            tables = content.tables.map { if (it.beforeLine >= block.endLine) it.copy(beforeLine = it.beforeLine + lineDelta) else it },
        )
    }

    data class Inserted(val content: NoteContent, val tableId: String)

    /**
     * Inserts an empty table below the line holding [caret] (an offset in [block]'s own text).
     * On an empty line other than the block's first, the table takes that line's place instead, so
     * the empty line ends up after it. A table never ends a block: an empty line is added below if needed.
     */
    fun insertTable(
        content: NoteContent,
        block: NoteBlock.Text,
        caret: Int,
        id: String,
        rows: Int = DEFAULT_ROWS,
        columns: Int = DEFAULT_COLUMNS,
    ): Inserted {
        val sub = subContent(content, block)
        val localLine = NoteEditing.lineIndexAt(sub.text, caret.coerceIn(0, sub.text.length))
        var updated = content
        val anchor = if (localLine > 0 && NoteEditing.lineText(sub.text, localLine).isEmpty()) {
            block.firstLine + localLine
        } else {
            val below = block.firstLine + localLine + 1
            if (below == block.endLine) updated = insertEmptyLine(updated, below)
            below
        }
        val table = NoteTable(id, anchor, List(rows) { List(columns) { "" } })
        val tables = (updated.tables + table).sortedBy { it.beforeLine }
        return Inserted(updated.copy(tables = tables), id)
    }

    fun deleteTable(content: NoteContent, id: String): NoteContent =
        content.copy(tables = content.tables.filterNot { it.id == id })

    fun setCell(content: NoteContent, id: String, row: Int, column: Int, text: String): NoteContent =
        updateTable(content, id) { table ->
            val clean = text.replace('\n', ' ')
            if (table.rows.getOrNull(row)?.getOrNull(column) == null) table
            else table.copy(rows = table.rows.mapIndexed { r, cells -> if (r == row) cells.mapIndexed { c, v -> if (c == column) clean else v } else cells })
        }

    /** Inserts an empty row at index [at]. */
    fun addRow(content: NoteContent, id: String, at: Int): NoteContent = updateTable(content, id) { table ->
        val rows = table.rows.toMutableList()
        rows.add(at.coerceIn(0, rows.size), List(table.columnCount) { "" })
        table.copy(rows = rows)
    }

    /** Inserts an empty column at index [at]. */
    fun addColumn(content: NoteContent, id: String, at: Int): NoteContent = updateTable(content, id) { table ->
        table.copy(rows = table.rows.map { cells -> cells.toMutableList().apply { add(at.coerceIn(0, size), "") } })
    }

    /** Removes a row; removing the only row removes the table. */
    fun removeRow(content: NoteContent, id: String, row: Int): NoteContent {
        val table = content.tables.firstOrNull { it.id == id } ?: return content
        if (table.rowCount <= 1) return deleteTable(content, id)
        return updateTable(content, id) { it.copy(rows = it.rows.filterIndexed { r, _ -> r != row }) }
    }

    /** Removes a column; removing the only column removes the table. */
    fun removeColumn(content: NoteContent, id: String, column: Int): NoteContent {
        val table = content.tables.firstOrNull { it.id == id } ?: return content
        if (table.columnCount <= 1) return deleteTable(content, id)
        return updateTable(content, id) { it.copy(rows = it.rows.map { cells -> cells.filterIndexed { c, _ -> c != column } }) }
    }

    /** Repairs content decoded from storage or sync so that the invariants above hold. */
    fun normalize(content: NoteContent): NoteContent {
        val lineCount = content.text.count { it == '\n' } + 1
        var result = content.copy(
            lines = List(lineCount) { content.lines.getOrElse(it) { LineFormat() } },
            spans = NoteEditing.normalize(content.spans.map { it.copy(start = it.start.coerceIn(0, content.text.length), end = it.end.coerceIn(0, content.text.length)) }),
            tables = emptyList(),
        )
        if (content.tables.isEmpty()) return result

        val tables = mutableListOf<NoteTable>()
        var shift = 0
        for (table in content.tables.sortedBy { it.beforeLine }) {
            var anchor = table.beforeLine.coerceIn(1, lineCount) + shift
            if (tables.isNotEmpty() && anchor <= tables.last().beforeLine) {
                // Two tables in a row: give them an empty line in between.
                anchor = tables.last().beforeLine
                result = insertEmptyLine(result, anchor)
                anchor++
                shift++
            }
            tables += rectangular(table).copy(beforeLine = anchor)
        }
        if (tables.last().beforeLine >= result.lines.size) result = insertEmptyLine(result, result.lines.size)
        return result.copy(tables = tables)
    }

    /** Inserts an empty body line at line index [at] (`1..lines.size`), moving later tables down. */
    private fun insertEmptyLine(content: NoteContent, at: Int): NoteContent {
        val text = content.text
        val position = if (at >= content.lines.size) text.length else NoteEditing.lineStart(text, at)
        val newText = if (at >= content.lines.size) "$text\n" else text.substring(0, position) + "\n" + text.substring(position)
        val lines = content.lines.toMutableList().apply { add(at.coerceAtMost(size), LineFormat(LineStyle.Body)) }
        val spans = content.spans.map {
            it.copy(start = if (it.start >= position && at < content.lines.size) it.start + 1 else it.start, end = if (it.end > position) it.end + 1 else it.end)
        }
        val tables = content.tables.map { if (it.beforeLine >= at) it.copy(beforeLine = it.beforeLine + 1) else it }
        return content.copy(text = newText, lines = lines, spans = spans, tables = tables)
    }

    private fun rectangular(table: NoteTable): NoteTable {
        val columns = maxOf(1, table.rows.maxOfOrNull { it.size } ?: 0)
        val rows = table.rows.ifEmpty { listOf(emptyList()) }
        return table.copy(rows = rows.map { cells -> List(columns) { cells.getOrElse(it) { "" }.replace('\n', ' ') } })
    }

    private fun updateTable(content: NoteContent, id: String, transform: (NoteTable) -> NoteTable): NoteContent =
        content.copy(tables = content.tables.map { if (it.id == id) transform(it) else it })

    /** The parts of [spans] inside [start, end). */
    private fun clip(spans: List<InlineSpan>, start: Int, end: Int): List<InlineSpan> =
        spans.mapNotNull { span ->
            val s = maxOf(span.start, start)
            val e = minOf(span.end, end)
            if (s < e) span.copy(start = s, end = e) else null
        }
}
