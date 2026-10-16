package app.inknotes.sync

import app.inknotes.data.NotesDao

/** One sync pass: list Drive, plan with [SyncMerge], then move files in both directions. */
class SyncEngine(private val dao: NotesDao, private val drive: DriveClient) {

    data class Report(val downloaded: Int, val uploaded: Int)

    suspend fun sync(): Report {
        val remote = drive.listAppData().mapNotNull { SyncMerge.parse(it.id, it.name, it.appProperties) }
        val plan = SyncMerge.plan(dao.noteStamps(), dao.folderStamps(), remote)

        var downloaded = 0
        for (entry in plan.downloads) {
            val text = drive.download(entry.fileId)
            // A file whose content doesn't match its name is skipped rather than trusted.
            val applied = when (entry.kind) {
                EntityKind.FOLDER -> syncJson.decodeFromString<SyncedFolder>(text)
                    .takeIf { it.id == entry.id }?.let { dao.applyRemoteFolder(it.toEntity()) }
                EntityKind.NOTE -> syncJson.decodeFromString<SyncedNote>(text)
                    .takeIf { it.id == entry.id }?.let { dao.applyRemoteNote(it.toEntity()) }
            }
            if (applied == true) downloaded++
        }

        val uploads = plan.uploads.groupBy { it.kind }
        uploads[EntityKind.FOLDER].orEmpty().chunked(CHUNK).forEach { chunk ->
            val rows = dao.foldersByIds(chunk.map { it.id }).associateBy { it.id }
            for (upload in chunk) {
                val row = rows[upload.id] ?: continue
                put(upload, row.updatedAt, syncJson.encodeToString(row.toSynced()))
            }
        }
        uploads[EntityKind.NOTE].orEmpty().chunked(CHUNK).forEach { chunk ->
            val rows = dao.notesByIds(chunk.map { it.id }).associateBy { it.id }
            for (upload in chunk) {
                val row = rows[upload.id] ?: continue
                put(upload, row.updatedAt, syncJson.encodeToString(row.toSynced()))
            }
        }

        for (fileId in plan.duplicates) drive.delete(fileId)
        return Report(downloaded, plan.uploads.size)
    }

    private suspend fun put(upload: Upload, updatedAt: Long, json: String) {
        val properties = mapOf(SyncMerge.UPDATED_AT_PROPERTY to updatedAt.toString())
        if (upload.fileId == null) {
            drive.create(SyncMerge.fileName(upload.kind, upload.id), properties, json)
        } else {
            drive.update(upload.fileId, properties, json)
        }
    }

    private companion object {
        /** Stays under SQLite's bound-parameter limit. */
        const val CHUNK = 500
    }
}
