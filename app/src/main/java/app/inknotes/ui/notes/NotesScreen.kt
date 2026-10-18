package app.inknotes.ui.notes

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import app.inknotes.model.LineFormat
import app.inknotes.model.LineStyle
import app.inknotes.model.NoteContent
import app.inknotes.settings.NoteLayout
import kotlinx.serialization.json.Json
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
import androidx.compose.runtime.key
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
    val layout by viewModel.layout.collectAsStateWithLifecycle()
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
                title = { Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = {
                    if (state.loaded) Text(pluralStringResource(R.plurals.note_count, state.notes.size, state.notes.size))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    val grid = layout == NoteLayout.GRID
                    IconButton(onClick = { viewModel.setLayout(if (grid) NoteLayout.LIST else NoteLayout.GRID) }) {
                        Icon(
                            if (grid) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                            contentDescription = stringResource(if (grid) R.string.layout_list else R.string.layout_grid),
                        )
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
        val pinnedHeader = stringResource(R.string.section_pinned)
        val sections = buildList {
            val pinned = state.notes.filter { it.isPinned && !isTrash }
            if (pinned.isNotEmpty()) add(NoteGroup("pinned", pinnedHeader, pinned))
            state.notes.filter { !it.isPinned || isTrash }
                .groupBy { DateGrouping.sectionFor(if (isTrash) it.trashedAt ?: it.modifiedAt else it.modifiedAt) }
                .forEach { (section, notes) ->
                    val header = when (section) {
                        is DateSection.Relative -> stringResource(section.label)
                        is DateSection.Month -> DateGrouping.monthLabel(section, locale)
                    }
                    add(NoteGroup(section.toString(), header, notes))
                }
        }
        val emptyMessage = when {
            !state.loaded || state.notes.isNotEmpty() -> null
            query.isNotBlank() -> R.string.no_results
            isTrash -> R.string.trash_empty
            else -> R.string.no_notes
        }
        val onQueryChange: (String) -> Unit = { viewModel.query.value = it }

        if (layout == NoteLayout.GRID) {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 120.dp,
                ),
                verticalItemSpacing = 12.dp,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    ListHeader(query, onQueryChange, isTrash, emptyMessage, horizontalPadding = 0.dp)
                }
                sections.forEach { group ->
                    item(key = "header-${group.key}", span = StaggeredGridItemSpan.FullLine) {
                        SectionHeader(group.header, Modifier.padding(start = 16.dp, top = 8.dp))
                    }
                    items(group.notes, key = { it.id }) { note ->
                        NoteCard(note, isTrash, locale, onOpenNote, actions)
                    }
                }
            }
        } else {
            LazyColumn(contentPadding = padding) {
                item { ListHeader(query, onQueryChange, isTrash, emptyMessage, horizontalPadding = 16.dp) }
                sections.forEach { group ->
                    item(key = group.key) {
                        NoteSection(group.header, group.notes, isTrash, locale, onOpenNote, actions)
                    }
                }
                item { Spacer(Modifier.height(120.dp)) }
            }
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


private class NoteGroup(val key: String, val header: String, val notes: List<NoteSummary>)

/** Search, the Recently Deleted explanation and the empty state: shared by list and gallery. */
@Composable
private fun ListHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    isTrash: Boolean,
    emptyMessage: Int?,
    horizontalPadding: Dp,
) {
    Column {
        SearchField(query, onQueryChange, horizontalPadding)
        if (isTrash) {
            Text(
                stringResource(R.string.trash_info, NotesRepository.TRASH_RETENTION_DAYS.toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = horizontalPadding + 16.dp, vertical = 4.dp),
            )
        }
        if (emptyMessage != null) {
            Text(
                stringResource(emptyMessage),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.semantics { heading() },
    )
}

private class NoteActions(
    val onPin: (NoteSummary) -> Unit,
    val onMove: (NoteSummary) -> Unit,
    val onDelete: (NoteSummary) -> Unit,
    val onRestore: (NoteSummary) -> Unit,
    val onDeleteForever: (NoteSummary) -> Unit,
)

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, horizontalPadding: Dp) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 8.dp),
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
            // Keyed so per-row state (swipe offset, menu) follows the note when rows move between sections.
            key(note.id) { NoteRow(note, isTrash, locale, onOpenNote, actions) }
        }
    }
}

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
        if (isTrash) {
            NoteRowContent(note, locale, onClick = { menu = true }, onLongClick = { menu = true })
        } else {
            SwipeableNote(note, actions) {
                NoteRowContent(note, locale, onClick = { onOpenNote(note.id) }, onLongClick = { menu = true })
            }
        }
        NoteMenu(note, isTrash, expanded = menu, onDismiss = { menu = false }, actions = actions)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRowContent(note: NoteSummary, locale: Locale, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
}

/**
 * Apple Notes gestures: swipe left to delete, swipe right to pin or unpin.
 * TalkBack users get the same actions as custom accessibility actions.
 */
@Composable
private fun SwipeableNote(note: NoteSummary, actions: NoteActions, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val pinLabel = stringResource(if (note.isPinned) R.string.action_unpin else R.string.action_pin)
    val deleteLabel = stringResource(R.string.action_delete)
    SwipeToDismissBox(
        state = state,
        backgroundContent = { SwipeBackground(state.dismissDirection, note.isPinned) },
        onDismiss = { direction ->
            when (direction) {
                SwipeToDismissBoxValue.EndToStart -> actions.onDelete(note)
                SwipeToDismissBoxValue.StartToEnd -> actions.onPin(note)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            scope.launch { state.reset() }
        },
        modifier = Modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(pinLabel) { actions.onPin(note); true },
                CustomAccessibilityAction(deleteLabel) { actions.onDelete(note); true },
            )
        },
    ) { content() }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, isPinned: Boolean) {
    val colors = MaterialTheme.colorScheme
    val (color, contentColor, icon, alignment) = when (direction) {
        SwipeToDismissBoxValue.EndToStart -> SwipeStyle(colors.errorContainer, colors.onErrorContainer, Icons.Outlined.Delete, Alignment.CenterEnd)
        SwipeToDismissBoxValue.StartToEnd -> SwipeStyle(
            colors.tertiaryContainer,
            colors.onTertiaryContainer,
            if (isPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin,
            Alignment.CenterStart,
        )
        SwipeToDismissBoxValue.Settled -> return
    }
    Box(
        contentAlignment = alignment,
        modifier = Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp),
    ) {
        Icon(icon, contentDescription = null, tint = contentColor)
    }
}

private data class SwipeStyle(val color: Color, val contentColor: Color, val icon: ImageVector, val alignment: Alignment)

/** Gallery card: title, the first lines of the note and its date. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: NoteSummary,
    isTrash: Boolean,
    locale: Locale,
    onOpenNote: (String) -> Unit,
    actions: NoteActions,
) {
    var menu by remember { mutableStateOf(false) }
    val lines = remember(note.content) { previewLines(note.content) }
    Box {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .combinedClickable(
                    onClick = { if (isTrash) menu = true else onOpenNote(note.id) },
                    onLongClick = { menu = true },
                ),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    note.title.ifEmpty { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (lines.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        lines.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = PREVIEW_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.isPinned && !isTrash) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = stringResource(R.string.section_pinned),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        DateGrouping.rowDate(note.modifiedAt, locale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        NoteMenu(note, isTrash, expanded = menu, onDismiss = { menu = false }, actions = actions)
    }
}

private const val PREVIEW_LINES = 8
private val previewJson = Json { ignoreUnknownKeys = true }

/** The note's lines after the title, with list markers, for the gallery card. */
private fun previewLines(json: String): List<String> {
    val content = runCatching { previewJson.decodeFromString<NoteContent>(json) }.getOrNull() ?: return emptyList()
    val lines = content.text.lines()
    val titleIndex = lines.indexOfFirst { it.isNotBlank() }
    var number = 0
    return lines.withIndex().drop(titleIndex + 1).mapNotNull { (index, line) ->
        val format = content.lines.getOrElse(index) { LineFormat() }
        number = if (format.style == LineStyle.Numbered) number + 1 else 0
        if (line.isBlank()) return@mapNotNull null
        when (format.style) {
            LineStyle.Checklist -> (if (format.checked) "☑ " else "☐ ") + line
            LineStyle.Bullet -> "• $line"
            LineStyle.Numbered -> "$number. $line"
            else -> line
        }
    }.take(PREVIEW_LINES)
}

@Composable
private fun NoteMenu(
    note: NoteSummary,
    isTrash: Boolean,
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: NoteActions,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (isTrash) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_restore)) },
                leadingIcon = { Icon(Icons.Outlined.RestoreFromTrash, null) },
                onClick = { onDismiss(); actions.onRestore(note) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete_forever)) },
                leadingIcon = { Icon(Icons.Outlined.DeleteForever, null) },
                onClick = { onDismiss(); actions.onDeleteForever(note) },
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(if (note.isPinned) R.string.action_unpin else R.string.action_pin)) },
                leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                onClick = { onDismiss(); actions.onPin(note) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_move)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
                onClick = { onDismiss(); actions.onMove(note) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete)) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                onClick = { onDismiss(); actions.onDelete(note) },
            )
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
