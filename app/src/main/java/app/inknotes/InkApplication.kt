package app.inknotes

import android.app.Application
import app.inknotes.data.InkDatabase
import app.inknotes.data.NotesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class InkApplication : Application() {

    /** Outlives screens, so pending saves finish even after the user navigates away. */
    val appScope = CoroutineScope(SupervisorJob())

    val repository: NotesRepository by lazy { NotesRepository(InkDatabase.create(this).notesDao()) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { repository.purgeExpiredTrash() }
    }
}
