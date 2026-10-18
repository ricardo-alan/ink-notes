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
import app.inknotes.model.LineFormat
import app.inknotes.model.LineStyle
import app.inknotes.model.NoteContent
import app.inknotes.model.NoteBlock
import app.inknotes.model.NoteEditing
import app.inknotes.model.NoteTables
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

/** What has the keyboard: a text block (by [NoteBlock.Text.key]) or a table cell. */
sealed interface EditorFocus {
    data class Text(val key: String) : EditorFocus
    data class Cell(val tableId: String, val row: Int, val column: Int) : EditorFocus
}

data class EditorUiState(
    val loaded: Boolean = false,
    val isNew: Boolean = false,
    val content: NoteContent = NoteContent.Empty,
    /** One text field value per text block, by [NoteBlock.Text.key]. */
    val fields: Map<String, TextFieldValue> = mapOf("" to TextFieldValue()),
    val focus: EditorFocus? = null,
    /** Focus the editor should move to programmatically; consumed by the screen. */
    val focusRequest: EditorFocus? = null,
    val isPinned: Boolean = false,
    val createdAt: Long? = null,
    val modifiedAt: Long? = null,
    val closed: Boolean = false,
) {
    /** The text block formatting applies to: the focused one, else the first. */
    val textKey: String get() = (focus as? EditorFocus.Text)?.key ?: ""
    val field: TextFieldValue get() = fields[textKey] ?: TextFieldValue()

    fun textBlock(key: String = textKey): NoteBlock.Text? = NoteTables.textBlock(content, key)

    fun subContent(key: String = textKey): NoteContent =
        textBlock(key)?.let { NoteTables.subContent(content, it) } ?: NoteContent.Empty
}

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
                    fields = syncFields(content, emptyMap()),
                    isPinned = entity.isPinned,
                    createdAt = entity.createdAt,
                    modifiedAt = entity.modifiedAt,
                )
            } else {
                // A "new checklist" shortcut starts with a checklist item; its text becomes the title.
                val start = if (route.checklist) NoteContent(lines = listOf(LineFormat(LineStyle.Checklist))) else NoteContent.Empty
                _state.value = EditorUiState(
                    loaded = true,
                    isNew = true,
                    content = start,
                    fields = syncFields(start, emptyMap()),
                    focusRequest = EditorFocus.Text(""),
                )
            }
        }
    }

    fun onTextChange(key: String, value: TextFieldValue) {
        val current = _state.value
        val block = current.textBlock(key) ?: return
        val sub = NoteTables.subContent(current.content, block)
        if (value.text == sub.text) {
            _state.update { it.copy(fields = it.fields + (key to value)) }
            return
        }
        val result = NoteEditing.applyTextChange(sub, value.text, value.selection.start, value.selection.end)
        val field = if (result.content.text == value.text) {
            value
        } else {
            TextFieldValue(result.content.text, TextRange(result.selectionStart, result.selectionEnd))
        }
        updateContent(NoteTables.replaceText(current.content, block, result.content), current.fields + (key to field))
    }

    fun setLineStyle(style: LineStyle) = editFocusedText { sub, field ->
        NoteEditing.setLineStyle(sub, field.selection.start, field.selection.end, style)
    }

    fun toggleInline(style: InlineStyle) = editFocusedText { sub, field ->
        NoteEditing.toggleInline(sub, field.selection.start, field.selection.end, style)
    }

    fun toggleChecked(key: String, line: Int) {
        val s = _state.value
        val block = s.textBlock(key) ?: return
        val sub = NoteTables.subContent(s.content, block)
        updateContent(NoteTables.replaceText(s.content, block, NoteEditing.toggleChecked(sub, line)), s.fields)
    }

    private fun editFocusedText(edit: (NoteContent, TextFieldValue) -> NoteContent) {
        val s = _state.value
        val block = s.textBlock() ?: return
        val sub = NoteTables.subContent(s.content, block)
        updateContent(NoteTables.replaceText(s.content, block, edit(sub, s.field)), s.fields)
    }

    fun onFocusChanged(target: EditorFocus, focused: Boolean) {
        _state.update {
            when {
                focused -> it.copy(focus = target)
                it.focus == target -> it.copy(focus = null)
                else -> it
            }
        }
    }

    fun requestFocus(target: EditorFocus) = _state.update { it.copy(focusRequest = target) }

    fun consumeFocusRequest() = _state.update { it.copy(focusRequest = null) }

    /** Inserts a table below the caret's line and puts the caret in its first cell. */
    fun insertTable() {
        val s = _state.value
        val block = s.textBlock() ?: return
        val inserted = NoteTables.insertTable(s.content, block, s.field.selection.min, NotesRepository.newId())
        updateContent(inserted.content, s.fields, focusRequest = EditorFocus.Cell(inserted.tableId, 0, 0))
    }

    /** Return in a cell moves to the cell below, adding a row at the bottom of the table. */
    fun onCellChange(tableId: String, row: Int, column: Int, text: String) {
        val s = _state.value
        if ('\n' !in text) {
            updateContent(NoteTables.setCell(s.content, tableId, row, column, text), s.fields)
            return
        }
        var content = NoteTables.setCell(s.content, tableId, row, column, text.replace("\n", ""))
        val table = content.tables.firstOrNull { it.id == tableId } ?: return
        if (row + 1 >= table.rowCount) content = NoteTables.addRow(content, tableId, row + 1)
        updateContent(content, s.fields, focusRequest = EditorFocus.Cell(tableId, row + 1, column))
    }

    /** Backspace at the very start of the text after a table moves into the table's last cell. */
    fun onBackspaceAtStart(key: String) {
        val table = _state.value.content.tables.firstOrNull { it.id == key } ?: return
        _state.update { it.copy(focusRequest = EditorFocus.Cell(table.id, table.rowCount - 1, table.columnCount - 1)) }
    }

    fun addRow() = editFocusedTable { content, cell ->
        NoteTables.addRow(content, cell.tableId, cell.row + 1) to cell.copy(row = cell.row + 1)
    }

    fun addColumn() = editFocusedTable { content, cell ->
        NoteTables.addColumn(content, cell.tableId, cell.column + 1) to cell.copy(column = cell.column + 1)
    }

    fun removeRow() = editFocusedTable { content, cell ->
        NoteTables.removeRow(content, cell.tableId, cell.row) to cell.copy(row = maxOf(0, cell.row - 1))
    }

    fun removeColumn() = editFocusedTable { content, cell ->
        NoteTables.removeColumn(content, cell.tableId, cell.column) to cell.copy(column = maxOf(0, cell.column - 1))
    }

    fun deleteTable() = editFocusedTable { content, cell -> NoteTables.deleteTable(content, cell.tableId) to cell }

    /** Applies a table edit; if the table went away, the caret goes to the end of the text above it. */
    private fun editFocusedTable(edit: (NoteContent, EditorFocus.Cell) -> Pair<NoteContent, EditorFocus.Cell>) {
        val s = _state.value
        val cell = s.focus as? EditorFocus.Cell ?: return
        val blocks = NoteTables.blocks(s.content)
        val above = blocks.getOrNull(blocks.indexOfFirst { it is NoteBlock.Table && it.table.id == cell.tableId } - 1) as? NoteBlock.Text
        val (content, next) = edit(s.content, cell)
        val focus: EditorFocus = if (content.tables.any { it.id == cell.tableId }) {
            next
        } else {
            EditorFocus.Text(above?.key ?: "")
        }
        var fields = s.fields
        if (focus is EditorFocus.Text) {
            // The text below the table merges into [above]: put the caret where the table was.
            val end = above?.let { NoteTables.subContent(s.content, it).text.length } ?: 0
            fields = syncFields(content, fields).let { it + (focus.key to it.getValue(focus.key).copy(selection = TextRange(end))) }
        }
        updateContent(content, fields, focusRequest = focus)
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

    private fun updateContent(content: NoteContent, fields: Map<String, TextFieldValue>, focusRequest: EditorFocus? = null) {
        _state.update {
            it.copy(
                content = content,
                fields = syncFields(content, fields),
                focusRequest = focusRequest ?: it.focusRequest,
            )
        }
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
        /** One field per text block; values whose text no longer matches the note are rebuilt. */
        private fun syncFields(content: NoteContent, fields: Map<String, TextFieldValue>): Map<String, TextFieldValue> =
            NoteTables.blocks(content).filterIsInstance<NoteBlock.Text>().associate { block ->
                val text = NoteTables.subContent(content, block).text
                val old = fields[block.key]
                block.key to when {
                    old == null -> TextFieldValue(text)
                    old.text == text -> old
                    else -> TextFieldValue(text, TextRange(old.selection.start.coerceAtMost(text.length), old.selection.end.coerceAtMost(text.length)))
                }
            }

        private const val KEY_NEW_ID = "newNoteId"
        private const val SAVE_DEBOUNCE_MS = 500L

        val Factory = viewModelFactory {
            initializer { EditorViewModel(createSavedStateHandle(), inkApp) }
        }
    }
}
