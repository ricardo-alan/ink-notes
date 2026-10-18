package app.inknotes.ui

import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.inknotes.InkApplication
import app.inknotes.InkIntents
import app.inknotes.ui.editor.EditorScreen
import app.inknotes.ui.folders.FoldersScreen
import app.inknotes.ui.notes.NotesScreen
import app.inknotes.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object FoldersRoute

@Serializable
data object SettingsRoute

/** [scope] is one of [NotesRoute.ALL], [NotesRoute.UNFILED], [NotesRoute.TRASH], [NotesRoute.FOLDER]. */
@Serializable
data class NotesRoute(val scope: String, val folderId: String? = null) {
    companion object {
        const val ALL = "all"
        const val UNFILED = "unfiled"
        const val TRASH = "trash"
        const val FOLDER = "folder"
    }
}

/** A null [noteId] creates a new note in [folderId], starting as a checklist if [checklist]. */
@Serializable
data class EditorRoute(val noteId: String? = null, val folderId: String? = null, val checklist: Boolean = false)

@Composable
fun InkNavHost(request: InkIntents.Request? = null, onRequestHandled: () -> Unit = {}) {
    val nav = rememberNavController()
    val repository = (LocalContext.current.applicationContext as InkApplication).repository
    LaunchedEffect(request) {
        val route = when (request) {
            null -> return@LaunchedEffect
            InkIntents.Request.NewNote -> EditorRoute()
            InkIntents.Request.NewChecklist -> EditorRoute(checklist = true)
            is InkIntents.Request.OpenNote -> EditorRoute(noteId = request.noteId)
            is InkIntents.Request.SharedText -> EditorRoute(noteId = repository.createFromText(request.text))
        }
        // Always on top of the folder list, so Back leads into the app.
        nav.navigate(route) { popUpTo<FoldersRoute>() }
        onRequestHandled()
    }
    val slideSpec = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 500f)
    NavHost(
        navController = nav,
        startDestination = FoldersRoute,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        enterTransition = { slideInHorizontally(slideSpec) { it / 4 } + fadeIn() },
        exitTransition = { slideOutHorizontally(slideSpec) { -it / 8 } + fadeOut() },
        popEnterTransition = { slideInHorizontally(slideSpec) { -it / 8 } + fadeIn() },
        popExitTransition = { slideOutHorizontally(slideSpec) { it / 4 } + fadeOut() },
    ) {
        composable<FoldersRoute> {
            FoldersScreen(
                onOpen = { scope, folderId -> nav.navigate(NotesRoute(scope, folderId)) },
                onNewNote = { nav.navigate(EditorRoute()) },
                onOpenSettings = { nav.navigate(SettingsRoute) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
        composable<NotesRoute> {
            val route = it.toRoute<NotesRoute>()
            NotesScreen(
                onBack = { nav.popBackStack() },
                onOpenNote = { id -> nav.navigate(EditorRoute(noteId = id)) },
                onNewNote = { nav.navigate(EditorRoute(folderId = route.folderId)) },
            )
        }
        composable<EditorRoute> {
            EditorScreen(onBack = { nav.popBackStack() })
        }
    }
}
