package app.inknotes.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.inknotes.InkApplication
import app.inknotes.InkIntents
import app.inknotes.R
import app.inknotes.data.NoteScope
import app.inknotes.data.NoteSummary
import kotlinx.coroutines.flow.first

/** Home screen widget: pinned notes first, then the most recent ones, plus a "new note" button. */
class InkWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as InkApplication).repository
        val notes = repository.notes(NoteScope.All, "").first().take(MAX_NOTES)
        provideContent {
            GlanceTheme { WidgetContent(context, notes) }
        }
    }

    companion object {
        const val MAX_NOTES = 20

        suspend fun refresh(context: Context) = InkWidget().updateAll(context)
    }
}

class InkWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = InkWidget()
}

@Composable
private fun WidgetContent(context: Context, notes: List<NoteSummary>) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth().padding(start = 8.dp, bottom = 8.dp),
        ) {
            Text(
                text = context.getString(R.string.folder_notes),
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight(),
            )
            CircleIconButton(
                imageProvider = ImageProvider(R.drawable.ic_edit_note),
                contentDescription = context.getString(R.string.new_note),
                onClick = actionStartActivity(InkIntents.newNote(context)),
            )
        }
        if (notes.isEmpty()) {
            Box(contentAlignment = Alignment.Center, modifier = GlanceModifier.fillMaxSize()) {
                Text(
                    text = context.getString(R.string.no_notes),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                )
            }
        } else {
            LazyColumn {
                items(notes, itemId = { it.id.hashCode().toLong() }) { note ->
                    Column {
                        NoteRow(context, note)
                        Spacer(GlanceModifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteRow(context: Context, note: NoteSummary) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(16.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(actionStartActivity(InkIntents.openNote(context, note.id))),
    ) {
        Text(
            text = (if (note.isPinned) "📌 " else "") + note.title.ifEmpty { context.getString(R.string.untitled) },
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp),
            maxLines = 1,
        )
        if (note.preview.isNotEmpty()) {
            Text(
                text = note.preview,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
        }
    }
}
