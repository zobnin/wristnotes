package org.execbit.rpker

import android.content.ActivityNotFoundException
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.execbit.rpker.rpk.RpkBuilder
import org.execbit.rpker.rpk.RpkBuildException
import org.execbit.rpker.ui.theme.RPKerTheme

private const val DRAFT_SAVE_DELAY_MILLIS = 500L

class MainActivity : ComponentActivity() {
    private val editorTextStore by lazy { EditorTextStore(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RPKerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RpkEditor(
                        initialText = editorTextStore.load(),
                        saveText = editorTextStore::save,
                        buildAndInstall = { text ->
                            val result = withContext(Dispatchers.IO) {
                                RpkBuilder(applicationContext).build(text)
                            }
                            GadgetbridgeInstaller.open(this@MainActivity, result.file)
                            getString(R.string.status_rpk_sent, result.versionName)
                        }
                    )
                }
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
@OptIn(ExperimentalLayoutApi::class)
private fun RpkEditor(
    initialText: String,
    saveText: (String) -> Unit,
    buildAndInstall: suspend (String) -> String,
) {
    var input by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(initialText.length),
            )
        )
    }
    var isBuilding by remember { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var editorFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val gadgetbridgeNotFound = stringResource(R.string.error_gadgetbridge_not_found)
    val gadgetbridgeSecurityError = stringResource(R.string.error_gadgetbridge_security)
    val buildFailed = stringResource(R.string.error_build_failed)
    val imeVisible = WindowInsets.isImeVisible
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentInputText by rememberUpdatedState(input.text)
    val currentSaveText by rememberUpdatedState(saveText)

    LaunchedEffect(input.text) {
        delay(DRAFT_SAVE_DELAY_MILLIS)
        currentSaveText(input.text)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                currentSaveText(currentInputText)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            currentSaveText(currentInputText)
        }
    }

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
                value = input,
                onValueChange = {
                    input = it
                    status = null
                    isError = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = 180.dp)
                    .onFocusChanged { editorFocused = it.isFocused },
                label = { Text(stringResource(R.string.markdown_label)) },
                placeholder = { Text(stringResource(R.string.markdown_placeholder)) },
                enabled = !isBuilding,
            )
            status?.let {
                Text(
                    text = it,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = {
                    isBuilding = true
                    status = null
                    isError = false
                    scope.launch {
                        try {
                            status = buildAndInstall(input.text)
                        } catch (_: ActivityNotFoundException) {
                            status = gadgetbridgeNotFound
                            isError = true
                        } catch (_: SecurityException) {
                            status = gadgetbridgeSecurityError
                            isError = true
                        } catch (error: RpkBuildException) {
                            status = error.message
                            isError = true
                        } catch (_: Exception) {
                            status = buildFailed
                            isError = true
                        } finally {
                            isBuilding = false
                        }
                    }
                },
                enabled = input.text.isNotBlank() && !isBuilding,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isBuilding) {
                        CircularProgressIndicator(
                            modifier = Modifier.heightIn(max = 20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(stringResource(if (isBuilding) R.string.button_building else R.string.button_install))
                }
            }
        }
        if (editorFocused && imeVisible && !isBuilding) {
            MarkdownToolbar { format ->
                val updated = input.applyMarkdownFormat(format)
                input = updated
                status = null
                isError = false
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
