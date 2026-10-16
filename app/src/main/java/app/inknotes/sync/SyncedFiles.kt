package app.inknotes.sync

import app.inknotes.data.FolderEntity
import app.inknotes.data.NoteEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * What a note or folder file in Drive contains. Kept separate from the Room entities so the
 * database can change without breaking files written by older versions of the app.
 */

@Serializable
data class SyncedFolder(
    val v: Int = FORMAT_VERSION,
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

@Serializable
data class SyncedNote(
    val v: Int = FORMAT_VERSION,
    val id: String,
    val folderId: String? = null,
    val content: String,
    val title: String,
    val preview: String,
    val searchText: String,
    val isPinned: Boolean = false,
    val createdAt: Long,
    val modifiedAt: Long,
    val updatedAt: Long,
    val trashedAt: Long? = null,
    val isDeleted: Boolean = false,
)

const val FORMAT_VERSION = 1

val syncJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun FolderEntity.toSynced() = SyncedFolder(
    id = id, name = name, createdAt = createdAt, updatedAt = updatedAt, isDeleted = isDeleted,
)

fun SyncedFolder.toEntity() = FolderEntity(
    id = id, name = name, createdAt = createdAt, updatedAt = updatedAt, isDeleted = isDeleted,
)

fun NoteEntity.toSynced() = SyncedNote(
    id = id, folderId = folderId, content = content, title = title, preview = preview,
    searchText = searchText, isPinned = isPinned, createdAt = createdAt, modifiedAt = modifiedAt,
    updatedAt = updatedAt, trashedAt = trashedAt, isDeleted = isDeleted,
)

fun SyncedNote.toEntity() = NoteEntity(
    id = id, folderId = folderId, content = content, title = title, preview = preview,
    searchText = searchText, isPinned = isPinned, createdAt = createdAt, modifiedAt = modifiedAt,
    updatedAt = updatedAt, trashedAt = trashedAt, isDeleted = isDeleted,
)
