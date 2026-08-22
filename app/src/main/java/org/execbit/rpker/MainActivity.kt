package org.execbit.rpker

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.execbit.rpker.rpk.RpkBuildException
import org.execbit.rpker.rpk.RpkBuilder
import org.execbit.rpker.rpk.MarkdownRenderer
import org.execbit.rpker.rpk.MarkdownStrings
import org.execbit.rpker.ui.theme.RPKerTheme

class MainActivity : ComponentActivity() {
    private val viewModel: WristNoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RPKerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WristNoteApp(
                        viewModel = viewModel,
                        buildAndInstall = { notes ->
                            val result = withContext(Dispatchers.IO) {
                                RpkBuilder(applicationContext).build(notes)
                            }
                            GadgetbridgeInstaller.open(this@MainActivity, result.file)
                            getString(R.string.status_rpk_sent, result.versionName)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WristNoteApp(
    viewModel: WristNoteViewModel,
    buildAndInstall: suspend (List<Note>) -> String,
) {
    val editor = viewModel.editor
    val context = LocalContext.current
    var isBuilding by remember { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val gadgetbridgeNotFound = stringResource(R.string.error_gadgetbridge_not_found)
    val gadgetbridgeSecurityError = stringResource(R.string.error_gadgetbridge_security)
    val buildFailed = stringResource(R.string.error_build_failed)

    BackHandler(enabled = editor != null) {
        viewModel.closeEditor()
    }

    if (editor != null) {
        NoteEditor(
            input = editor.input,
            onSave = viewModel::saveEditor,
        )
    } else {
        NotesScreen(
            notes = viewModel.notes,
            isBuilding = isBuilding,
            status = status,
            isError = isError,
            onAdd = {
                status = null
                isError = false
                viewModel.addNote()
            },
            onEdit = { noteId ->
                status = null
                isError = false
                viewModel.editNote(noteId)
            },
            onDelete = { noteId ->
                status = null
                isError = false
                viewModel.deleteNote(noteId)
            },
            onMove = { fromIndex, toIndex ->
                status = null
                isError = false
                viewModel.moveNote(fromIndex, toIndex)
            },
            onSync = {
                isBuilding = true
                status = null
                isError = false
                val notesToSend = viewModel.notes.toList()
                scope.launch {
                    try {
                        val message = buildAndInstall(notesToSend)
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    } catch (_: ActivityNotFoundException) {
                        status = gadgetbridgeNotFound
                        isError = true
                    } catch (_: SecurityException) {
                        status = gadgetbridgeSecurityError
                        isError = true
                    } catch (error: RpkBuildException) {
                        status = error.message ?: buildFailed
                        isError = true
                    } catch (_: Exception) {
                        status = buildFailed
                        isError = true
                    } finally {
                        isBuilding = false
                    }
                }
            },
        )
    }
}

@Composable
private fun NotesScreen(
    notes: List<Note>,
    isBuilding: Boolean,
    status: String?,
    isError: Boolean,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onSync: () -> Unit,
) {
    var pendingDeletion by remember { mutableStateOf<Note?>(null) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val dragState = rememberNotesDragState(listState, onMove)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp),
            style = MaterialTheme.typography.headlineLarge,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(notes, key = { _, note -> note.id }) { index, note ->
                val isDragging = dragState.draggedItemIndex == index
                NoteRow(
                    note = note,
                    enabled = !isBuilding,
                    isDragging = isDragging,
                    draggedOffset = if (isDragging) dragState.draggedItemOffset else 0f,
                    onDragStart = { dragState.start(index) },
                    onDrag = { deltaY ->
                        val scrollAmount = dragState.dragBy(deltaY)
                        if (scrollAmount != 0f && autoScrollJob?.isActive != true) {
                            autoScrollJob = scope.launch { listState.scrollBy(scrollAmount) }
                        }
                    },
                    onDragEnd = {
                        autoScrollJob?.cancel()
                        autoScrollJob = null
                        dragState.end()
                    },
                    onClick = { onEdit(note.id) },
                    onDelete = { pendingDeletion = note },
                )
            }
        }
        status?.let {
            Text(
                text = it,
                modifier = Modifier.padding(horizontal = 20.dp),
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = onAdd,
                modifier = Modifier.size(64.dp),
                enabled = !isBuilding,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = stringResource(R.string.action_add_note),
                    modifier = Modifier.size(30.dp),
                )
            }
            FilledIconButton(
                onClick = onSync,
                modifier = Modifier.size(64.dp),
                enabled = notes.isNotEmpty() && !isBuilding,
            ) {
                if (isBuilding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_sync),
                        contentDescription = stringResource(R.string.action_sync),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
    }

    pendingDeletion?.let { note ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text(stringResource(R.string.delete_note_title)) },
            text = { Text(stringResource(R.string.delete_note_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(note.id)
                        pendingDeletion = null
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun NoteRow(
    note: Note,
    enabled: Boolean,
    isDragging: Boolean,
    draggedOffset: Float,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val defaultImageAlt = stringResource(R.string.markdown_image_default_alt)
    val imageLabel = stringResource(R.string.markdown_image_label)
    val preview = remember(note.markdown, defaultImageAlt, imageLabel) {
        MarkdownRenderer.render(
            markdown = notePreview(note.markdown),
            strings = MarkdownStrings(
                defaultImageAlt = defaultImageAlt,
                imageLabel = imageLabel,
            ),
        ).toAnnotatedString()
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging) 1f else 0f)
            .graphicsLayer { translationY = draggedOffset }
            .then(if (isDragging) Modifier.shadow(8.dp, MaterialTheme.shapes.medium) else Modifier)
            .pointerInput(note.id, enabled) {
                if (!enabled) return@pointerInput
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDragCancel = onDragEnd,
                    onDragEnd = onDragEnd,
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.y)
                    },
                )
            },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val reorderDescription = stringResource(R.string.action_reorder_note)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = reorderDescription },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_drag_handle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = preview,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            IconButton(onClick = onDelete, enabled = enabled) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.action_delete_note),
                )
            }
        }
    }
}

private data class MarkdownToolbarAction(
    @param:StringRes val symbol: Int,
    @param:StringRes val description: Int,
    val format: MarkdownFormat,
)

private val markdownToolbarActions = listOf(
    MarkdownToolbarAction(R.string.format_heading_symbol, R.string.format_heading, MarkdownFormat.HEADING),
    MarkdownToolbarAction(R.string.format_bold_symbol, R.string.format_bold, MarkdownFormat.BOLD),
    MarkdownToolbarAction(R.string.format_italic_symbol, R.string.format_italic, MarkdownFormat.ITALIC),
    MarkdownToolbarAction(
        R.string.format_strikethrough_symbol,
        R.string.format_strikethrough,
        MarkdownFormat.STRIKETHROUGH,
    ),
    MarkdownToolbarAction(R.string.format_code_symbol, R.string.format_code, MarkdownFormat.INLINE_CODE),
    MarkdownToolbarAction(
        R.string.format_bullet_list_symbol,
        R.string.format_bullet_list,
        MarkdownFormat.BULLET_LIST,
    ),
    MarkdownToolbarAction(
        R.string.format_numbered_list_symbol,
        R.string.format_numbered_list,
        MarkdownFormat.NUMBERED_LIST,
    ),
    MarkdownToolbarAction(R.string.format_quote_symbol, R.string.format_quote, MarkdownFormat.QUOTE),
    MarkdownToolbarAction(
        R.string.format_horizontal_rule_symbol,
        R.string.format_horizontal_rule,
        MarkdownFormat.HORIZONTAL_RULE,
    ),
)

@Composable
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
private fun NoteEditor(
    input: TextFieldState,
    onSave: () -> Unit,
) {
    var editorFocused by remember { mutableStateOf(false) }
    val imeVisible = WindowInsets.isImeVisible

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
            )
            if (!imeVisible) {
                Text(
                    text = stringResource(R.string.editor_description),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            OutlinedTextField(
                state = input,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 180.dp)
                    .onFocusChanged { editorFocused = it.isFocused },
                labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
                label = { Text(stringResource(R.string.markdown_label)) },
                placeholder = { Text(stringResource(R.string.markdown_placeholder)) },
            )
            Button(
                onClick = onSave,
                enabled = input.text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.button_save))
            }
        }
        if (editorFocused && imeVisible) {
            MarkdownToolbar { format ->
                input.applyMarkdownFormat(format)
            }
        }
    }
}

@Composable
private fun MarkdownToolbar(onFormat: (MarkdownFormat) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RectangleShape,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            markdownToolbarActions.forEach { action ->
                val description = stringResource(action.description)
                Surface(
                    onClick = { onFormat(action.format) },
                    modifier = Modifier
                        .sizeIn(minWidth = 44.dp, minHeight = 40.dp)
                        .focusProperties { canFocus = false }
                        .semantics { contentDescription = description },
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.small,
                    tonalElevation = 1.dp,
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(action.symbol),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
