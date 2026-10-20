package app.inknotes.model

/**
 * Reconciles a note open in the editor with a newer version written by someone else
 * (an assistant through App Functions, Drive sync, another screen).
 *
 * Three versions take part:
 * - base: what the editor last loaded from or saved to the database;
 * - local: what the editor shows now (base plus any unsaved edits);
 * - remote: what the database holds now.
 *
 * Policy:
 * 1. Local has no unsaved edits (local == base): adopt remote as is.
 * 2. Remote only inserted lines or tables into base (what assistants do when they add checklist
 *    items, and the common case for a sync of a quick edit elsewhere): replay those insertions on
 *    top of local, each after the line it followed in base (lines are matched by text, so a line
 *    the user checked or restyled still anchors). Local edits stay untouched.
 * 3. Anything else (both sides edited or deleted lines): local wins in the note, and remote is
 *    returned whole as [Outcome.Conflict.copy] for the caller to keep as a separate note. Nothing
 *    the other side wrote is dropped silently, and nothing the user typed is overwritten.
 */
object NoteMerge {

    sealed interface Outcome {
        /** The editor should show [content]. With [keptLocalEdits] it still has unsaved edits. */
        data class Apply(val content: NoteContent, val keptLocalEdits: Boolean) : Outcome

        /** Keep [keep] in the note and save [copy] (the other side's version) as a new note. */
        data class Conflict(val keep: NoteContent, val copy: NoteContent) : Outcome
    }

    fun reconcile(base: NoteContent, local: NoteContent, remote: NoteContent): Outcome {
        if (same(local, base) || same(local, remote)) return Outcome.Apply(remote, keptLocalEdits = false)
        if (same(remote, base)) return Outcome.Apply(local, keptLocalEdits = true)
        val merged = applyInsertions(base, local, remote)
        return if (merged != null) Outcome.Apply(merged, keptLocalEdits = true) else Outcome.Conflict(local, remote)
    }

    /** Equal apart from the format version, which only changes when a note is stored. */
    fun same(a: NoteContent, b: NoteContent): Boolean = a.copy(version = 0) == b.copy(version = 0)

    /**
     * When [remote] is [base] with items inserted (base is a subsequence of remote), returns [local]
     * with the same items inserted at the matching places; otherwise null.
     */
    fun applyInsertions(base: NoteContent, local: NoteContent, remote: NoteContent): NoteContent? {
        val baseItems = items(base)
        val remoteItems = items(remote)
        val localItems = items(local)

        // Runs of remote-only items, keyed by how many base items precede them.
        val runs = mutableListOf<Pair<Int, List<Item>>>()
        var matched = 0
        var pending = mutableListOf<Item>()
        for (item in remoteItems) {
            if (matched < baseItems.size && item == baseItems[matched]) {
                if (pending.isNotEmpty()) runs += matched to pending.also { pending = mutableListOf() }
                matched++
            } else {
                pending += item
            }
        }
        if (matched < baseItems.size) return null
        if (pending.isNotEmpty()) runs += matched to pending
        if (runs.isEmpty()) return null

        val toLocal = matchIndices(baseItems.map(Item::key), localItems.map(Item::key))
        val result = localItems.toMutableList()
        // Splice from the bottom up so earlier positions stay valid.
        runs.map { (after, run) -> localPosition(after, toLocal, localItems.size) to run }
            .sortedByDescending { it.first }
            .forEach { (at, run) -> result.addAll(at, run) }
        return NoteTables.normalize(join(local, result))
    }

    /**
     * Where a run that followed the first [after] base items goes in local: right after the nearest
     * preceding base item that survives in local, else right before the nearest following one,
     * else at the end.
     */
    private fun localPosition(after: Int, toLocal: IntArray, localSize: Int): Int {
        for (i in after - 1 downTo 0) if (toLocal[i] >= 0) return toLocal[i] + 1
        for (i in after until toLocal.size) if (toLocal[i] >= 0) return toLocal[i]
        return localSize
    }

    /** For each element of [a], its index in [b] along a longest common subsequence, or -1. */
    private fun matchIndices(a: List<String>, b: List<String>): IntArray {
        val n = a.size
        val m = b.size
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
            lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        val result = IntArray(n) { -1 }
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> { result[i] = j; i++; j++ }
                lcs[i + 1][j] >= lcs[i][j + 1] -> i++
                else -> j++
            }
        }
        return result
    }

    /** A note's lines and tables in reading order; a table comes right before the line it is anchored to. */
    private sealed interface Item {
        /** Identity used to anchor insertions in a locally edited note. */
        val key: String

        data class Line(val text: String, val format: LineFormat, val spans: List<InlineSpan>) : Item {
            override val key: String get() = "L$text"
        }

        data class Table(val id: String, val rows: List<List<String>>) : Item {
            override val key: String get() = "T$id"
        }
    }

    private fun items(content: NoteContent): List<Item> = buildList {
        val text = content.text
        var start = 0
        var table = 0
        content.lines.forEachIndexed { index, format ->
            while (table < content.tables.size && content.tables[table].beforeLine <= index) {
                content.tables[table++].let { add(Item.Table(it.id, it.rows)) }
            }
            val end = if (index == content.lines.lastIndex) text.length else text.indexOf('\n', start).let { if (it == -1) text.length else it }
            val spans = content.spans
                .filter { it.start < end && it.end > start }
                .map { it.copy(start = maxOf(it.start, start) - start, end = minOf(it.end, end) - start) }
            add(Item.Line(text.substring(start, end), format, spans))
            start = minOf(end + 1, text.length)
        }
        while (table < content.tables.size) content.tables[table++].let { add(Item.Table(it.id, it.rows)) }
    }

    private fun join(template: NoteContent, items: List<Item>): NoteContent {
        val lines = mutableListOf<Item.Line>()
        val tables = mutableListOf<NoteTable>()
        val spans = mutableListOf<InlineSpan>()
        var offset = 0
        for (item in items) when (item) {
            is Item.Table -> tables += NoteTable(item.id, lines.size, item.rows)
            is Item.Line -> {
                item.spans.mapTo(spans) { it.copy(start = it.start + offset, end = it.end + offset) }
                offset += item.text.length + 1
                lines += item
            }
        }
        if (lines.isEmpty()) lines += Item.Line("", LineFormat(LineStyle.Title), emptyList())
        return template.copy(
            text = lines.joinToString("\n") { it.text },
            lines = lines.map { it.format },
            spans = NoteEditing.normalize(spans),
            tables = tables,
        )
    }

    /**
     * [content] with [suffix] added to the end of its title line, so a conflict copy is easy to
     * tell apart in the list. An untitled note gets [suffix] (trimmed) as its title.
     */
    fun withTitleSuffix(content: NoteContent, suffix: String): NoteContent {
        val textLines = content.text.split('\n')
        val titleLine = textLines.indexOfFirst { it.isNotBlank() }
        if (titleLine == -1) {
            val title = suffix.trim()
            return content.copy(
                text = title + "\n" + content.text,
                lines = listOf(LineFormat(LineStyle.Title)) + content.lines,
                spans = content.spans.map { it.copy(start = it.start + title.length + 1, end = it.end + title.length + 1) },
                tables = content.tables.map { it.copy(beforeLine = it.beforeLine + 1) },
            )
        }
        val at = NoteEditing.lineStart(content.text, titleLine) + textLines[titleLine].trimEnd().length
        return content.copy(
            text = content.text.substring(0, at) + suffix + content.text.substring(at),
            spans = content.spans.map {
                it.copy(start = if (it.start >= at) it.start + suffix.length else it.start, end = if (it.end > at) it.end + suffix.length else it.end)
            },
        )
    }

    /**
     * Maps a caret [offset] in [old] to [new] when the text changed around it: unchanged if the
     * change is after it, shifted by the length difference if the change is before it.
     */
    fun mapOffset(old: String, new: String, offset: Int): Int {
        if (old == new) return offset.coerceIn(0, new.length)
        var prefix = 0
        val shortest = minOf(old.length, new.length)
        while (prefix < shortest && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < shortest - prefix && old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
        return when {
            offset <= prefix -> offset
            offset >= old.length - suffix -> offset + new.length - old.length
            else -> prefix + (new.length - suffix - prefix)
        }.coerceIn(0, new.length)
    }
}
