package app.inknotes.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.inknotes.InkApplication
import app.inknotes.data.NoteScope
import app.inknotes.model.NoteChecklists
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Debug builds only: changes a note the way another writer would (straight through the
 * repository, not the editor), to test how an open editor copes. For when App Functions can't be
 * called (see docs/appfunctions.md). The note is found by its exact title.
 *
 * Add checklist items, like an assistant:
 *   adb shell am broadcast -n app.inknotes.debug/app.inknotes.debug.ExternalEditReceiver -a add --es title "Groceries" --es items "Eggs,Jam"
 * Rewrite one line (an edit the editor can't merge):
 *   adb shell am broadcast -n app.inknotes.debug/app.inknotes.debug.ExternalEditReceiver -a replace --es title "Groceries" --es from "Milk" --es to "Oat milk"
 */
class ExternalEditReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as InkApplication
        val title = intent.getStringExtra("title") ?: return
        val pending = goAsync()
        app.appScope.launch {
            try {
                val repository = app.repository
                val summary = repository.notes(NoteScope.All, "").first().firstOrNull { it.title == title }
                val (entity, content) = summary?.let { repository.loadContent(it.id) } ?: run {
                    Log.w(TAG, "No note titled \"$title\"")
                    return@launch
                }
                val updated = when (intent.action) {
                    "add" -> NoteChecklists.append(
                        content,
                        intent.getStringExtra("items").orEmpty().split(','),
                        app.appearance.state.value.moveCheckedToBottom,
                    )
                    "replace" -> content.copy(
                        text = content.text.split('\n')
                            .map { if (it == intent.getStringExtra("from")) intent.getStringExtra("to").orEmpty() else it }
                            .joinToString("\n"),
                        spans = emptyList(),
                    )
                    else -> return@launch
                }
                repository.saveContent(entity.id, entity.folderId, updated)
                Log.i(TAG, "${intent.action}: \"$title\" is now ${updated.text.replace("\n", " | ")}")
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "ExternalEdit"
    }
}
