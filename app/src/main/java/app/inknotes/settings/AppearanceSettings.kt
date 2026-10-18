package app.inknotes.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class NoteLayout { LIST, GRID }

data class Appearance(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Black backgrounds in dark mode, for OLED screens. */
    val pureBlack: Boolean = false,
    val noteLayout: NoteLayout = NoteLayout.LIST,
    /** Checked checklist items move below the unchecked ones. */
    val moveCheckedToBottom: Boolean = true,
)

/** Device-local look-and-feel preferences. */
class AppearanceSettings(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Appearance> = _state.asStateFlow()

    fun setThemeMode(mode: ThemeMode) = write { putString(THEME_MODE, mode.name) }
    fun setPureBlack(enabled: Boolean) = write { putBoolean(PURE_BLACK, enabled) }
    fun setNoteLayout(layout: NoteLayout) = write { putString(NOTE_LAYOUT, layout.name) }
    fun setMoveCheckedToBottom(enabled: Boolean) = write { putBoolean(MOVE_CHECKED, enabled) }

    private fun write(block: SharedPreferences.Editor.() -> Unit) {
        synchronized(this) {
            prefs.edit(action = block)
            _state.value = read()
        }
    }

    private fun read() = Appearance(
        themeMode = enumOrDefault(prefs.getString(THEME_MODE, null), ThemeMode.SYSTEM),
        pureBlack = prefs.getBoolean(PURE_BLACK, false),
        noteLayout = enumOrDefault(prefs.getString(NOTE_LAYOUT, null), NoteLayout.LIST),
        moveCheckedToBottom = prefs.getBoolean(MOVE_CHECKED, true),
    )

    private companion object {
        const val THEME_MODE = "themeMode"
        const val PURE_BLACK = "pureBlack"
        const val NOTE_LAYOUT = "noteLayout"
        const val MOVE_CHECKED = "moveCheckedToBottom"

        inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
            name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
    }
}
