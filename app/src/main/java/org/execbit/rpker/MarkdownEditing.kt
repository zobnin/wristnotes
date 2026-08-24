package org.execbit.rpker

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.byValue
import androidx.compose.foundation.text.input.then
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
    MarkdownFormat.HEADING -> increaseHeadingLevel()
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

private val markdownAtomicBackspaceTransformation = InputTransformation {
    val previous = TextFieldValue(originalText.toString(), originalSelection)
    val proposed = TextFieldValue(asCharSequence().toString(), selection)
    val marker = atomicMarkdownMarkerForBackspace(previous, proposed) ?: return@InputTransformation

    replace(marker.start, marker.end - 1, "")
    placeCursorBeforeCharAt(marker.start)
}

internal val markdownInputTransformation =
    InputTransformation.byValue { current, proposed ->
        continueMarkdownListOnNewline(current, proposed)
    }.then(markdownAtomicBackspaceTransformation)

internal fun continueMarkdownListOnNewline(
    current: CharSequence,
    proposed: CharSequence,
): CharSequence {
    if (proposed.length != current.length + 1) return proposed

    val insertedAt = current.indices.firstOrNull { current[it] != proposed[it] } ?: current.length
    if (proposed[insertedAt] != '\n') return proposed
    if (!proposed.removeRange(insertedAt, insertedAt + 1).contentEquals(current)) return proposed

    val previousLine = proposed
        .subSequence(0, insertedAt)
        .toString()
        .substringAfterLast('\n')
    val marker = markdownListPrefixes.firstOrNull(previousLine::startsWith) ?: return proposed
    return proposed.replaceRange(insertedAt + 1, insertedAt + 1, marker)
}

internal fun TextFieldValue.applyAtomicMarkdownBackspace(previous: TextFieldValue): TextFieldValue {
    val marker = atomicMarkdownMarkerForBackspace(previous, this) ?: return this
    return copy(
        text = text.removeRange(marker.start, marker.end - 1),
        selection = TextRange(marker.start),
        composition = null,
    )
}

private fun atomicMarkdownMarkerForBackspace(
    previous: TextFieldValue,
    proposed: TextFieldValue,
): TextRange? {
    if (!previous.selection.collapsed || !proposed.selection.collapsed) return null
    if (previous.text.length != proposed.text.length + 1) return null

    val deletedAt = proposed.selection.start
    if (previous.selection.start != deletedAt + 1) return null
    if (!previous.text.removeRange(deletedAt, deletedAt + 1).contentEquals(proposed.text)) return null

    return atomicMarkdownMarkerAt(previous.text, deletedAt)
}

private fun atomicMarkdownMarkerAt(text: String, index: Int): TextRange? {
    val lineStart = text.lastIndexOf('\n', index - 1) + 1
    markdownBlockMarkerEnd(text, lineStart)?.let { markerEnd ->
        if (index in lineStart until markerEnd) return TextRange(lineStart, markerEnd)
    }

    val marker = text.getOrNull(index)?.takeIf { it == '*' || it == '~' } ?: return null
    var runStart = index
    while (runStart > 0 && text[runStart - 1] == marker) runStart--
    var runEnd = index + 1
    while (runEnd < text.length && text[runEnd] == marker) runEnd++
    if (runEnd - runStart < 2) return null

    val pairStart = runStart + ((index - runStart) / 2) * 2
    return if (pairStart + 2 <= runEnd) {
        TextRange(pairStart, pairStart + 2)
    } else {
        TextRange(runEnd - 2, runEnd)
    }
}

private fun markdownBlockMarkerEnd(text: String, lineStart: Int): Int? {
    if (text.startsWith("---", lineStart)) return lineStart + 3
    if (text.startsWith("- ", lineStart) || text.startsWith("> ", lineStart)) return lineStart + 2

    var markerEnd = lineStart
    while (markerEnd < text.length && markerEnd - lineStart < 6 && text[markerEnd] == '#') {
        markerEnd++
    }
    if (markerEnd > lineStart && text.getOrNull(markerEnd) == ' ') return markerEnd + 1

    markerEnd = lineStart
    while (text.getOrNull(markerEnd)?.isDigit() == true) markerEnd++
    if (markerEnd > lineStart && text.startsWith(". ", markerEnd)) return markerEnd + 2

    return null
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

private val markdownListPrefixes = listOf("- ", "1. ")
private val markdownHeadingPrefixes = (1..6).map { level -> "#".repeat(level) + " " }
private val markdownLinePrefixes = markdownListPrefixes + markdownHeadingPrefixes + "> "

private fun TextFieldValue.increaseHeadingLevel(): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val firstLineStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0)).let { index ->
        if (index < 0 || start == 0) 0 else index + 1
    }
    val lineStarts = buildList {
        add(firstLineStart)
        for (index in firstLineStart until end) {
            if (text[index] == '\n' && index + 1 < end) add(index + 1)
        }
    }
    val insertions = lineStarts.mapNotNull { lineStart ->
        val headingPrefix = markdownHeadingPrefixes.firstOrNull { prefix ->
            text.startsWith(prefix, lineStart)
        }
        when {
            headingPrefix == null -> lineStart to "# "
            headingPrefix.length <= 6 -> lineStart + headingPrefix.length - 1 to "#"
            else -> null
        }
    }
    if (insertions.isEmpty()) return this

    var updatedText = text
    insertions.asReversed().forEach { (index, insertion) ->
        updatedText = updatedText.substring(0, index) + insertion + updatedText.substring(index)
    }
    val updatedSelection = if (selection.collapsed) {
        TextRange(start + insertions.filter { it.first <= start }.sumOf { it.second.length })
    } else {
        TextRange(
            start + insertions.filter { it.first <= start }.sumOf { it.second.length },
            end + insertions.filter { it.first < end }.sumOf { it.second.length },
        )
    }
    return copy(text = updatedText, selection = updatedSelection, composition = null)
}

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
