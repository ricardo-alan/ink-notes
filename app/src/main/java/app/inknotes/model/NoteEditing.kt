package app.inknotes.model

/** Pure editing operations on [NoteContent]. Everything here is UI-agnostic and unit-testable. */
object NoteEditing {

    data class Result(val content: NoteContent, val selectionStart: Int, val selectionEnd: Int = selectionStart)

    /**
     * Reconciles formatting after the text changed from [old].text to [newText].
     * Assumes a single contiguous edit (what a text field produces).
     */
    fun applyTextChange(old: NoteContent, newText: String, selStart: Int, selEnd: Int): Result {
        val oldText = old.text
        if (oldText == newText) return Result(old, selStart, selEnd)

        var prefix = 0
        val maxPrefix = minOf(oldText.length, newText.length)
        while (prefix < maxPrefix && oldText[prefix] == newText[prefix]) prefix++
        var suffix = 0
        val maxSuffix = minOf(oldText.length, newText.length) - prefix
        while (suffix < maxSuffix && oldText[oldText.length - 1 - suffix] == newText[newText.length - 1 - suffix]) suffix++

        val oldEnd = oldText.length - suffix
        val inserted = newText.substring(prefix, newText.length - suffix)
        val removed = oldText.substring(prefix, oldEnd)

        val startLine = lineIndexAt(oldText, prefix)
        val currentFormat = old.lines.getOrElse(startLine) { LineFormat() }

        // Enter on an empty list item ends the list instead of adding another item.
        if (removed.isEmpty() && inserted == "\n" && currentFormat.style.isList &&
            lineText(oldText, startLine).isEmpty()
        ) {
            val lines = old.lines.toMutableList()
            lines[startLine] = LineFormat(LineStyle.Body)
            return Result(old.copy(lines = lines), prefix)
        }

        val endLine = lineIndexAt(oldText, oldEnd)
        val newLineCount = endLine - startLine + 1 + inserted.count { it == '\n' } - removed.count { it == '\n' }
        val continuation = continuationOf(currentFormat)
        val middle = MutableList(newLineCount) { i -> if (i == 0) currentFormat else continuation }

        // Enter at the very start of a non-empty line: the content moves down and keeps its format.
        val atLineStart = prefix == lineStart(oldText, startLine)
        if (removed.isEmpty() && atLineStart && inserted.all { it == '\n' } && lineText(oldText, startLine).isNotEmpty()) {
            for (i in middle.indices) middle[i] = if (i == middle.lastIndex) currentFormat else continuation
        }

        val lines = old.lines.subList(0, startLine) + middle + old.lines.subList(minOf(endLine + 1, old.lines.size), old.lines.size)
        val spans = shiftSpans(old.spans, prefix, oldEnd, inserted.length)

        return Result(NoteContent(old.version, newText, lines, spans), selStart, selEnd)
    }

    private fun continuationOf(format: LineFormat): LineFormat = when (format.style) {
        LineStyle.Title, LineStyle.Heading -> LineFormat(LineStyle.Body)
        else -> LineFormat(format.style, checked = false)
    }

    private fun shiftSpans(spans: List<InlineSpan>, start: Int, oldEnd: Int, insertedLength: Int): List<InlineSpan> {
        val delta = insertedLength - (oldEnd - start)
        fun map(pos: Int): Int = when {
            // Pure insertion: text typed right after a styled run continues it, typed right before does not.
            oldEnd == start -> if (pos < start) pos else pos + insertedLength
            pos <= start -> pos
            pos >= oldEnd -> pos + delta
            else -> start + insertedLength
        }
        return normalize(spans.map { it.copy(start = map(it.start), end = map(it.end)) })
    }

    /** Applies [style] to every line touched by the selection, or reverts them to body text if they all have it. */
    fun setLineStyle(content: NoteContent, selStart: Int, selEnd: Int, style: LineStyle): NoteContent {
        val (from, to) = lineRange(content.text, selStart, selEnd)
        val allHaveIt = (from..to).all { content.lines[it].style == style }
        val target = if (allHaveIt && style != LineStyle.Body) LineStyle.Body else style
        val lines = content.lines.mapIndexed { i, f -> if (i in from..to) LineFormat(target, checked = f.checked && target == LineStyle.Checklist) else f }
        return content.copy(lines = lines)
    }

    fun toggleChecked(content: NoteContent, line: Int): NoteContent {
        val format = content.lines.getOrNull(line) ?: return content
        if (format.style != LineStyle.Checklist) return content
        val lines = content.lines.toMutableList().also { it[line] = format.copy(checked = !format.checked) }
        return content.copy(lines = lines)
    }

    /** Toggles an inline style over the selection (or the word under the cursor when nothing is selected). */
    fun toggleInline(content: NoteContent, selStart: Int, selEnd: Int, style: InlineStyle): NoteContent {
        var start = minOf(selStart, selEnd)
        var end = maxOf(selStart, selEnd)
        if (start == end) {
            val text = content.text
            while (start > 0 && text[start - 1].isLetterOrDigit()) start--
            while (end < text.length && text[end].isLetterOrDigit()) end++
            if (start == end) return content
        }
        val same = content.spans.filter { it.style == style }
        val others = content.spans.filter { it.style != style }
        val covered = isCovered(same, start, end)
        val updated = if (covered) {
            same.flatMap { s ->
                if (s.end <= start || s.start >= end) {
                    listOf(s)
                } else {
                    listOfNotNull(
                        s.copy(end = start).takeIf { s.start < start },
                        s.copy(start = end).takeIf { s.end > end },
                    )
                }
            }
        } else {
            same + InlineSpan(style, start, end)
        }
        return content.copy(spans = normalize(others + updated))
    }

    fun isInlineActive(content: NoteContent, selStart: Int, selEnd: Int, style: InlineStyle): Boolean {
        val start = minOf(selStart, selEnd)
        val end = maxOf(selStart, selEnd)
        val same = content.spans.filter { it.style == style }
        return if (start == end) same.any { start > it.start && start <= it.end } else isCovered(same, start, end)
    }

    fun lineStyleAt(content: NoteContent, offset: Int): LineStyle =
        content.lines.getOrElse(lineIndexAt(content.text, offset)) { LineFormat() }.style

    private fun isCovered(spans: List<InlineSpan>, start: Int, end: Int): Boolean {
        var pos = start
        for (s in spans.sortedBy { it.start }) {
            if (s.start <= pos && s.end > pos) pos = s.end
            if (pos >= end) return true
        }
        return false
    }

    /** Sorts and merges overlapping or touching spans of the same style. */
    fun normalize(spans: List<InlineSpan>): List<InlineSpan> =
        spans.filter { it.start < it.end }.groupBy { it.style }.flatMap { (_, group) ->
            group.sortedBy { it.start }.fold(mutableListOf<InlineSpan>()) { acc, s ->
                val last = acc.lastOrNull()
                if (last != null && s.start <= last.end) acc[acc.lastIndex] = last.copy(end = maxOf(last.end, s.end)) else acc.add(s)
                acc
            }
        }.sortedWith(compareBy({ it.start }, { it.style.ordinal }))

    fun lineIndexAt(text: String, offset: Int): Int {
        var count = 0
        for (i in 0 until minOf(offset, text.length)) if (text[i] == '\n') count++
        return count
    }

    fun lineStart(text: String, line: Int): Int {
        if (line == 0) return 0
        var count = 0
        for (i in text.indices) if (text[i] == '\n' && ++count == line) return i + 1
        return text.length
    }

    fun lineText(text: String, line: Int): String {
        val start = lineStart(text, line)
        val end = text.indexOf('\n', start).let { if (it == -1) text.length else it }
        return text.substring(start, end)
    }

    private fun lineRange(text: String, selStart: Int, selEnd: Int): Pair<Int, Int> =
        lineIndexAt(text, minOf(selStart, selEnd)) to lineIndexAt(text, maxOf(selStart, selEnd))
}
