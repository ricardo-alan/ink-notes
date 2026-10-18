package app.inknotes

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.inknotes.data.InkDatabase
import app.inknotes.data.NoteScope
import app.inknotes.data.NotesRepository
import app.inknotes.settings.AppearanceSettings
import app.inknotes.sync.DriveSync
import app.inknotes.widget.InkWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class InkApplication : Application() {

    /** Outlives screens, so pending saves finish even after the user navigates away. */
    val appScope = CoroutineScope(SupervisorJob())

    private val database by lazy { InkDatabase.create(this) }

    val repository: NotesRepository by lazy { NotesRepository(database.notesDao()) }

    val sync: DriveSync by lazy { DriveSync(this, database.notesDao()) }

    val appearance: AppearanceSettings by lazy { AppearanceSettings(this) }

    /**
     * Where to navigate next, from a shortcut, tile, widget or share. Lives here rather than in
     * the activity so it survives the activity being recreated while it is pending.
     */
    val pendingRequest = MutableStateFlow<InkIntents.Request?>(null)

    /** Turns an incoming request into navigation; shared text is saved first, outside any screen. */
    fun submit(request: InkIntents.Request) {
        if (request is InkIntents.Request.SharedText) {
            appScope.launch { pendingRequest.value = InkIntents.Request.OpenNote(repository.createFromText(request.text)) }
        } else {
            pendingRequest.value = request
        }
    }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        appScope.launch { repository.purgeExpiredTrash() }
        InkIntents.publishShortcuts(this)
        // Keep the home screen widget in step with the notes (cheap when no widget is placed).
        appScope.launch {
            repository.notes(NoteScope.All, "")
                .map { notes -> notes.take(InkWidget.MAX_NOTES).map { listOf(it.id, it.title, it.preview, it.isPinned) } }
                .distinctUntilChanged()
                .debounce(500)
                .collect { InkWidget.refresh(this@InkApplication) }
        }
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
