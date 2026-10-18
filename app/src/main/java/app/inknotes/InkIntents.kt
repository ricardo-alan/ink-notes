package app.inknotes

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/** Entry points from outside the app: launcher shortcuts, the Quick Settings tile, the widget and sharing. */
object InkIntents {
    const val ACTION_NEW_NOTE = "app.inknotes.action.NEW_NOTE"
    const val ACTION_NEW_CHECKLIST = "app.inknotes.action.NEW_CHECKLIST"
    const val ACTION_OPEN_NOTE = "app.inknotes.action.OPEN_NOTE"
    private const val EXTRA_NOTE_ID = "noteId"

    fun newNote(context: Context): Intent = launch(context, ACTION_NEW_NOTE)

    fun newChecklist(context: Context): Intent = launch(context, ACTION_NEW_CHECKLIST)

    /**
     * Long-press launcher shortcuts. Published from code rather than res/xml/shortcuts.xml,
     * which can't name the debug build's package (app.inknotes.debug).
     */
    fun publishShortcuts(context: Context) {
        val shortcuts = listOf(
            shortcut(context, "new_note", R.string.new_note, R.drawable.ic_shortcut_note, newNote(context)),
            shortcut(context, "new_checklist", R.string.new_checklist, R.drawable.ic_shortcut_checklist, newChecklist(context)),
        )
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    private fun shortcut(context: Context, id: String, label: Int, icon: Int, intent: Intent) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(context.getString(label))
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(intent)
            .build()

    fun openNote(context: Context, noteId: String): Intent =
        launch(context, ACTION_OPEN_NOTE)
            .setData(Uri.parse("ink://note/$noteId")) // distinct per note, so widget rows don't share one PendingIntent
            .putExtra(EXTRA_NOTE_ID, noteId)

    private fun launch(context: Context, action: String) =
        Intent(context, MainActivity::class.java)
            .setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    /** What an incoming intent asks the app to do, if anything. */
    sealed interface Request {
        data object NewNote : Request
        data object NewChecklist : Request
        data class OpenNote(val noteId: String) : Request
        data class SharedText(val text: String) : Request
    }

    fun parse(intent: Intent?): Request? = when (intent?.action) {
        ACTION_NEW_NOTE -> Request.NewNote
        ACTION_NEW_CHECKLIST -> Request.NewChecklist
        ACTION_OPEN_NOTE -> intent.getStringExtra(EXTRA_NOTE_ID)?.let { Request.OpenNote(it) }
        Intent.ACTION_SEND -> sharedText(intent)?.let { Request.SharedText(it) }
        else -> null
    }

    /** Browsers share the page title as the subject and the URL as the text: keep both. */
    private fun sharedText(intent: Intent): String? {
        if (intent.type?.startsWith("text/") != true) return null
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        val combined = listOf(subject, text).filter { it.isNotEmpty() }.distinct().joinToString("\n")
        return combined.ifEmpty { null }
    }
}
