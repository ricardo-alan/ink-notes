package app.inknotes.model

import kotlinx.serialization.Serializable

/**
 * A note is plain text plus formatting metadata:
 * - one [LineFormat] per line (`lines.size == text.count('\n') + 1`)
 * - inline [InlineSpan]s as character ranges over [text].
 *
 * Kept independent from any UI type so it can be stored, searched and synced as-is.
 */
@Serializable
data class NoteContent(
    val version: Int = 1,
    val text: String = "",
    val lines: List<LineFormat> = listOf(LineFormat(LineStyle.Title)),
    val spans: List<InlineSpan> = emptyList(),
) {
    val isBlank: Boolean get() = text.isBlank()

    val title: String
        get() = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(200).orEmpty()

    val preview: String
        get() = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.drop(1).firstOrNull()?.take(200).orEmpty()

    companion object {
        val Empty = NoteContent()
    }
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
