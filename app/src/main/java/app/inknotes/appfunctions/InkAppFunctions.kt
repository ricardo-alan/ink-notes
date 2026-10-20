package app.inknotes.appfunctions

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionPermissionRequiredException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import app.inknotes.InkApplication
import app.inknotes.data.NoteScope
import app.inknotes.data.NoteSummary
import app.inknotes.data.NotesRepository
import app.inknotes.model.LineStyle
import app.inknotes.model.NoteChecklists
import app.inknotes.model.NoteContent
import kotlinx.coroutines.flow.first

/**
 * Ink's actions for system assistants (App Functions, Android 16+). The compiler generates
 * [InkAppFunctionService] from this class, plus `assets/ink_app_functions.xml` describing it.
 *
 * Writing is always allowed. Reading note contents needs the user's opt-in in Settings
 * ([app.inknotes.settings.AssistantSettings]); without it the read functions refuse.
 */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(serviceName = "InkAppFunctionService", appFunctionXmlFileName = "ink_app_functions")
abstract class InkAppFunctions : AppFunctionService() {

    private val app: InkApplication get() = application as InkApplication
    private val repository: NotesRepository get() = app.repository

    /**
     * Creates a new note in Ink and returns it. Use this to save free-form text the user dictates,
     * such as an idea, a draft or meeting notes. For a to-do or shopping list, use createChecklist instead.
     *
     * @param title The note's title, shown as its first line. Keep it short, e.g. "Gift ideas".
     * @param body Optional text below the title. Line breaks are kept. Leave empty for a title-only note.
     * @return The note that was created.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun createNote(title: String, body: String? = null): CreatedNote {
        val content = NoteChecklists.note(title, body.orEmpty())
        if (content.isBlank) throw AppFunctionInvalidArgumentException("Provide a title or a body for the note.")
        return save(content, itemsAdded = 0)
    }

    /**
     * Creates a new checklist note in Ink: a title followed by unchecked items. Use this for
     * shopping lists, to-do lists, packing lists and the like. To add items to a list that already
     * exists, use addChecklistItems instead.
     *
     * @param title The checklist's title, e.g. "Groceries".
     * @param items The items to add, one per entry, in order, e.g. ["Milk", "Bread"]. Can be empty.
     * @return The note that was created.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun createChecklist(title: String, items: List<String>): CreatedNote {
        val content = NoteChecklists.checklist(title, items)
        if (content.title.isEmpty()) throw AppFunctionInvalidArgumentException("Provide a title for the checklist.")
        return save(content, itemsAdded = content.lines.count { it.style == LineStyle.Checklist })
    }

    /**
     * Adds unchecked items to an existing checklist note, e.g. "add eggs to my groceries list".
     * Identify the note by noteId (from createChecklist or searchNotes) or by noteTitle; if both
     * are given, noteId wins. A title matches exactly (ignoring case and accents) or, failing that,
     * as part of exactly one note's title. Items go at the end of the note's last checklist; a note
     * without a checklist gets one at its end. Fails if no note, or more than one, matches.
     *
     * @param items The items to add, one per entry, in order. Must contain at least one non-empty item.
     * @param noteId The id of the note to add to, if known.
     * @param noteTitle The title of the note to add to, e.g. "Groceries", when the id isn't known.
     * @return The note the items were added to.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun addChecklistItems(items: List<String>, noteId: String? = null, noteTitle: String? = null): CreatedNote {
        if (items.none { it.isNotBlank() }) throw AppFunctionInvalidArgumentException("Provide at least one non-empty item.")
        val id = when {
            !noteId.isNullOrBlank() -> noteId
            !noteTitle.isNullOrBlank() -> findByTitle(noteTitle).id
            else -> throw AppFunctionInvalidArgumentException("Provide noteId or noteTitle.")
        }
        val (entity, content) = repository.loadContent(id)?.takeIf { it.first.trashedAt == null }
            ?: throw AppFunctionElementNotFoundException("No note with id $id.")
        val keepCheckedLast = app.appearance.state.value.moveCheckedToBottom
        val updated = NoteChecklists.append(content, items, keepCheckedLast)
        val added = NoteChecklists.items(updated).size - NoteChecklists.items(content).size
        repository.saveContent(entity.id, entity.folderId, updated)
        app.sync.requestSync()
        return CreatedNote(entity.id, updated.title, added)
    }

    /**
     * Searches the user's notes (not including Recently Deleted) and returns the best matches,
     * pinned notes first, then the most recently edited. Matches words anywhere in a note, ignoring
     * case and accents. An empty query lists the most recent notes. Only works if the user allowed
     * assistants to read notes in Ink's settings; otherwise it fails with a permission error.
     *
     * @param query The text to look for, e.g. "passport". Empty to list recent notes.
     * @param limit The maximum number of notes to return, from 1 to 50. Defaults to 10.
     * @return The matching notes, without their full text; use getNote to read one.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun searchNotes(query: String, limit: Int? = null): NoteSearchResults {
        requireReadAccess()
        val notes = repository.notes(NoteScope.All, query).first()
        val count = (limit ?: DEFAULT_LIMIT).coerceIn(1, MAX_LIMIT)
        return NoteSearchResults(
            notes = notes.take(count).map { FoundNote(it.id, it.title, it.preview, it.isPinned, it.modifiedAt) },
            totalMatches = notes.size,
        )
    }

    /**
     * Returns a note's full text and its checklist items. Only works if the user allowed assistants
     * to read notes in Ink's settings; otherwise it fails with a permission error.
     *
     * @param noteId The note's id, from searchNotes, createNote or createChecklist.
     * @return The note's contents.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getNote(noteId: String): NoteDetails {
        requireReadAccess()
        val (entity, content) = repository.loadContent(noteId)?.takeIf { it.first.trashedAt == null }
            ?: throw AppFunctionElementNotFoundException("No note with id $noteId.")
        return NoteDetails(
            id = entity.id,
            title = content.title,
            text = content.plainText,
            checklistItems = NoteChecklists.items(content).map { ChecklistItem(it.text, it.checked) },
            isPinned = entity.isPinned,
            modifiedAt = entity.modifiedAt,
        )
    }

    private suspend fun save(content: NoteContent, itemsAdded: Int): CreatedNote {
        val id = NotesRepository.newId()
        repository.saveContent(id, folderId = null, content = content)
        app.sync.requestSync()
        return CreatedNote(id, content.title, itemsAdded)
    }

    private suspend fun findByTitle(title: String): NoteSummary {
        val notes = repository.notes(NoteScope.All, "").first()
        return when (val match = NoteMatching.byTitle(notes, title)) {
            is NoteMatching.Match.Found -> match.note
            NoteMatching.Match.None -> throw AppFunctionElementNotFoundException("No note titled \"$title\".")
            is NoteMatching.Match.Ambiguous -> throw AppFunctionInvalidArgumentException(
                // Other notes' titles are only revealed when the user lets assistants read notes.
                if (app.assistants.allowRead.value) {
                    "${match.candidates.size} notes match \"$title\": " +
                        match.candidates.take(5).joinToString { "\"${it.title}\" (id ${it.id})" } + ". Pass noteId or the exact title."
                } else {
                    "${match.candidates.size} notes match \"$title\". Pass the note's exact title."
                }
            )
        }
    }

    private fun requireReadAccess() {
        if (!app.assistants.allowRead.value) {
            throw AppFunctionPermissionRequiredException(
                "The user hasn't allowed assistants to read their notes. They can turn on " +
                    "\"Allow assistants to read notes\" in Ink's Settings. Creating notes and adding checklist items still work."
            )
        }
    }

    private companion object {
        const val DEFAULT_LIMIT = 10
        const val MAX_LIMIT = 50
    }
}

/**
 * A note that was created or changed.
 *
 * @property id The note's id, to refer to it in later calls.
 * @property title The note's title.
 * @property itemsAdded How many checklist items were added (0 for a plain note).
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class CreatedNote(val id: String, val title: String, val itemsAdded: Int)

/**
 * Notes found by a search.
 *
 * @property notes The matching notes, best first, up to the requested limit.
 * @property totalMatches How many notes matched in total, which can exceed the number returned.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class NoteSearchResults(val notes: List<FoundNote>, val totalMatches: Int)

/**
 * A note in search results.
 *
 * @property id The note's id, for getNote or addChecklistItems.
 * @property title The note's first line.
 * @property preview The note's second non-empty line, if any.
 * @property isPinned Whether the user pinned the note to the top of the list.
 * @property modifiedAt When the note was last edited, in milliseconds since the Unix epoch.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class FoundNote(val id: String, val title: String, val preview: String, val isPinned: Boolean, val modifiedAt: Long)

/**
 * A note's full contents.
 *
 * @property id The note's id.
 * @property title The note's first line.
 * @property text The whole note as plain text, title included. Table rows are tab-separated lines.
 * @property checklistItems The note's checklist items in order. Empty if it has none.
 * @property isPinned Whether the user pinned the note to the top of the list.
 * @property modifiedAt When the note was last edited, in milliseconds since the Unix epoch.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class NoteDetails(
    val id: String,
    val title: String,
    val text: String,
    val checklistItems: List<ChecklistItem>,
    val isPinned: Boolean,
    val modifiedAt: Long,
)

/**
 * One checklist item.
 *
 * @property text The item's text.
 * @property checked Whether the item is checked off.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
class ChecklistItem(val text: String, val checked: Boolean)
