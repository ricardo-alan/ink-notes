package app.inknotes.ui.notes

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.inknotes.R
import app.inknotes.data.NoteScope
import app.inknotes.data.NoteSummary
import app.inknotes.data.NotesRepository
import app.inknotes.ui.common.GroupedSection
import app.inknotes.ui.common.RowDivider
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun NotesScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onNewNote: () -> Unit,
    viewModel: NotesViewModel = viewModel(factory = NotesViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val scope = viewModel.scope
    val isTrash = scope == NoteScope.Trash
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    val coroutines = rememberCoroutineScope()
    var moving by remember { mutableStateOf<NoteSummary?>(null) }
    var deletingForever by remember { mutableStateOf<NoteSummary?>(null) }

    val title = when (scope) {
        NoteScope.All -> stringResource(R.string.folder_all_notes)
        NoteScope.Unfiled -> stringResource(R.string.folder_notes)
        NoteScope.Trash -> stringResource(R.string.folder_recently_deleted)
        is NoteScope.Folder -> state.folderName.orEmpty()
    }
    val deletedMessage = stringResource(R.string.note_deleted)
    val undoLabel = stringResource(R.string.undo)

    val actions = NoteActions(
        onPin = { viewModel.setPinned(it.id, !it.isPinned) },
        onMove = { moving = it },
        onDelete = { note ->
            viewModel.moveToTrash(note.id)
            coroutines.launch {
                val result = snackbar.showSnackbar(deletedMessage, actionLabel = undoLabel, withDismissAction = true)
                if (result == SnackbarResult.ActionPerformed) viewModel.restore(note.id)
            }
        },
        onRestore = { viewModel.restore(it.id) },
        onDeleteForever = { deletingForever = it },
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = {
                    if (state.loaded) Text(pluralStringResource(R.plurals.note_count, state.notes.size, state.notes.size))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (!isTrash) {
                MediumFloatingActionButton(onClick = onNewNote) {
                    Icon(Icons.Outlined.EditNote, contentDescription = stringResource(R.string.new_note), modifier = Modifier.size(28.dp))
                }
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item { SearchField(query, onQueryChange = { viewModel.query.value = it }) }

            if (isTrash) {
                item {
                    Text(
                        stringResource(R.string.trash_info, NotesRepository.TRASH_RETENTION_DAYS.toInt()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
                    )
                }
            }

            if (state.loaded && state.notes.isEmpty()) {
                item {
                    val message = when {
                        query.isNotBlank() -> R.string.no_results
                        isTrash -> R.string.trash_empty
                        else -> R.string.no_notes
                    }
                    Text(
                        stringResource(message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            val pinned = state.notes.filter { it.isPinned && !isTrash }
            if (pinned.isNotEmpty()) {
                item(key = "pinned") {
                    NoteSection(stringResource(R.string.section_pinned), pinned, isTrash, locale, onOpenNote, actions)
                }
            }
            val rest = state.notes.filter { !it.isPinned || isTrash }
            rest.groupBy { DateGrouping.sectionFor(if (isTrash) it.trashedAt ?: it.modifiedAt else it.modifiedAt) }
                .forEach { (section, notes) ->
                    item(key = section.toString()) {
                        val header = when (section) {
                            is DateSection.Relative -> stringResource(section.label)
                            is DateSection.Month -> DateGrouping.monthLabel(section, locale)
                        }
                        NoteSection(header, notes, isTrash, locale, onOpenNote, actions)
                    }
                }
            item { Spacer(Modifier.height(120.dp)) }
        }
    }

    moving?.let { note ->
        MoveToFolderDialog(
            folders = state.folders.map { it.folder.id to it.folder.name },
            currentFolderId = note.folderId,
            onDismiss = { moving = null },
            onSelect = { folderId -> viewModel.moveToFolder(note.id, folderId); moving = null },
        )
    }
    deletingForever?.let { note ->
        AlertDialog(
            onDismissRequest = { deletingForever = null },
            title = { Text(stringResource(R.string.delete_forever_confirm_title)) },
            text = { Text(stringResource(R.string.delete_forever_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteForever(note.id); deletingForever = null }) {
                    Text(stringResource(R.string.action_delete_forever), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deletingForever = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private class NoteActions(
    val onPin: (NoteSummary) -> Unit,
    val onMove: (NoteSummary) -> Unit,
    val onDelete: (NoteSummary) -> Unit,
    val onRestore: (NoteSummary) -> Unit,
    val onDeleteForever: (NoteSummary) -> Unit,
)

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                if (query.isEmpty()) {
                    Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.clear_search))
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun NoteSection(
    header: String,
    notes: List<NoteSummary>,
    isTrash: Boolean,
    locale: Locale,
    onOpenNote: (String) -> Unit,
    actions: NoteActions,
) {
    GroupedSection(header = header) {
        notes.forEachIndexed { index, note ->
            if (index > 0) RowDivider()
            NoteRow(note, isTrash, locale, onOpenNote, actions)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(
    note: NoteSummary,
    isTrash: Boolean,
    locale: Locale,
    onOpenNote: (String) -> Unit,
    actions: NoteActions,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { if (isTrash) menu = true else onOpenNote(note.id) },
                    onLongClick = { menu = true },
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                note.title.ifEmpty { stringResource(R.string.untitled) },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row {
                Text(
                    DateGrouping.rowDate(note.modifiedAt, locale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    note.preview.ifEmpty { stringResource(R.string.no_additional_text) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (isTrash) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_restore)) },
                    leadingIcon = { Icon(Icons.Outlined.RestoreFromTrash, null) },
                    onClick = { menu = false; actions.onRestore(note) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete_forever)) },
                    leadingIcon = { Icon(Icons.Outlined.DeleteForever, null) },
                    onClick = { menu = false; actions.onDeleteForever(note) },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(stringResource(if (note.isPinned) R.string.action_unpin else R.string.action_pin)) },
                    leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                    onClick = { menu = false; actions.onPin(note) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_move)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
                    onClick = { menu = false; actions.onMove(note) },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                    onClick = { menu = false; actions.onDelete(note) },
                )
            }
        }
    }
}

@Composable
private fun MoveToFolderDialog(
    folders: List<Pair<String, String>>,
    currentFolderId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_move)) },
        text = {
            val options = listOf<Pair<String?, String>>(null to stringResource(R.string.folder_notes)) + folders
            LazyColumn {
                items(options) { (id, name) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = id != currentFolderId) { onSelect(id) }
                            .padding(vertical = 14.dp),
                    ) {
                        Icon(
                            if (id == null) Icons.AutoMirrored.Outlined.Notes else Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = if (id == currentFolderId) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (id == currentFolderId) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Plain-text share, used by the editor too. */
fun shareNoteIntent(text: String): Intent =
    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null)
