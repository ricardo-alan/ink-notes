package app.inknotes

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.inknotes.settings.ThemeMode
import app.inknotes.ui.InkNavHost
import app.inknotes.ui.theme.InkTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handledIntent = savedInstanceState?.getString(KEY_HANDLED_INTENT)
        handle(intent)
        val app = application as InkApplication
        val appearanceSettings = app.appearance
        setContent {
            val appearance by appearanceSettings.state.collectAsStateWithLifecycle()
            val request by app.pendingRequest.collectAsStateWithLifecycle()
            val darkTheme = when (appearance.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status and navigation bar icons follow the app's theme, not the system's.
            DisposableEffect(darkTheme) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            InkTheme(darkTheme = darkTheme, pureBlack = appearance.pureBlack) {
                InkNavHost(request = request, onRequestHandled = { app.pendingRequest.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /**
     * Recreated activities (rotation, process death) get their original intent again; remembering
     * which one was handled keeps a share from creating the same note twice, while an intent that
     * arrives together with the recreation is still handled.
     */
    private var handledIntent: String? = null

    private fun handle(intent: Intent?) {
        val key = intent?.toUri(0) ?: return
        if (key == handledIntent) return
        handledIntent = key
        InkIntents.parse(intent)?.let { (application as InkApplication).submit(it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_HANDLED_INTENT, handledIntent)
    }

    private companion object {
        const val KEY_HANDLED_INTENT = "handledIntent"
    }
}
