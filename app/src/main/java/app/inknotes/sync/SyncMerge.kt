package app.inknotes.sync

import app.inknotes.data.SyncStamp

/*
 * Pure merge logic, kept free of Android and network code so it can be unit tested.
 *
 * Every note and folder is one file in Drive's appDataFolder. The file's appProperties carry the
 * row's updatedAt, so a sync only needs the file list to decide what to move: whichever side has
 * the newer updatedAt wins (last writer wins). Tombstones (isDeleted) are ordinary rows, so
 * deletions travel the same way.
 */

enum class EntityKind(val prefix: String) { NOTE("note-"), FOLDER("folder-") }

/** A note or folder file as listed in Drive. */
data class RemoteEntry(
    val fileId: String,
    val kind: EntityKind,
    val id: String,
    val updatedAt: Long,
)

/** Sends a local row to Drive; [fileId] is null when Drive has no file for it yet. */
data class Upload(val kind: EntityKind, val id: String, val fileId: String?)

data class SyncPlan(
    val downloads: List<RemoteEntry>,
    val uploads: List<Upload>,
    /** Extra files for an id that already has a newer file; safe to delete. */
    val duplicates: List<String>,
) {
    val isEmpty get() = downloads.isEmpty() && uploads.isEmpty() && duplicates.isEmpty()
}

object SyncMerge {

    const val UPDATED_AT_PROPERTY = "updatedAt"
    private const val SUFFIX = ".json"

    fun fileName(kind: EntityKind, id: String) = kind.prefix + id + SUFFIX

    /** Reads a Drive file back into an entry, or null if it isn't one of Ink's files. */
    fun parse(fileId: String, name: String, appProperties: Map<String, String>): RemoteEntry? {
        if (!name.endsWith(SUFFIX)) return null
        val kind = EntityKind.entries.firstOrNull { name.startsWith(it.prefix) } ?: return null
        val id = name.substring(kind.prefix.length, name.length - SUFFIX.length)
        if (id.isEmpty()) return null
        // A file without a readable stamp is still downloaded when we have nothing local,
        // and is overwritten by any local version.
        val updatedAt = appProperties[UPDATED_AT_PROPERTY]?.toLongOrNull() ?: 0L
        return RemoteEntry(fileId, kind, id, updatedAt)
    }

    fun plan(
        localNotes: List<SyncStamp>,
        localFolders: List<SyncStamp>,
        remote: List<RemoteEntry>,
    ): SyncPlan {
        val downloads = mutableListOf<RemoteEntry>()
        val uploads = mutableListOf<Upload>()
        val duplicates = mutableListOf<String>()
        // Folders first, so notes downloaded in the same pass find their folder.
        for ((kind, local) in listOf(EntityKind.FOLDER to localFolders, EntityKind.NOTE to localNotes)) {
            val remoteById = remote.filter { it.kind == kind }.groupBy { it.id }.mapValues { (_, files) ->
                val sorted = files.sortedWith(compareByDescending<RemoteEntry> { it.updatedAt }.thenBy { it.fileId })
                sorted.drop(1).mapTo(duplicates) { it.fileId }
                sorted.first()
            }
            val localById = local.associateBy { it.id }
            for (id in localById.keys + remoteById.keys) {
                val l = localById[id]
                val r = remoteById[id]
                when {
                    l == null && r != null -> downloads += r
                    l != null && r == null -> uploads += Upload(kind, id, fileId = null)
                    l != null && r != null && l.updatedAt > r.updatedAt -> uploads += Upload(kind, id, r.fileId)
                    l != null && r != null && r.updatedAt > l.updatedAt -> downloads += r
                }
            }
        }
        return SyncPlan(downloads, uploads, duplicates)
    }
}
