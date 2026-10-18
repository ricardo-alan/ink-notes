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
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /** A shortcut, tile, widget or share waiting to be handled by the navigation. */
    private val pendingRequest = MutableStateFlow<InkIntents.Request?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // On recreation (rotation, theme change) the intent was already handled.
        if (savedInstanceState == null) pendingRequest.value = InkIntents.parse(intent)
        val appearanceSettings = (application as InkApplication).appearance
        setContent {
            val appearance by appearanceSettings.state.collectAsStateWithLifecycle()
            val request by pendingRequest.collectAsStateWithLifecycle()
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
                InkNavHost(request = request, onRequestHandled = { pendingRequest.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingRequest.value = InkIntents.parse(intent)
    }
}
