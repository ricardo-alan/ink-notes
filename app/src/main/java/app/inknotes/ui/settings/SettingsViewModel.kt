package app.inknotes.ui.settings

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.inknotes.settings.Appearance
import app.inknotes.settings.AppearanceSettings
import app.inknotes.settings.ThemeMode
import app.inknotes.sync.DriveClient
import app.inknotes.sync.DriveSync
import app.inknotes.sync.SyncStatus
import app.inknotes.ui.common.inkApp
import com.google.android.gms.auth.api.identity.AuthorizationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val sync: SyncStatus = SyncStatus(),
    val syncing: Boolean = false,
    val appearance: Appearance = Appearance(),
    /** Waiting for Google to grant Drive access. */
    val connecting: Boolean = false,
)

sealed interface SettingsEvent {
    /** Google needs to show its account picker / consent screen. */
    data class Authorize(val intent: PendingIntent) : SettingsEvent
    data object ConnectFailed : SettingsEvent
}

class SettingsViewModel(
    private val sync: DriveSync,
    private val appearanceSettings: AppearanceSettings,
) : ViewModel() {

    private val connecting = MutableStateFlow(false)
    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    val state: StateFlow<SettingsUiState> = combine(
        sync.settings.status,
        sync.isSyncing,
        appearanceSettings.state,
        connecting,
    ) { status, syncing, appearance, connecting ->
        SettingsUiState(status, syncing, appearance, connecting)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(sync.settings.current, appearance = appearanceSettings.state.value),
    )

    fun setThemeMode(mode: ThemeMode) = appearanceSettings.setThemeMode(mode)

    fun setPureBlack(enabled: Boolean) = appearanceSettings.setPureBlack(enabled)

    fun turnOn() = connect(account = null)

    fun signInAgain() = connect(account = sync.settings.current.account)

    fun turnOff() = viewModelScope.launch { sync.disable() }

    fun syncNow() = sync.requestSync()

    fun onAuthorizeResult(resultCode: Int, data: Intent?) = viewModelScope.launch {
        if (resultCode != Activity.RESULT_OK) {
            connecting.value = false
            return@launch
        }
        attempt { finish(sync.auth.resultFromIntent(data)) }
    }

    private fun connect(account: String?) = viewModelScope.launch {
        connecting.value = true
        attempt {
            val result = sync.auth.authorize(account)
            val intent = result.pendingIntent
            if (result.hasResolution() && intent != null) _events.send(SettingsEvent.Authorize(intent)) else finish(result)
        }
    }

    private suspend fun finish(result: AuthorizationResult) {
        val token = checkNotNull(result.accessToken) { "No access token" }
        // The account is remembered so background syncs ask Google for the same one.
        sync.enable(DriveClient(token).accountEmail())
        connecting.value = false
    }

    private suspend fun attempt(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("InkSync", "Couldn't connect to Google Drive", e)
            connecting.value = false
            _events.send(SettingsEvent.ConnectFailed)
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { SettingsViewModel(inkApp.sync, inkApp.appearance) } }
    }
}
