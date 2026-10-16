package app.inknotes.ui.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import app.inknotes.data.FolderWithCount
import app.inknotes.data.NoteScope
import app.inknotes.data.NoteSummary
import app.inknotes.data.NotesRepository
import app.inknotes.ui.NotesRoute
import app.inknotes.ui.common.inkApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotesUiState(
    val folderName: String? = null,
    val notes: List<NoteSummary> = emptyList(),
    val folders: List<FolderWithCount> = emptyList(),
    val loaded: Boolean = false,
)

class NotesViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: NotesRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<NotesRoute>()

    val scope: NoteScope = when (route.scope) {
        NotesRoute.UNFILED -> NoteScope.Unfiled
        NotesRoute.TRASH -> NoteScope.Trash
        NotesRoute.FOLDER -> NoteScope.Folder(requireNotNull(route.folderId))
        else -> NoteScope.All
    }

    val query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<NotesUiState> = combine(
        query.flatMapLatest { repository.notes(scope, it) },
        (scope as? NoteScope.Folder)?.let { f -> repository.observeFolder(f.id).map { it?.name } } ?: flowOf(null),
        repository.folders,
    ) { notes, folderName, folders -> NotesUiState(folderName, notes, folders, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun setPinned(id: String, pinned: Boolean) = viewModelScope.launch { repository.setPinned(id, pinned) }
    fun moveToFolder(id: String, folderId: String?) = viewModelScope.launch { repository.moveToFolder(id, folderId) }
    fun moveToTrash(id: String) = viewModelScope.launch { repository.moveToTrash(id) }
    fun restore(id: String) = viewModelScope.launch { repository.restore(id) }
    fun deleteForever(id: String) = viewModelScope.launch { repository.deleteForever(id) }

    companion object {
        val Factory = viewModelFactory {
            initializer { NotesViewModel(createSavedStateHandle(), inkApp.repository) }
        }
    }
}
