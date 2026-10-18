package app.inknotes.ui.folders

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.inknotes.R
import app.inknotes.data.FolderEntity
import app.inknotes.ui.NotesRoute
import app.inknotes.ui.common.GroupedSection
import app.inknotes.ui.common.RowDivider

@Composable
fun FoldersScreen(
    onOpen: (scope: String, folderId: String?) -> Unit,
    onNewNote: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: FoldersViewModel = viewModel(factory = FoldersViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<FolderEntity?>(null) }
    var deleting by remember { mutableStateOf<FolderEntity?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.folders_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showCreate = true }) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = stringResource(R.string.folder_new))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            MediumFloatingActionButton(onClick = onNewNote) {
                Icon(Icons.Outlined.EditNote, contentDescription = stringResource(R.string.new_note), modifier = Modifier.size(28.dp))
            }
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                GroupedSection(header = null) {
                    FolderRow(Icons.Outlined.Inbox, stringResource(R.string.folder_all_notes), state.allCount) {
                        onOpen(NotesRoute.ALL, null)
                    }
                    RowDivider()
                    FolderRow(Icons.AutoMirrored.Outlined.Notes, stringResource(R.string.folder_notes), state.unfiledCount) {
                        onOpen(NotesRoute.UNFILED, null)
                    }
                }
            }
            if (state.folders.isNotEmpty()) {
                item {
                    GroupedSection(header = stringResource(R.string.section_my_folders)) {
                        state.folders.forEachIndexed { index, item ->
                            if (index > 0) RowDivider()
                            FolderRow(
                                icon = Icons.Outlined.Folder,
                                name = item.folder.name,
                                count = item.noteCount,
                                onRename = { renaming = item.folder },
                                onDelete = { deleting = item.folder },
                            ) { onOpen(NotesRoute.FOLDER, item.folder.id) }
                        }
                    }
                }
            }
            if (state.trashCount > 0) {
                item {
                    Spacer(Modifier.height(20.dp))
                    GroupedSection(header = null) {
                        FolderRow(Icons.Outlined.DeleteOutline, stringResource(R.string.folder_recently_deleted), state.trashCount) {
                            onOpen(NotesRoute.TRASH, null)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(120.dp)) }
        }
    }

    if (showCreate) {
        FolderNameDialog(
            title = stringResource(R.string.folder_new),
            initial = "",
            confirmLabel = stringResource(R.string.create),
            onDismiss = { showCreate = false },
            onConfirm = { viewModel.createFolder(it); showCreate = false },
        )
    }
    renaming?.let { folder ->
        FolderNameDialog(
            title = stringResource(R.string.folder_rename),
            initial = folder.name,
            confirmLabel = stringResource(R.string.save),
            onDismiss = { renaming = null },
            onConfirm = { viewModel.renameFolder(folder.id, it); renaming = null },
        )
    }
    deleting?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.folder_delete_confirm_title, folder.name)) },
            text = { Text(stringResource(R.string.folder_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteFolder(folder.id); deleting = null }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(
    icon: ImageVector,
    name: String,
    count: Int,
    onRename: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val countLabel = pluralStringResource(R.plurals.note_count, count, count)
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (onRename != null) ({ menu = true }) else null,
                )
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .semantics(mergeDescendants = true) { contentDescription = "$name, $countLabel" },
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(count.toString(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onRename != null && onDelete != null) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.folder_rename)) },
                    leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                    onClick = { menu = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.folder_delete)) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun FolderNameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(stringResource(R.string.folder_name_hint)) },
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
