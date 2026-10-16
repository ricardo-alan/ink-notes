package app.inknotes.sync

import app.inknotes.data.NoteEntity
import app.inknotes.data.SyncStamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeTest {

    private fun stamp(id: String, at: Long) = SyncStamp(id, at)
    private fun note(id: String, at: Long, fileId: String = "f-$id") = RemoteEntry(fileId, EntityKind.NOTE, id, at)
    private fun folder(id: String, at: Long, fileId: String = "f-$id") = RemoteEntry(fileId, EntityKind.FOLDER, id, at)

    @Test
    fun `nothing on either side plans nothing`() {
        assertTrue(SyncMerge.plan(emptyList(), emptyList(), emptyList()).isEmpty)
    }

    @Test
    fun `local only rows are created in Drive`() {
        val plan = SyncMerge.plan(listOf(stamp("n1", 10)), listOf(stamp("d1", 5)), emptyList())
        assertEquals(
            listOf(Upload(EntityKind.FOLDER, "d1", null), Upload(EntityKind.NOTE, "n1", null)),
            plan.uploads,
        )
        assertTrue(plan.downloads.isEmpty())
    }

    @Test
    fun `remote only rows are downloaded, folders before notes`() {
        val remote = listOf(note("n1", 10), folder("d1", 5))
        val plan = SyncMerge.plan(emptyList(), emptyList(), remote)
        assertEquals(listOf(folder("d1", 5), note("n1", 10)), plan.downloads)
        assertTrue(plan.uploads.isEmpty())
    }

    @Test
    fun `newer local edit wins and updates the existing file`() {
        val plan = SyncMerge.plan(listOf(stamp("n1", 20)), emptyList(), listOf(note("n1", 10, fileId = "abc")))
        assertEquals(listOf(Upload(EntityKind.NOTE, "n1", "abc")), plan.uploads)
        assertTrue(plan.downloads.isEmpty())
    }

    @Test
    fun `newer remote edit wins`() {
        val plan = SyncMerge.plan(listOf(stamp("n1", 10)), emptyList(), listOf(note("n1", 20)))
        assertEquals(listOf(note("n1", 20)), plan.downloads)
        assertTrue(plan.uploads.isEmpty())
    }

    @Test
    fun `equal stamps mean already in sync`() {
        val plan = SyncMerge.plan(listOf(stamp("n1", 10)), listOf(stamp("d1", 3)), listOf(note("n1", 10), folder("d1", 3)))
        assertTrue(plan.isEmpty)
    }

    @Test
    fun `notes and folders with the same id are independent`() {
        val plan = SyncMerge.plan(listOf(stamp("x", 10)), emptyList(), listOf(folder("x", 10)))
        assertEquals(listOf(folder("x", 10)), plan.downloads)
        assertEquals(listOf(Upload(EntityKind.NOTE, "x", null)), plan.uploads)
    }

    @Test
    fun `duplicate files keep the newest and delete the rest`() {
        val remote = listOf(note("n1", 10, fileId = "old"), note("n1", 30, fileId = "new"), note("n1", 20, fileId = "mid"))
        val plan = SyncMerge.plan(listOf(stamp("n1", 25)), emptyList(), remote)
        assertEquals(listOf(note("n1", 30, fileId = "new")), plan.downloads)
        assertEquals(setOf("old", "mid"), plan.duplicates.toSet())
    }

    @Test
    fun `duplicates with equal stamps pick a stable winner`() {
        val remote = listOf(note("n1", 10, fileId = "b"), note("n1", 10, fileId = "a"))
        val plan = SyncMerge.plan(emptyList(), emptyList(), remote)
        assertEquals("a", plan.downloads.single().fileId)
        assertEquals(listOf("b"), plan.duplicates)
    }

    @Test
    fun `a local deletion after the remote edit is pushed`() {
        // The tombstone is just a newer row, so it overwrites the remote file.
        val plan = SyncMerge.plan(listOf(stamp("n1", 50)), emptyList(), listOf(note("n1", 40)))
        assertEquals(listOf(Upload(EntityKind.NOTE, "n1", "f-n1")), plan.uploads)
    }

    @Test
    fun `an edit made after a remote deletion survives`() {
        // Remote tombstone at 40, local edit at 60: the edit wins and resurrects the note remotely.
        val plan = SyncMerge.plan(listOf(stamp("n1", 60)), emptyList(), listOf(note("n1", 40)))
        assertEquals(1, plan.uploads.size)
        assertTrue(plan.downloads.isEmpty())
    }

    @Test
    fun `parse reads Ink file names and stamps`() {
        assertEquals(
            RemoteEntry("fid", EntityKind.NOTE, "1234-abcd", 99),
            SyncMerge.parse("fid", "note-1234-abcd.json", mapOf("updatedAt" to "99")),
        )
        assertEquals(
            RemoteEntry("fid", EntityKind.FOLDER, "f", 0),
            SyncMerge.parse("fid", "folder-f.json", emptyMap()),
        )
        assertNull(SyncMerge.parse("fid", "other.json", emptyMap()))
        assertNull(SyncMerge.parse("fid", "note-.json", emptyMap()))
        assertNull(SyncMerge.parse("fid", "note-1.txt", emptyMap()))
    }

    @Test
    fun `file names round trip through parse`() {
        val name = SyncMerge.fileName(EntityKind.FOLDER, "abc")
        assertEquals("abc", SyncMerge.parse("x", name, emptyMap())?.id)
    }

    @Test
    fun `note files round trip and tolerate unknown fields`() {
        val entity = NoteEntity(
            id = "n1", folderId = null, content = "{}", title = "T", preview = "P", searchText = "t p",
            isPinned = true, createdAt = 1, modifiedAt = 2, updatedAt = 3, trashedAt = 4, isDeleted = false,
        )
        val text = syncJson.encodeToString(entity.toSynced())
        assertEquals(entity, syncJson.decodeFromString<SyncedNote>(text).toEntity())
        val future = text.dropLast(1) + ",\"color\":\"red\"}"
        assertEquals(entity, syncJson.decodeFromString<SyncedNote>(future).toEntity())
    }
}
