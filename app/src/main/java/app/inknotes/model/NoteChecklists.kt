package app.inknotes.model

/** Builds and extends checklists from plain strings, for callers outside the editor (assistants). */
object NoteChecklists {

    data class Item(val text: String, val checked: Boolean)

    /** A note with [title] as its title line followed by [body] as body text. */
    fun note(title: String, body: String): NoteContent {
        val text = listOf(singleLine(title), body.trimEnd()).filter { it.isNotEmpty() }.joinToString("\n")
        val lineCount = text.count { it == '\n' } + 1
        return NoteContent(text = text, lines = List(lineCount) { if (it == 0) LineFormat(LineStyle.Title) else LineFormat(LineStyle.Body) })
    }

    /** A note with [title] as its title line followed by one unchecked item per entry of [items]. */
    fun checklist(title: String, items: List<String>): NoteContent {
        val cleaned = clean(items)
        return NoteContent(
            text = (listOf(singleLine(title)) + cleaned).joinToString("\n"),
            lines = listOf(LineFormat(LineStyle.Title)) + cleaned.map { LineFormat(LineStyle.Checklist) },
        )
    }

    /** The checklist items of [content] in order, with their text trimmed. */
    fun items(content: NoteContent): List<Item> =
        content.text.split('\n').zip(content.lines)
            .filter { (_, format) -> format.style == LineStyle.Checklist }
            .map { (text, format) -> Item(text.trim(), format.checked) }

    /**
     * Adds [items] as unchecked checklist lines to the note's last checklist. With [keepCheckedLast]
     * they go above that checklist's trailing checked items, matching how the editor sorts; otherwise
     * at its very end. A note without a checklist gets a new one at the end (reusing an empty last line).
     */
    fun append(content: NoteContent, items: List<String>, keepCheckedLast: Boolean): NoteContent {
        val cleaned = clean(items)
        if (cleaned.isEmpty()) return content
        val lastChecklist = content.lines.indexOfLast { it.style == LineStyle.Checklist }
        if (lastChecklist == -1) {
            val last = content.lines.lastIndex
            return if (last > 0 && content.lines[last].style == LineStyle.Body && NoteEditing.lineText(content.text, last).isBlank()) {
                val start = NoteEditing.lineStart(content.text, last)
                content.copy(
                    text = content.text.substring(0, start) + cleaned.joinToString("\n"),
                    lines = content.lines.dropLast(1) + cleaned.map { LineFormat(LineStyle.Checklist) },
                    spans = NoteEditing.normalize(content.spans.map { it.copy(start = minOf(it.start, start), end = minOf(it.end, start)) }),
                )
            } else {
                insertLines(content, content.lines.size, cleaned)
            }
        }
        val run = NoteEditing.listRun(content, lastChecklist)
        var at = run.last + 1
        if (keepCheckedLast) while (at > run.first && content.lines[at - 1].checked) at--
        return insertLines(content, at, cleaned)
    }

    /** Inserts unchecked checklist lines before line [at] (or after the last line when `at == lines.size`). */
    private fun insertLines(content: NoteContent, at: Int, texts: List<String>): NoteContent {
        val text = content.text
        val atEnd = at >= content.lines.size
        val position = if (atEnd) text.length else NoteEditing.lineStart(text, at)
        val inserted = if (atEnd) texts.joinToString("") { "\n$it" } else texts.joinToString("") { "$it\n" }
        val formats = texts.map { LineFormat(LineStyle.Checklist) }
        val lines = content.lines.subList(0, at) + formats + content.lines.subList(at, content.lines.size)
        val spans = content.spans.map {
            it.copy(
                start = if (it.start >= position && !atEnd) it.start + inserted.length else it.start,
                end = if (it.end > position) it.end + inserted.length else it.end,
            )
        }
        val tables = content.tables.map { if (it.beforeLine >= at) it.copy(beforeLine = it.beforeLine + texts.size) else it }
        return content.copy(
            text = text.substring(0, position) + inserted + text.substring(position),
            lines = lines,
            spans = NoteEditing.normalize(spans),
            tables = tables,
        )
    }

    private fun clean(items: List<String>): List<String> = items.map(::singleLine).filter { it.isNotEmpty() }

    private fun singleLine(text: String): String = text.replace(Regex("\\s*\\n\\s*"), " ").trim()
}
