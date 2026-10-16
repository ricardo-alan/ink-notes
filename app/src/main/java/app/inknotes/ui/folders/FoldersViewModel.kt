package app.inknotes.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.inknotes.data.FolderWithCount
import app.inknotes.data.NotesRepository
import app.inknotes.ui.common.inkApp
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FoldersUiState(
    val allCount: Int = 0,
    val unfiledCount: Int = 0,
    val trashCount: Int = 0,
    val folders: List<FolderWithCount> = emptyList(),
)

class FoldersViewModel(private val repository: NotesRepository) : ViewModel() {

    val state: StateFlow<FoldersUiState> = combine(
        repository.allCount,
        repository.unfiledCount,
        repository.trashCount,
        repository.folders,
    ) { all, unfiled, trash, folders -> FoldersUiState(all, unfiled, trash, folders) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoldersUiState())

    fun createFolder(name: String) = viewModelScope.launch { repository.createFolder(name) }
    fun renameFolder(id: String, name: String) = viewModelScope.launch { repository.renameFolder(id, name) }
    fun deleteFolder(id: String) = viewModelScope.launch { repository.deleteFolder(id) }

    companion object {
        val Factory = viewModelFactory { initializer { FoldersViewModel(inkApp.repository) } }
    }
}
