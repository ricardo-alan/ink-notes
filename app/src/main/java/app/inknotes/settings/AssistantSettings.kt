package app.inknotes.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What system assistants may do with Ink through App Functions (Android 16+). Creating notes and
 * adding checklist items are always allowed; reading notes needs the user's opt-in. Device-local.
 */
class AssistantSettings(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("assistants", Context.MODE_PRIVATE)
    private val _allowRead = MutableStateFlow(prefs.getBoolean(ALLOW_READ, false))
    val allowRead: StateFlow<Boolean> = _allowRead.asStateFlow()

    fun setAllowRead(enabled: Boolean) {
        synchronized(this) {
            prefs.edit { putBoolean(ALLOW_READ, enabled) }
            _allowRead.value = enabled
        }
    }

    companion object {
        private const val ALLOW_READ = "allowRead"

        /** App Functions only exist on Android 16 and later. */
        val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA

        /**
         * Hidden for now: assistants calling App Functions are still rolling out, so the switch did
         * nothing visible for users. The functions stay registered; reading stays off (the default).
         */
        const val SHOW_IN_SETTINGS = false
    }
}
