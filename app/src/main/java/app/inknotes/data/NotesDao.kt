package app.inknotes.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

private const val LIVE = "isDeleted = 0 AND trashedAt IS NULL"
private const val SUMMARY = "id, folderId, title, preview, content, isPinned, modifiedAt, trashedAt"
private const val SEARCH = "(:query = '' OR searchText LIKE '%' || :query || '%' ESCAPE '\\')"

@Dao
interface NotesDao {

    // Folders

    @Query(
        """
        SELECT f.*, (SELECT COUNT(*) FROM notes n WHERE n.folderId = f.id AND n.isDeleted = 0 AND n.trashedAt IS NULL) AS noteCount
        FROM folders f WHERE f.isDeleted = 0 ORDER BY f.name COLLATE NOCASE
        """
    )
    fun observeFolders(): Flow<List<FolderWithCount>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolder(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE id = :id")
    fun observeFolder(id: String): Flow<FolderEntity?>

    @Upsert
    suspend fun upsertFolder(folder: FolderEntity)

    @Query("UPDATE folders SET isDeleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun deleteFolder(id: String, now: Long)

    // Counts

    @Query("SELECT COUNT(*) FROM notes WHERE $LIVE")
    fun observeAllCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE $LIVE AND folderId IS NULL")
    fun observeUnfiledCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 0 AND trashedAt IS NOT NULL")
    fun observeTrashCount(): Flow<Int>

    // Notes

    @Query("SELECT $SUMMARY FROM notes WHERE $LIVE AND $SEARCH ORDER BY isPinned DESC, modifiedAt DESC")
    fun observeAll(query: String): Flow<List<NoteSummary>>

    @Query("SELECT $SUMMARY FROM notes WHERE $LIVE AND folderId IS :folderId AND $SEARCH ORDER BY isPinned DESC, modifiedAt DESC")
    fun observeInFolder(folderId: String?, query: String): Flow<List<NoteSummary>>

    @Query("SELECT $SUMMARY FROM notes WHERE isDeleted = 0 AND trashedAt IS NOT NULL AND $SEARCH ORDER BY trashedAt DESC")
    fun observeTrash(query: String): Flow<List<NoteSummary>>

    @Query("SELECT * FROM notes WHERE id = :id AND isDeleted = 0")
    suspend fun getNote(id: String): NoteEntity?

    @Upsert
    suspend fun upsertNote(note: NoteEntity)

    @Query("UPDATE notes SET isPinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, now: Long)

    @Query("UPDATE notes SET folderId = :folderId, updatedAt = :now WHERE id = :id")
    suspend fun moveToFolder(id: String, folderId: String?, now: Long)

    @Query("UPDATE notes SET trashedAt = :now, isPinned = 0, updatedAt = :now WHERE id = :id")
    suspend fun moveToTrash(id: String, now: Long)

    @Query("UPDATE notes SET trashedAt = :now, isPinned = 0, updatedAt = :now WHERE folderId = :folderId AND trashedAt IS NULL")
    suspend fun trashFolderNotes(folderId: String, now: Long)

    @Query("UPDATE notes SET trashedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: String, now: Long)

    /** Turns a note into a tombstone: content is dropped, the row stays so deletion can sync. */
    @Query(
        """
        UPDATE notes SET isDeleted = 1, content = '', title = '', preview = '', searchText = '', updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun purge(id: String, now: Long)

    @Query(
        """
        UPDATE notes SET isDeleted = 1, content = '', title = '', preview = '', searchText = '', updatedAt = :now
        WHERE isDeleted = 0 AND trashedAt IS NOT NULL AND trashedAt < :cutoff
        """
    )
    suspend fun purgeTrashedBefore(cutoff: Long, now: Long)

    // Sync: these see every row, tombstones included.

    @Query("SELECT id, updatedAt FROM notes")
    suspend fun noteStamps(): List<SyncStamp>

    @Query("SELECT id, updatedAt FROM folders")
    suspend fun folderStamps(): List<SyncStamp>

    @Query("SELECT * FROM notes WHERE id IN (:ids)")
    suspend fun notesByIds(ids: List<String>): List<NoteEntity>

    @Query("SELECT * FROM folders WHERE id IN (:ids)")
    suspend fun foldersByIds(ids: List<String>): List<FolderEntity>

    @Query("SELECT updatedAt FROM notes WHERE id = :id")
    suspend fun noteUpdatedAt(id: String): Long?

    @Query("SELECT updatedAt FROM folders WHERE id = :id")
    suspend fun folderUpdatedAt(id: String): Long?

    /** Stores a row from another device unless a newer local edit landed while it was downloading. */
    @Transaction
    suspend fun applyRemoteNote(note: NoteEntity): Boolean {
        val local = noteUpdatedAt(note.id)
        if (local != null && local >= note.updatedAt) return false
        upsertNote(note)
        return true
    }

    @Transaction
    suspend fun applyRemoteFolder(folder: FolderEntity): Boolean {
        val local = folderUpdatedAt(folder.id)
        if (local != null && local >= folder.updatedAt) return false
        upsertFolder(folder)
        return true
    }
}
