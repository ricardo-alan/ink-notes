package app.inknotes.data

import app.inknotes.model.LineFormat
import app.inknotes.model.LineStyle
import app.inknotes.model.NoteContent
import app.inknotes.model.NoteTables
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Which notes a list shows. */
sealed interface NoteScope {
    data object All : NoteScope
    data object Unfiled : NoteScope
    data object Trash : NoteScope
    data class Folder(val id: String) : NoteScope
}

class NotesRepository(
    private val dao: NotesDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    val folders: Flow<List<FolderWithCount>> = dao.observeFolders()
    val allCount: Flow<Int> = dao.observeAllCount()
    val unfiledCount: Flow<Int> = dao.observeUnfiledCount()
    val trashCount: Flow<Int> = dao.observeTrashCount()

    fun observeFolder(id: String): Flow<FolderEntity?> = dao.observeFolder(id)

    fun notes(scope: NoteScope, query: String): Flow<List<NoteSummary>> {
        val q = escapeLike(normalizeForSearch(query.trim()))
        return when (scope) {
            NoteScope.All -> dao.observeAll(q)
            NoteScope.Unfiled -> dao.observeInFolder(null, q)
            NoteScope.Trash -> dao.observeTrash(q)
            is NoteScope.Folder -> dao.observeInFolder(scope.id, q)
        }
    }

    suspend fun loadContent(id: String): Pair<NoteEntity, NoteContent>? {
        val note = dao.getNote(id) ?: return null
        return note to decode(note)
    }

    fun decode(note: NoteEntity): NoteContent =
        runCatching { NoteTables.normalize(json.decodeFromString<NoteContent>(note.content)) }.getOrDefault(NoteContent.Empty)

    /** A note's row as stored (tombstones included), updated on every write by anyone. */
    fun observeNoteRow(id: String): Flow<NoteEntity?> = dao.observeNoteRow(id)

    suspend fun noteRow(id: String): NoteEntity? = dao.getNoteRow(id)

    /** Creates or updates a note's content. Returns the stored entity. */
    suspend fun saveContent(id: String, folderId: String?, content: NoteContent): NoteEntity {
        val entity = buildEntity(id, folderId, content, dao.getNote(id), clock())
        dao.upsertNote(entity)
        return entity
    }

    /**
     * Like [saveContent], but only if nobody wrote the note since the caller saw it with
     * [expectedUpdatedAt] (null = it didn't exist). Returns null when someone did, so the caller can
     * reconcile first. A deleted note (tombstone) is brought back.
     */
    suspend fun saveContentIfUnchanged(id: String, folderId: String?, content: NoteContent, expectedUpdatedAt: Long?): NoteEntity? =
        dao.upsertNoteIfUnchanged(id, expectedUpdatedAt) { row ->
            // Strictly newer than what it replaces, so every write is a distinct version.
            val now = maxOf(clock(), (row?.updatedAt ?: 0L) + 1)
            buildEntity(id, folderId, content, row?.takeUnless { it.isDeleted }, now)
        }

    private fun buildEntity(id: String, folderId: String?, content: NoteContent, existing: NoteEntity?, now: Long) = NoteEntity(
        id = id,
        folderId = existing?.folderId ?: folderId,
        content = json.encodeToString(content.copy(version = NoteContent.CURRENT_VERSION)),
        title = content.title,
        preview = content.preview,
        searchText = normalizeForSearch(content.plainText),
        isPinned = existing?.isPinned ?: false,
        createdAt = existing?.createdAt ?: now,
        modifiedAt = now,
        updatedAt = now,
        trashedAt = existing?.trashedAt,
    )

    /** Creates a note from text shared by another app: first line becomes the title. Returns its id. */
    suspend fun createFromText(text: String): String {
        val id = newId()
        val lineCount = text.count { it == '\n' } + 1
        val lines = List(lineCount) { if (it == 0) LineFormat(LineStyle.Title) else LineFormat(LineStyle.Body) }
        saveContent(id, folderId = null, content = NoteContent(text = text, lines = lines))
        return id
    }

    suspend fun setPinned(id: String, pinned: Boolean) = dao.setPinned(id, pinned, clock())
    suspend fun moveToFolder(id: String, folderId: String?) = dao.moveToFolder(id, folderId, clock())
    suspend fun moveToTrash(id: String) = dao.moveToTrash(id, clock())
    suspend fun restore(id: String) = dao.restore(id, clock())
    suspend fun deleteForever(id: String) = dao.purge(id, clock())

    suspend fun purgeExpiredTrash() {
        val now = clock()
        dao.purgeTrashedBefore(now - TimeUnit.DAYS.toMillis(TRASH_RETENTION_DAYS), now)
    }

    suspend fun createFolder(name: String): String {
        val now = clock()
        val id = newId()
        dao.upsertFolder(FolderEntity(id = id, name = name.trim(), createdAt = now, updatedAt = now))
        return id
    }

    suspend fun renameFolder(id: String, name: String) {
        val folder = dao.getFolder(id) ?: return
        dao.upsertFolder(folder.copy(name = name.trim(), updatedAt = clock()))
    }

    /** Like Apple Notes: deleting a folder sends its notes to Recently Deleted. */
    suspend fun deleteFolder(id: String) {
        val now = clock()
        dao.trashFolderNotes(id, now)
        dao.deleteFolder(id, now)
    }

    companion object {
        const val TRASH_RETENTION_DAYS = 30L

        fun newId(): String = UUID.randomUUID().toString()

        fun normalizeForSearch(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase(Locale.ROOT)

        private fun escapeLike(text: String): String =
            text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}
