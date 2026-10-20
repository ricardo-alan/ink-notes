package app.inknotes.appfunctions

import app.inknotes.data.NoteSummary
import app.inknotes.data.NotesRepository

/** Picks the note an assistant means when it names one by title. Pure, so it can be unit tested. */
object NoteMatching {

    sealed interface Match {
        data class Found(val note: NoteSummary) : Match
        data class Ambiguous(val candidates: List<NoteSummary>) : Match
        data object None : Match
    }

    /**
     * An exact title match (ignoring case and accents) wins, the most recent first as [notes] are
     * ordered; otherwise the single note whose title contains [title]. Several partial matches
     * are ambiguous rather than guessed, since the caller is about to change that note.
     */
    fun byTitle(notes: List<NoteSummary>, title: String): Match {
        val wanted = key(title)
        if (wanted.isEmpty()) return Match.None
        notes.firstOrNull { key(it.title) == wanted }?.let { return Match.Found(it) }
        val partial = notes.filter { key(it.title).contains(wanted) }
        return when (partial.size) {
            0 -> Match.None
            1 -> Match.Found(partial.single())
            else -> Match.Ambiguous(partial)
        }
    }

    private fun key(text: String): String = NotesRepository.normalizeForSearch(text).trim().replace(Regex("\\s+"), " ")
}
