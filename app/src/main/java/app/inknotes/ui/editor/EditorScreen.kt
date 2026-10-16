package app.inknotes.ui.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.KeyboardHide
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.inknotes.R
import app.inknotes.model.InlineStyle
import app.inknotes.model.LineStyle
import app.inknotes.model.NoteContent
import app.inknotes.model.NoteEditing
import app.inknotes.ui.notes.shareNoteIntent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun EditorScreen(
    onBack: () -> Unit,
    viewModel: EditorViewModel = viewModel(factory = EditorViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.flushNow() }
    LaunchedEffect(state.closed) { if (state.closed) onBack() }
    LaunchedEffect(state.loaded) {
        if (state.loaded && state.isNew) focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (focused) {
                        TextButton(onClick = { keyboard?.hide(); focusManager.clearFocus() }) {
                            Text(stringResource(R.string.hide_keyboard))
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (state.isPinned) R.string.action_unpin else R.string.action_pin)) },
                                leadingIcon = { Icon(Icons.Outlined.PushPin, null) },
                                onClick = { menu = false; viewModel.togglePinned() },
                                enabled = !state.content.isBlank,
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_share)) },
                                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                onClick = { menu = false; context.startActivity(shareNoteIntent(state.content.text)) },
                                enabled = !state.content.isBlank,
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                onClick = { menu = false; viewModel.delete() },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).imePadding()) {
            if (state.loaded) {
                NoteBody(
                    state = state,
                    onValueChange = viewModel::onValueChange,
                    onToggleChecked = viewModel::toggleChecked,
                    modifier = Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused },
                )
            }
            AnimatedVisibility(
                visible = focused,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
            ) {
                FormattingToolbar(
                    content = state.content,
                    selectionStart = state.field.selection.start,
                    selectionEnd = state.field.selection.end,
                    onLineStyle = viewModel::setLineStyle,
                    onInline = viewModel::toggleInline,
                )
            }
        }
    }
}

@Composable
private fun NoteBody(
    state: EditorUiState,
    onValueChange: (androidx.compose.ui.text.input.TextFieldValue) -> Unit,
    onToggleChecked: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.sp, color = colors.onSurface)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val display = remember(state.content, colors.onSurfaceVariant) {
        buildNoteDisplay(state.content, colors.onSurfaceVariant)
    }
    val transformation = remember(display) { NoteVisualTransformation(state.content, display) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewport = maxHeight
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            NoteDate(state.modifiedAt)
            Box(Modifier.fillMaxWidth()) {
                BasicTextField(
                    value = state.field,
                    onValueChange = onValueChange,
                    textStyle = textStyle,
                    visualTransformation = transformation,
                    cursorBrush = SolidColor(colors.primary),
                    onTextLayout = { layout = it },
                    modifier = modifier
                        .fillMaxWidth()
                        .heightIn(min = viewport - 48.dp)
                        .padding(bottom = 120.dp),
                )
                layout?.let { ListMarkers(state.content, display, it, textStyle, onToggleChecked) }
            }
        }
    }
}

@Composable
private fun NoteDate(modifiedAt: Long?) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val label = remember(modifiedAt, locale) {
        val instant = modifiedAt?.let(Instant::ofEpochMilli) ?: Instant.now()
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG, FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}

/** Bullets, numbers and tappable checkboxes drawn in the indent of list lines. */
@Composable
private fun ListMarkers(
    content: NoteContent,
    display: NoteDisplay,
    layout: TextLayoutResult,
    textStyle: TextStyle,
    onToggleChecked: (Int) -> Unit,
) {
    if (layout.layoutInput.text != display.text) return
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    val indent = with(density) { ListIndent.toDp() }
    val itemLabel = stringResource(R.string.checklist_item)
    var number = 0

    content.lines.forEachIndexed { index, format ->
        number = if (format.style == LineStyle.Numbered) number + 1 else 0
        if (!format.style.isList) return@forEachIndexed
        val offset = display.mapping.originalToTransformed(NoteEditing.lineStart(content.text, index).coerceAtMost(content.text.length))
        val visualLine = layout.getLineForOffset(offset)
        val top = layout.getLineTop(visualLine)
        val height = with(density) { (layout.getLineBottom(visualLine) - top).toDp() }
        val position = Modifier
            .offset { IntOffset(0, top.toInt()) }
            .width(indent)
            .height(height)

        when (format.style) {
            LineStyle.Checklist -> {
                val lineText = NoteEditing.lineText(content.text, index)
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = position.semantics(mergeDescendants = true) {
                        role = Role.Checkbox
                        contentDescription = lineText.ifBlank { itemLabel }
                        stateDescription = if (format.checked) "✓" else ""
                    },
                ) {
                    IconToggleButton(
                        checked = format.checked,
                        onCheckedChange = { onToggleChecked(index) },
                        modifier = Modifier.offset(x = (-12).dp).size(44.dp),
                    ) {
                        CheckCircle(checked = format.checked)
                    }
                }
            }
            else -> Box(contentAlignment = Alignment.CenterStart, modifier = position) {
                Text(
                    text = if (format.style == LineStyle.Bullet) "•" else "$number.",
                    style = textStyle.copy(color = colors.onSurfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun CheckCircle(checked: Boolean) {
    val colors = MaterialTheme.colorScheme
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    AnimatedContent(
        targetState = checked,
        transitionSpec = { (scaleIn(spatial) + fadeIn()) togetherWith fadeOut() },
        label = "check",
    ) { isChecked ->
        Icon(
            imageVector = if (isChecked) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (isChecked) colors.primary else colors.outline,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun FormattingToolbar(
    content: NoteContent,
    selectionStart: Int,
    selectionEnd: Int,
    onLineStyle: (LineStyle) -> Unit,
    onInline: (InlineStyle) -> Unit,
) {
    var formatMode by rememberSaveable { mutableStateOf(false) }
    val lineStyle = NoteEditing.lineStyleAt(content, selectionStart)
    fun inlineActive(style: InlineStyle) = NoteEditing.isInlineActive(content, selectionStart, selectionEnd, style)

    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
    ) {
        AnimatedContent(targetState = formatMode, label = "toolbar") { formatting ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (formatting) {
                    StyleButton(R.string.format_title, lineStyle == LineStyle.Title) { onLineStyle(LineStyle.Title) }
                    StyleButton(R.string.format_heading, lineStyle == LineStyle.Heading) { onLineStyle(LineStyle.Heading) }
                    StyleButton(R.string.format_body, lineStyle == LineStyle.Body) { onLineStyle(LineStyle.Body) }
                    ToolbarToggle(Icons.Outlined.FormatBold, R.string.format_bold, inlineActive(InlineStyle.Bold)) { onInline(InlineStyle.Bold) }
                    ToolbarToggle(Icons.Outlined.FormatItalic, R.string.format_italic, inlineActive(InlineStyle.Italic)) { onInline(InlineStyle.Italic) }
                    ToolbarToggle(Icons.Outlined.FormatUnderlined, R.string.format_underline, inlineActive(InlineStyle.Underline)) { onInline(InlineStyle.Underline) }
                    ToolbarToggle(Icons.Outlined.FormatStrikethrough, R.string.format_strikethrough, inlineActive(InlineStyle.Strikethrough)) { onInline(InlineStyle.Strikethrough) }
                    IconButton(onClick = { formatMode = false }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.format_close))
                    }
                } else {
                    ToolbarToggle(Icons.Outlined.TextFields, R.string.format_text_style, false) { formatMode = true }
                    ToolbarToggle(Icons.Outlined.Checklist, R.string.format_checklist, lineStyle == LineStyle.Checklist) { onLineStyle(LineStyle.Checklist) }
                    ToolbarToggle(Icons.AutoMirrored.Outlined.FormatListBulleted, R.string.format_bullets, lineStyle == LineStyle.Bullet) { onLineStyle(LineStyle.Bullet) }
                    ToolbarToggle(Icons.Outlined.FormatListNumbered, R.string.format_numbers, lineStyle == LineStyle.Numbered) { onLineStyle(LineStyle.Numbered) }
                }
            }
        }
    }
}

@Composable
private fun ToolbarToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, active: Boolean, onClick: () -> Unit) {
    IconToggleButton(checked = active, onCheckedChange = { onClick() }) {
        Icon(icon, contentDescription = stringResource(label))
    }
}

@Composable
private fun StyleButton(label: Int, active: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            stringResource(label),
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
