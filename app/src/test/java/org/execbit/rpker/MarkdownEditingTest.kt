package org.execbit.rpker

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownEditingTest {
    @Test
    fun wrapsSelectionAndKeepsItSelected() {
        val result = TextFieldValue("abcd", TextRange(1, 3)).applyMarkdownFormat(MarkdownFormat.BOLD)

        assertEquals("a**bc**d", result.text)
        assertEquals(TextRange(3, 5), result.selection)
    }

    @Test
    fun insertsPairedMarkersAroundCursor() {
        val result = TextFieldValue("text", TextRange(2)).applyMarkdownFormat(MarkdownFormat.ITALIC)

        assertEquals("te**xt", result.text)
        assertEquals(TextRange(3), result.selection)
    }

    @Test
    fun prefixesEverySelectedLine() {
        val result = TextFieldValue("one\ntwo", TextRange(1, 7))
            .applyMarkdownFormat(MarkdownFormat.BULLET_LIST)

        assertEquals("- one\n- two", result.text)
    }

    @Test
    fun requestsCapitalizationAfterMarkdownLinePrefixes() {
        val formats = listOf(
            MarkdownFormat.HEADING,
            MarkdownFormat.BULLET_LIST,
            MarkdownFormat.NUMBERED_LIST,
            MarkdownFormat.QUOTE,
        )

        formats.forEach { format ->
            val result = TextFieldValue("", TextRange.Zero).applyMarkdownFormat(format)

            assertEquals(
                "Expected capitalization after $format",
                true,
                shouldCapitalizeMarkdownContent(result.text, result.selection),
            )
        }
    }

    @Test
    fun keepsMarkdownCapitalizationModeDuringFirstWord() {
        assertEquals(
            true,
            shouldCapitalizeMarkdownContent("# H", TextRange(3)),
        )
    }

    @Test
    fun stopsRequestingMarkdownCapitalizationAfterFirstWord() {
        assertEquals(
            false,
            shouldCapitalizeMarkdownContent("# Heading ", TextRange(10)),
        )
    }

}
