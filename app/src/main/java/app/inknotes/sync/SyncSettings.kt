package app.inknotes.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SyncError { NONE, AUTH, NETWORK, OTHER }

data class SyncStatus(
    val enabled: Boolean = false,
    val account: String? = null,
    val lastSyncedAt: Long? = null,
    val error: SyncError = SyncError.NONE,
)

/** Small, device-local sync preferences. Never synced and never holds tokens. */
class SyncSettings(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("sync", Context.MODE_PRIVATE)
    private val _status = MutableStateFlow(read())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    val current: SyncStatus get() = _status.value

    fun enable(account: String?) = write {
        putBoolean(ENABLED, true)
        putString(ACCOUNT, account)
        putString(ERROR, SyncError.NONE.name)
    }

    fun disable() = write { clear() }

    fun recordSuccess(at: Long) = write {
        putLong(LAST_SYNCED, at)
        putString(ERROR, SyncError.NONE.name)
    }

    fun recordError(error: SyncError) = write { putString(ERROR, error.name) }

    private fun write(block: SharedPreferences.Editor.() -> Unit) {
        synchronized(this) {
            prefs.edit(commit = true, action = block)
            _status.value = read()
        }
    }

    private fun read() = SyncStatus(
        enabled = prefs.getBoolean(ENABLED, false),
        account = prefs.getString(ACCOUNT, null),
        lastSyncedAt = prefs.getLong(LAST_SYNCED, 0L).takeIf { it > 0 },
        error = prefs.getString(ERROR, null)?.let { runCatching { SyncError.valueOf(it) }.getOrNull() } ?: SyncError.NONE,
    )

    private companion object {
        const val ENABLED = "enabled"
        const val ACCOUNT = "account"
        const val LAST_SYNCED = "lastSyncedAt"
        const val ERROR = "error"
    }
}
