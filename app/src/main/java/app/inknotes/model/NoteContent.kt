package app.inknotes.model

import kotlinx.serialization.Serializable

/**
 * A note is plain text plus formatting metadata:
 * - one [LineFormat] per line (`lines.size == text.count('\n') + 1`)
 * - inline [InlineSpan]s as character ranges over [text]
 * - [NoteTable]s embedded between lines (version 2). Notes without tables encode exactly as version 1.
 *
 * Kept independent from any UI type so it can be stored, searched and synced as-is.
 */
@Serializable
data class NoteContent(
    val version: Int = 1,
    val text: String = "",
    val lines: List<LineFormat> = listOf(LineFormat(LineStyle.Title)),
    val spans: List<InlineSpan> = emptyList(),
    /** Sorted by [NoteTable.beforeLine]; see [NoteTables] for the invariants. */
    val tables: List<NoteTable> = emptyList(),
) {
    val isBlank: Boolean get() = text.isBlank() && tables.isEmpty()

    val title: String
        get() = readingLines(" ").map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(200).orEmpty()

    val preview: String
        get() = readingLines(" ").map { it.trim() }.filter { it.isNotEmpty() }.drop(1).firstOrNull()?.take(200).orEmpty()

    /** The whole note as plain text in reading order; table rows become tab-separated lines. */
    val plainText: String get() = readingLines("\t").joinToString("\n")

    /** Text lines with each table row spliced in where the table sits, cells joined by [cellSeparator]. */
    private fun readingLines(cellSeparator: String): Sequence<String> = sequence {
        val textLines = text.split('\n')
        var table = 0
        textLines.forEachIndexed { index, line ->
            while (table < tables.size && tables[table].beforeLine <= index) {
                tables[table++].rows.forEach { row -> yield(row.filter { it.isNotBlank() }.joinToString(cellSeparator)) }
            }
            yield(line)
        }
        while (table < tables.size) tables[table++].rows.forEach { row -> yield(row.filter { it.isNotBlank() }.joinToString(cellSeparator)) }
    }

    companion object {
        /** Written on save. Version 1 notes (no `version` key) decode unchanged. */
        const val CURRENT_VERSION = 2
        val Empty = NoteContent()
    }
}

/**
 * A grid of plain-text cells shown right before text line [beforeLine].
 * [rows] is rectangular: every row has the same number of cells.
 */
@Serializable
data class NoteTable(
    val id: String,
    val beforeLine: Int,
    val rows: List<List<String>>,
) {
    val rowCount: Int get() = rows.size
    val columnCount: Int get() = rows.firstOrNull()?.size ?: 0
}

@Serializable
enum class LineStyle { Title, Heading, Body, Bullet, Numbered, Checklist;

    val isList: Boolean get() = this == Bullet || this == Numbered || this == Checklist
}

@Serializable
data class LineFormat(
    val style: LineStyle = LineStyle.Body,
    val checked: Boolean = false,
)

@Serializable
enum class InlineStyle { Bold, Italic, Underline, Strikethrough }

/** [start] inclusive, [end] exclusive. */
@Serializable
data class InlineSpan(val style: InlineStyle, val start: Int, val end: Int)
