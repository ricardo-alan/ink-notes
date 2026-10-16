package app.inknotes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import app.inknotes.R
import app.inknotes.model.NoteTable

private val MinCellWidth = 112.dp
private val TableShape = RoundedCornerShape(12.dp)

/**
 * A table as a grid of plain-text cells. Columns share the available width and the grid scrolls
 * sideways once they would get narrower than [MinCellWidth]. All cells in a row are as tall as the tallest one.
 */
@Composable
fun NoteTableView(
    table: NoteTable,
    focusRequest: EditorFocus.Cell?,
    onFocusRequestHandled: () -> Unit,
    onCellChange: (tableId: String, row: Int, column: Int, text: String) -> Unit,
    onFocusChanged: (EditorFocus, Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val tableLabel = stringResource(R.string.table)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        val cellWidth = max(MinCellWidth, maxWidth / table.columnCount)
        Column(
            Modifier
                .horizontalScroll(rememberScrollState())
                .clip(TableShape)
                .border(1.dp, colors.outlineVariant, TableShape)
                .semantics {
                    contentDescription = tableLabel
                    collectionInfo = CollectionInfo(rowCount = table.rowCount, columnCount = table.columnCount)
                },
        ) {
            table.rows.forEachIndexed { row, cells ->
                Row(Modifier.height(IntrinsicSize.Min)) {
                    cells.forEachIndexed { column, text ->
                        val cell = EditorFocus.Cell(table.id, row, column)
                        TableCell(
                            text = text,
                            width = cellWidth,
                            label = stringResource(R.string.table_cell, row + 1, column + 1),
                            row = row,
                            column = column,
                            lastRow = row == table.rowCount - 1,
                            lastColumn = column == table.columnCount - 1,
                            focusRequested = focusRequest == cell,
                            onFocusRequestHandled = onFocusRequestHandled,
                            onChange = { onCellChange(table.id, row, column, it) },
                            onFocusChanged = { onFocusChanged(cell, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    width: Dp,
    label: String,
    row: Int,
    column: Int,
    lastRow: Boolean,
    lastColumn: Boolean,
    focusRequested: Boolean,
    onFocusRequestHandled: () -> Unit,
    onChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp, color = colors.onSurface)
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf(TextFieldValue(text)) }
    // The note is the source of truth; resync when it changes from outside (row removed, Return pressed...).
    if (value.text != text) value = TextFieldValue(text, TextRange(text.length))

    LaunchedEffect(focusRequested) {
        if (focusRequested) {
            value = value.copy(selection = TextRange(value.text.length))
            focusRequester.requestFocus()
            onFocusRequestHandled()
        }
    }

    val divider = colors.outlineVariant
    BasicTextField(
        value = value,
        onValueChange = {
            value = it
            onChange(it.text)
        },
        textStyle = textStyle,
        cursorBrush = SolidColor(colors.primary),
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(if (focused) colors.surfaceContainerHigh else colors.surface)
            .drawBehind {
                val stroke = 1.dp.toPx()
                if (!lastColumn) drawLine(divider, Offset(size.width - stroke / 2, 0f), Offset(size.width - stroke / 2, size.height), stroke)
                if (!lastRow) drawLine(divider, Offset(0f, size.height - stroke / 2), Offset(size.width, size.height - stroke / 2), stroke)
            }
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                onFocusChanged(it.isFocused)
            }
            .semantics {
                contentDescription = label
                collectionItemInfo = CollectionItemInfo(rowIndex = row, rowSpan = 1, columnIndex = column, columnSpan = 1)
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}
