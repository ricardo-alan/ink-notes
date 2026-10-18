package app.inknotes

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.TileService

/** "New note" in Quick Settings: opens the editor and closes the shade. */
class QuickNoteTileService : TileService() {

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun(::openNewNote) else openNewNote()
    }

    private fun openNewNote() {
        val intent = InkIntents.newNote(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
