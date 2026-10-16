package app.inknotes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FolderEntity::class, NoteEntity::class], version = 1)
abstract class InkDatabase : RoomDatabase() {
    abstract fun notesDao(): NotesDao

    companion object {
        fun create(context: Context): InkDatabase =
            Room.databaseBuilder(context, InkDatabase::class.java, "ink.db").build()
    }
}
