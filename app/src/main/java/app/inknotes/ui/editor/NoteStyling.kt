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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.inknotes.model.InlineStyle
import app.inknotes.model.LineStyle
import app.inknotes.model.NoteContent

/** Width reserved on the left of list lines for bullets, numbers and checkboxes. */
val ListIndent: TextUnit = 30.sp

private const val LINE_SEPARATOR = '\u200B'

/**
 * The text as laid out on screen: line breaks become zero-width paragraph separators, plus a
 * trailing one so an empty last line still gets its own paragraph (and the caret can sit there).
 */
fun displayText(content: NoteContent): String = content.text.replace('\n', LINE_SEPARATOR) + LINE_SEPARATOR

private class SentinelMapping(private val length: Int) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int = offset
    override fun transformedToOriginal(offset: Int): Int = minOf(offset, length)
}

/**
 * Renders [NoteContent] formatting on top of the raw text field text.
 * Character offsets never change; only a trailing sentinel is added (see [displayText]).
 */
class NoteVisualTransformation(
    private val content: NoteContent,
    private val mutedColor: Color,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        if (text.text != content.text) return TransformedText(text, OffsetMapping.Identity)
        return TransformedText(styled(content, mutedColor), SentinelMapping(content.text.length))
    }

    override fun equals(other: Any?): Boolean =
        other is NoteVisualTransformation && other.content == content && other.mutedColor == mutedColor

    override fun hashCode(): Int = content.hashCode() * 31 + mutedColor.hashCode()
}

/**
 * Every line becomes its own paragraph. Paragraph boundaries already break lines, so each
 * '\n' is shown as a zero-width space (see [displayText]); otherwise Compose would render an
 * extra empty line at every boundary.
 */
fun styled(content: NoteContent, mutedColor: Color): AnnotatedString =
    AnnotatedString.Builder(displayText(content)).apply {
    val text = content.text
    var start = 0
    content.lines.forEach { format ->
        if (start > text.length) return@forEach
        val end = text.indexOf('\n', start).let { if (it == -1) text.length else it }
        when (format.style) {
            LineStyle.Title -> addStyle(SpanStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold), start, end)
            LineStyle.Heading -> addStyle(SpanStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold), start, end)
            else -> Unit
        }
        val paragraph = when (format.style) {
            LineStyle.Title -> ParagraphStyle(lineHeight = 34.sp)
            LineStyle.Heading -> ParagraphStyle(lineHeight = 28.sp)
            LineStyle.Body -> ParagraphStyle()
            else -> ParagraphStyle(textIndent = TextIndent(firstLine = ListIndent, restLine = ListIndent))
        }
        addStyle(paragraph, start, end + 1)
        if (format.style == LineStyle.Checklist && format.checked) addStyle(SpanStyle(color = mutedColor), start, end)
        start = end + 1
    }

    for (span in content.spans) {
        val s = span.start.coerceIn(0, text.length)
        val e = span.end.coerceIn(0, text.length)
        if (s >= e) continue
        when (span.style) {
            InlineStyle.Bold -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), s, e)
            InlineStyle.Italic -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), s, e)
            else -> Unit
        }
    }
    decorations(content, text.length).forEach { (range, decoration) -> addStyle(SpanStyle(textDecoration = decoration), range.first, range.last) }
}.toAnnotatedString()

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
