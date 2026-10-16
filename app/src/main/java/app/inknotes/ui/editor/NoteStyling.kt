package app.inknotes.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.inknotes.model.InlineStyle
import app.inknotes.model.LineFormat
import app.inknotes.model.LineStyle
import app.inknotes.model.NoteContent

/** Width reserved on the left of list lines for bullets, numbers and checkboxes. */
val ListIndent: TextUnit = 30.sp

private const val ZERO_WIDTH = '\u200B'

/**
 * What the editor lays out: the note text with formatting, plus invisible characters that
 * exist only on screen.
 *
 * - Every line is its own paragraph. Paragraph boundaries already break lines, so each '\n'
 *   is shown as a zero-width space; otherwise Compose renders an extra empty line per boundary.
 * - A trailing zero-width space gives an empty last line its own paragraph, so the caret can sit there.
 * - Between blocks, a one-character spacer paragraph adds vertical space (see [gapBetween]).
 *   Compose has no paragraph spacing, and line height applies to every wrapped line.
 */
class NoteDisplay(val text: AnnotatedString, val mapping: OffsetMapping)

/**
 * Space between two consecutive lines, depending on what kind of block each one is.
 * Must stay above zero: every line break needs a spacer for caret placement to work.
 */
private fun gapBetween(previous: LineFormat, next: LineFormat): TextUnit = when {
    previous.style == LineStyle.Title -> 10.sp
    next.style == LineStyle.Title -> 16.sp
    next.style == LineStyle.Heading -> 14.sp
    previous.style == LineStyle.Heading -> 6.sp
    previous.style.isList && previous.style == next.style -> 2.sp
    previous.style.isList || next.style.isList -> 10.sp
    else -> 6.sp
}

private val SpacerFontSize = 1.sp

fun buildNoteDisplay(content: NoteContent, mutedColor: Color): NoteDisplay {
    val text = content.text
    val lineStarts = buildList {
        add(0)
        text.forEachIndexed { i, c -> if (c == '\n') add(i + 1) }
    }
    val formats = List(lineStarts.size) { content.lines.getOrElse(it) { LineFormat() } }

    // Original offsets where a spacer goes (start of the following line) and its height.
    val spacers = (1 until lineStarts.size).map { i -> lineStarts[i] to gapBetween(formats[i - 1], formats[i]) }
    val mapping = SpacerMapping(spacers.map { it.first }.toIntArray(), text.length)

    val display = buildString(text.length + spacers.size + 1) {
        var spacer = 0
        text.forEachIndexed { i, c ->
            while (spacer < spacers.size && spacers[spacer].first == i) { append(ZERO_WIDTH); spacer++ }
            append(if (c == '\n') ZERO_WIDTH else c)
        }
        while (spacer < spacers.size) { append(ZERO_WIDTH); spacer++ }
        append(ZERO_WIDTH)
    }

    val annotated = AnnotatedString.Builder(display).apply {
        fun style(style: SpanStyle, start: Int, end: Int) {
            val s = mapping.startOf(start)
            val e = mapping.endOf(end)
            if (s < e) addStyle(style, s, e)
        }

        lineStarts.forEachIndexed { index, start ->
            val format = formats[index]
            val end = if (index + 1 < lineStarts.size) lineStarts[index + 1] - 1 else text.length
            when (format.style) {
                LineStyle.Title -> style(SpanStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold), start, end)
                LineStyle.Heading -> style(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), start, end)
                else -> Unit
            }
            if (format.style == LineStyle.Checklist && format.checked) style(SpanStyle(color = mutedColor), start, end)
            val paragraph = when (format.style) {
                LineStyle.Title -> ParagraphStyle(lineHeight = 34.sp)
                LineStyle.Heading -> ParagraphStyle(lineHeight = 28.sp)
                LineStyle.Body -> ParagraphStyle()
                else -> ParagraphStyle(textIndent = TextIndent(firstLine = ListIndent, restLine = ListIndent))
            }
            // The paragraph includes the character that stands in for its line break (or the trailing sentinel).
            addStyle(paragraph, mapping.startOf(start), mapping.endOf(end) + 1)
        }

        spacers.forEachIndexed { k, (position, gap) ->
            val at = position + k
            addStyle(
                ParagraphStyle(
                    lineHeight = gap,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.None,
                        mode = LineHeightStyle.Mode.Fixed,
                    ),
                ),
                at,
                at + 1,
            )
            addStyle(SpanStyle(fontSize = SpacerFontSize), at, at + 1)
        }

        for (span in content.spans) {
            when (span.style) {
                InlineStyle.Bold -> style(SpanStyle(fontWeight = FontWeight.Bold), span.start, span.end)
                InlineStyle.Italic -> style(SpanStyle(fontStyle = FontStyle.Italic), span.start, span.end)
                else -> Unit
            }
        }
        decorations(content, text.length).forEach { (range, decoration) ->
            style(SpanStyle(textDecoration = decoration), range.first, range.last)
        }
    }.toAnnotatedString()

    return NoteDisplay(annotated, mapping)
}

/**
 * Maps note offsets to display offsets, skipping the spacers. A spacer sits right before the
 * line it precedes, so the start of a line maps to after its spacer.
 */
private class SpacerMapping(private val spacers: IntArray, private val length: Int) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int = startOf(offset)

    override fun transformedToOriginal(offset: Int): Int {
        var before = 0
        while (before < spacers.size && spacers[before] + before < offset) before++
        // Tapping past the end of a line lands on the spacer that follows it: keep the caret on that line.
        if (before < spacers.size && spacers[before] + before == offset) return spacers[before] - 1
        return (offset - before).coerceIn(0, length)
    }

    /** Display offset for a range start or caret position. */
    fun startOf(offset: Int): Int = offset + spacers.count { it <= offset }

    /** Display offset for an exclusive range end: does not swallow the next line's spacer. */
    fun endOf(offset: Int): Int = offset + spacers.count { it < offset }
}

/** Renders [NoteContent] formatting on top of the raw text field text. */
class NoteVisualTransformation(
    private val content: NoteContent,
    private val display: NoteDisplay,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        if (text.text != content.text) return TransformedText(text, OffsetMapping.Identity)
        return TransformedText(display.text, display.mapping)
    }

    override fun equals(other: Any?): Boolean = other is NoteVisualTransformation && other.content == content

    override fun hashCode(): Int = content.hashCode()
}

/** Underline and strikethrough must be combined into one decoration where they overlap. */
private fun decorations(content: NoteContent, length: Int): List<Pair<IntRange, TextDecoration>> {
    val relevant = content.spans.filter { it.style == InlineStyle.Underline || it.style == InlineStyle.Strikethrough }
    if (relevant.isEmpty()) return emptyList()
    val bounds = (relevant.flatMap { listOf(it.start, it.end) }).map { it.coerceIn(0, length) }.distinct().sorted()
    return bounds.zipWithNext().mapNotNull { (a, b) ->
        val underline = relevant.any { it.style == InlineStyle.Underline && it.start <= a && it.end >= b }
        val strike = relevant.any { it.style == InlineStyle.Strikethrough && it.start <= a && it.end >= b }
        val decoration = when {
            underline && strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
            underline -> TextDecoration.Underline
            strike -> TextDecoration.LineThrough
            else -> return@mapNotNull null
        }
        // IntRange used as [start, end) here.
        IntRange(a, b) to decoration
    }
}
