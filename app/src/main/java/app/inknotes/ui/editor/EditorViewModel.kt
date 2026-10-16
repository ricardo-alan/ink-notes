package app.inknotes.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import app.inknotes.InkApplication
import app.inknotes.data.NotesRepository
import app.inknotes.model.InlineStyle
import app.inknotes.model.LineStyle
import app.inknotes.model.NoteContent
import app.inknotes.model.NoteEditing
import app.inknotes.ui.EditorRoute
import app.inknotes.ui.common.inkApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class EditorUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = false,
    val content: NoteContent = NoteContent.Empty,
    val field: TextFieldValue = TextFieldValue(),
    val isPinned: Boolean = false,
    val createdAt: Long? = null,
    val modifiedAt: Long? = null,
    val closed: Boolean = false,
)

class EditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val app: InkApplication,
) : ViewModel() {

    private val repository: NotesRepository = app.repository
    private val route = savedStateHandle.toRoute<EditorRoute>()

    /** New notes get their id up front, so a process restart keeps editing the same note. */
    private val noteId: String = route.noteId
        ?: savedStateHandle.get<String>(KEY_NEW_ID)
        ?: NotesRepository.newId().also { savedStateHandle[KEY_NEW_ID] = it }

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var persisted = false
    private var dirty = false
    private var saveJob: Job? = null
    private val saveMutex = Mutex()

    init {
        viewModelScope.launch {
            val loaded = repository.loadContent(noteId)
            if (loaded != null) {
                val (entity, content) = loaded
                persisted = true
                _state.value = EditorUiState(
                    loaded = true,
                    content = content,
                    field = TextFieldValue(content.text),
                    isPinned = entity.isPinned,
                    createdAt = entity.createdAt,
                    modifiedAt = entity.modifiedAt,
                )
            } else {
                _state.value = EditorUiState(loaded = true, isNew = true)
            }
        }
    }

    fun onValueChange(value: TextFieldValue) {
        val current = _state.value
        if (value.text == current.content.text) {
            _state.update { it.copy(field = value) }
            return
        }
        val result = NoteEditing.applyTextChange(current.content, value.text, value.selection.start, value.selection.end)
        val field = if (result.content.text == value.text) {
            value
        } else {
            TextFieldValue(result.content.text, TextRange(result.selectionStart, result.selectionEnd))
        }
        updateContent(result.content, field)
    }

    fun setLineStyle(style: LineStyle) {
        val s = _state.value
        updateContent(NoteEditing.setLineStyle(s.content, s.field.selection.start, s.field.selection.end, style), s.field)
    }

    fun toggleInline(style: InlineStyle) {
        val s = _state.value
        updateContent(NoteEditing.toggleInline(s.content, s.field.selection.start, s.field.selection.end, style), s.field)
    }

    fun toggleChecked(line: Int) {
        val s = _state.value
        updateContent(NoteEditing.toggleChecked(s.content, line), s.field)
    }

    fun togglePinned() {
        val pinned = !_state.value.isPinned
        _state.update { it.copy(isPinned = pinned) }
        viewModelScope.launch {
            flush()
            if (persisted) repository.setPinned(noteId, pinned)
        }
    }

    fun delete() {
        saveJob?.cancel()
        dirty = false
        app.appScope.launch {
            saveMutex.withLock { if (persisted) repository.moveToTrash(noteId) }
        }
        _state.update { it.copy(closed = true) }
    }

    /** Called when the screen stops (app backgrounded or navigated away). */
    fun flushNow() {
        if (!dirty) return
        saveJob?.cancel()
        app.appScope.launch { flush() }
    }

    private fun updateContent(content: NoteContent, field: TextFieldValue) {
        _state.update { it.copy(content = content, field = field) }
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            flush()
        }
    }

    private suspend fun flush() = saveMutex.withLock {
        if (!dirty || _state.value.closed) return@withLock
        dirty = false
        val content = _state.value.content
        if (content.isBlank) {
            // Like Apple Notes, an emptied note simply disappears.
            if (persisted) {
                repository.deleteForever(noteId)
                persisted = false
            }
        } else {
            val saved = repository.saveContent(noteId, route.folderId, content)
            persisted = true
            _state.update { it.copy(createdAt = saved.createdAt, modifiedAt = saved.modifiedAt) }
        }
    }

    override fun onCleared() {
        if (dirty) app.appScope.launch { flush() }
    }

    companion object {
        private const val KEY_NEW_ID = "newNoteId"
        private const val SAVE_DEBOUNCE_MS = 500L

        val Factory = viewModelFactory {
            initializer { EditorViewModel(createSavedStateHandle(), inkApp) }
        }
    }
}
