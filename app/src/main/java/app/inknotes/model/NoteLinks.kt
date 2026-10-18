package app.inknotes.model

/** A web address, email or phone number found in a note, as a range of its text. */
data class NoteLink(val start: Int, val end: Int, val uri: String)

/**
 * Finds tappable links in plain text. Plain Kotlin regexes (not android.util.Patterns) so it can
 * be unit tested; deliberately conservative to avoid underlining things that aren't links.
 */
object NoteLinks {

    private val url = Regex("""\b(?:https?://|www\.)[^\s<>"]+""", RegexOption.IGNORE_CASE)
    private val email = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")
    private val phone = Regex("""(?<![\w+])\+?\d[\d\s().-]{6,}\d(?!\w)""")

    /** Characters that usually end a sentence rather than belong to the link. */
    private const val TRAILING = ".,;:!?)]}'\""

    fun find(text: String): List<NoteLink> {
        val links = mutableListOf<NoteLink>()
        fun overlaps(start: Int, end: Int) = links.any { start < it.end && end > it.start }

        for (match in url.findAll(text)) {
            val raw = match.value.trimEnd { it in TRAILING }
            val end = match.range.first + raw.length
            val uri = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
            links += NoteLink(match.range.first, end, uri)
        }
        for (match in email.findAll(text)) {
            if (!overlaps(match.range.first, match.range.last + 1)) {
                links += NoteLink(match.range.first, match.range.last + 1, "mailto:${match.value}")
            }
        }
        for (match in phone.findAll(text)) {
            val digits = match.value.count { it.isDigit() }
            // Dates, times and amounts have few digits or no separators worth dialing.
            if (digits in 8..15 && !overlaps(match.range.first, match.range.last + 1)) {
                val number = match.value.filter { it.isDigit() || it == '+' }
                links += NoteLink(match.range.first, match.range.last + 1, "tel:$number")
            }
        }
        return links.sortedBy { it.start }
    }
}
