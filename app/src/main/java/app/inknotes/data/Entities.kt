package app.inknotes.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Sync-ready schema:
 * - ids are UUIDs, so devices can create rows without coordinating.
 * - updatedAt changes on every modification (last-writer-wins when syncing).
 * - deletions are tombstones (isDeleted) so they can propagate to other devices.
 */

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

@Entity(
    tableName = "notes",
    indices = [Index("folderId"), Index("modifiedAt")],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    /** null = the default "Notes" folder. */
    val folderId: String?,
    /** Serialized [app.inknotes.model.NoteContent]. */
    val content: String,
    val title: String,
    val preview: String,
    /** Lowercased, accent-free text used for search. */
    val searchText: String,
    val isPinned: Boolean = false,
    val createdAt: Long,
    /** Last time the text changed; what the user sees and sorts by. */
    val modifiedAt: Long,
    /** Last time anything changed (pin, move, trash...); used for sync. */
    val updatedAt: Long,
    /** Set while the note is in Recently Deleted. */
    val trashedAt: Long? = null,
    val isDeleted: Boolean = false,
)

data class NoteSummary(
    val id: String,
    val folderId: String?,
    val title: String,
    val preview: String,
    /** Serialized note, for the gallery view's multi-line preview. */
    val content: String,
    val isPinned: Boolean,
    val modifiedAt: Long,
    val trashedAt: Long?,
)

data class FolderWithCount(
    @Embedded val folder: FolderEntity,
    val noteCount: Int,
)

/** A row's identity and version, enough to decide what needs syncing. */
data class SyncStamp(val id: String, val updatedAt: Long)
