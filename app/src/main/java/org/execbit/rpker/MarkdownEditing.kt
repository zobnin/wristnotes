package org.execbit.rpker

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal enum class MarkdownFormat {
    HEADING,
    BOLD,
    ITALIC,
    STRIKETHROUGH,
    INLINE_CODE,
    BULLET_LIST,
    NUMBERED_LIST,
    QUOTE,
    HORIZONTAL_RULE,
}

internal fun TextFieldValue.applyMarkdownFormat(format: MarkdownFormat): TextFieldValue = when (format) {
    MarkdownFormat.HEADING -> prefixSelectedLines("# ")
    MarkdownFormat.BOLD -> wrapSelection("**", "**")
    MarkdownFormat.ITALIC -> wrapSelection("*", "*")
    MarkdownFormat.STRIKETHROUGH -> wrapSelection("~~", "~~")
    MarkdownFormat.INLINE_CODE -> wrapSelection("`", "`")
    MarkdownFormat.BULLET_LIST -> prefixSelectedLines("- ")
    MarkdownFormat.NUMBERED_LIST -> prefixSelectedLines("1. ")
    MarkdownFormat.QUOTE -> prefixSelectedLines("> ")
    MarkdownFormat.HORIZONTAL_RULE -> insertBlock("---")
}

internal fun TextFieldState.applyMarkdownFormat(format: MarkdownFormat) {
    val updated = TextFieldValue(text.toString(), selection).applyMarkdownFormat(format)
    edit {
        replace(0, length, updated.text)
        selection = updated.selection
    }
}

internal fun shouldCapitalizeMarkdownContent(
    text: CharSequence,
    selection: TextRange,
): Boolean {
    if (!selection.collapsed) return false

    val lineBeforeCursor = text
        .subSequence(0, selection.start)
        .toString()
        .substringAfterLast('\n')
    val prefix = markdownLinePrefixes.firstOrNull(lineBeforeCursor::startsWith) ?: return false
    return lineBeforeCursor
        .removePrefix(prefix)
        .none(Char::isWhitespace)
}

private val markdownLinePrefixes = setOf("# ", "- ", "1. ", "> ")

private fun TextFieldValue.wrapSelection(opening: String, closing: String): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val selected = text.substring(start, end)
    val replacement = opening + selected + closing
    val updatedSelection = if (selection.collapsed) {
        TextRange(start + opening.length)
    } else {
        TextRange(start + opening.length, end + opening.length)
    }
    return copy(
        text = text.replaceRange(start, end, replacement),
        selection = updatedSelection,
        composition = null,
    )
}

private fun TextFieldValue.prefixSelectedLines(prefix: String): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val firstLineStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { index ->
        if (index < 0 || start == 0) 0 else index + 1
    }
    val insertionPoints = buildList {
        add(firstLineStart)
        for (index in firstLineStart until end) {
            if (text[index] == '\n' && index + 1 < end) add(index + 1)
        }
    }
    var updatedText = text
    insertionPoints.asReversed().forEach { index ->
        updatedText = updatedText.substring(0, index) + prefix + updatedText.substring(index)
    }
    val updatedSelection = if (selection.collapsed) {
        TextRange(start + prefix.length)
    } else {
        TextRange(
            start + insertionPoints.count { it <= start } * prefix.length,
            end + insertionPoints.count { it < end } * prefix.length,
        )
    }
    return copy(text = updatedText, selection = updatedSelection, composition = null)
}

private fun TextFieldValue.insertBlock(block: String): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val leadingNewline = start > 0 && text[start - 1] != '\n'
    val trailingNewline = end < text.length && text[end] != '\n'
    val replacement = buildString {
        if (leadingNewline) append('\n')
        append(block)
        if (trailingNewline) append('\n')
    }
    return copy(
        text = text.replaceRange(start, end, replacement),
        selection = TextRange(start + replacement.length),
        composition = null,
    )
}
