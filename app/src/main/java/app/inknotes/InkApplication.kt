package app.inknotes

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.inknotes.data.InkDatabase
import app.inknotes.data.NotesRepository
import app.inknotes.settings.AppearanceSettings
import app.inknotes.sync.DriveSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class InkApplication : Application() {

    /** Outlives screens, so pending saves finish even after the user navigates away. */
    val appScope = CoroutineScope(SupervisorJob())

    private val database by lazy { InkDatabase.create(this) }

    val repository: NotesRepository by lazy { NotesRepository(database.notesDao()) }

    val sync: DriveSync by lazy { DriveSync(this, database.notesDao()) }

    val appearance: AppearanceSettings by lazy { AppearanceSettings(this) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { repository.purgeExpiredTrash() }
        // Sync (when turned on) as the app comes to the foreground and again as it leaves it.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                sync.schedulePeriodic()
                sync.requestSync()
            }

            override fun onStop(owner: LifecycleOwner) = sync.requestSync()
        })
    }
}
