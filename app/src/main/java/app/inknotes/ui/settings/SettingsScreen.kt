package app.inknotes.ui.settings

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.inknotes.R
import app.inknotes.settings.Appearance
import app.inknotes.settings.AssistantSettings
import app.inknotes.settings.ThemeMode
import app.inknotes.sync.SyncError
import app.inknotes.ui.common.GroupedSection
import app.inknotes.ui.common.RowDivider

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val connectFailed = stringResource(R.string.sync_connect_failed)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val authorize = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        viewModel.onAuthorizeResult(it.resultCode, it.data)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.Authorize ->
                    authorize.launch(IntentSenderRequest.Builder(event.intent.intentSender).build())
                SettingsEvent.ConnectFailed ->
                    snackbar.showSnackbar(connectFailed)
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                AppearanceSection(
                    appearance = state.appearance,
                    onThemeMode = viewModel::setThemeMode,
                    onPureBlack = viewModel::setPureBlack,
                )
            }
            item {
                GroupedSection(header = stringResource(R.string.settings_section_notes)) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.move_checked)) },
                        supportingContent = { Text(stringResource(R.string.move_checked_summary)) },
                        leadingContent = { Icon(Icons.Outlined.Checklist, contentDescription = null) },
                        trailingContent = { Switch(checked = state.appearance.moveCheckedToBottom, onCheckedChange = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.toggleable(
                            value = state.appearance.moveCheckedToBottom,
                            role = Role.Switch,
                            onValueChange = viewModel::setMoveCheckedToBottom,
                        ),
                    )
                }
            }
            if (AssistantSettings.SHOW_IN_SETTINGS && AssistantSettings.isSupported) {
                item {
                    GroupedSection(header = stringResource(R.string.settings_section_assistants)) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.assistants_read)) },
                            supportingContent = { Text(stringResource(R.string.assistants_read_summary)) },
                            leadingContent = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                            trailingContent = { Switch(checked = state.assistantsCanRead, onCheckedChange = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.toggleable(
                                value = state.assistantsCanRead,
                                role = Role.Switch,
                                onValueChange = viewModel::setAssistantsCanRead,
                            ),
                        )
                    }
                }
            }
            item {
                GroupedSection(header = stringResource(R.string.settings_section_sync)) {
                    val enabled = state.sync.enabled
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.sync_drive)) },
                        supportingContent = {
                            Text(
                                state.sync.account?.takeIf { enabled }?.let { stringResource(R.string.sync_account, it) }
                                    ?: stringResource(R.string.sync_drive_summary)
                            )
                        },
                        leadingContent = { Icon(Icons.Outlined.CloudSync, contentDescription = null) },
                        trailingContent = {
                            if (state.connecting) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Switch(checked = enabled, onCheckedChange = null)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.toggleable(
                            value = enabled,
                            enabled = !state.connecting,
                            role = Role.Switch,
                            onValueChange = { on -> if (on) viewModel.turnOn() else viewModel.turnOff() },
                        ),
                    )
                    if (enabled) {
                        RowDivider()
                        SyncStatusRow(state, onSyncNow = viewModel::syncNow, onSignInAgain = viewModel::signInAgain)
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.sync_privacy_info),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
                )
            }
            item { Spacer(Modifier.height(48.dp)) }
        }
    }
}

@Composable
private fun SyncStatusRow(state: SettingsUiState, onSyncNow: () -> Unit, onSignInAgain: () -> Unit) {
    val error = state.sync.error
    val text = when {
        state.syncing -> stringResource(R.string.sync_in_progress)
        error == SyncError.AUTH -> stringResource(R.string.sync_error_auth)
        error == SyncError.NETWORK -> stringResource(R.string.sync_error_network)
        error == SyncError.OTHER -> stringResource(R.string.sync_error_other)
        state.sync.lastSyncedAt != null -> stringResource(
            R.string.sync_last,
            DateUtils.getRelativeTimeSpanString(state.sync.lastSyncedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
        )
        else -> stringResource(R.string.sync_never)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
    ) {
        if (error != SyncError.NONE && !state.syncing) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = 12.dp).size(20.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (error == SyncError.AUTH) {
            TextButton(onClick = onSignInAgain, enabled = !state.connecting) { Text(stringResource(R.string.sync_sign_in_again)) }
        } else {
            TextButton(onClick = onSyncNow, enabled = !state.syncing) { Text(stringResource(R.string.sync_now)) }
        }
    }
}

@Composable
private fun AppearanceSection(
    appearance: Appearance,
    onThemeMode: (ThemeMode) -> Unit,
    onPureBlack: (Boolean) -> Unit,
) {
    val options = listOf(
        ThemeMode.SYSTEM to R.string.theme_system,
        ThemeMode.LIGHT to R.string.theme_light,
        ThemeMode.DARK to R.string.theme_dark,
    )
    GroupedSection(header = stringResource(R.string.settings_section_appearance)) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.theme)) },
            leadingContent = { Icon(Icons.Outlined.Palette, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            options.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = appearance.themeMode == mode,
                    onClick = { onThemeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(stringResource(label)) }
            }
        }
        RowDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.pure_black)) },
            supportingContent = { Text(stringResource(R.string.pure_black_summary)) },
            leadingContent = { Icon(Icons.Outlined.Contrast, contentDescription = null) },
            trailingContent = { Switch(checked = appearance.pureBlack, onCheckedChange = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.toggleable(
                value = appearance.pureBlack,
                role = Role.Switch,
                onValueChange = onPureBlack,
            ),
        )
    }
}
