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
    fun repeatedHeadingActionIncreasesHeadingLevel() {
        var value = TextFieldValue("", TextRange.Zero)

        val expectedHeadings = (1..6).map { level -> "#".repeat(level) + " " }
        expectedHeadings.forEach { expected ->
            value = value.applyMarkdownFormat(MarkdownFormat.HEADING)

            assertEquals(expected, value.text)
            assertEquals(TextRange(expected.length), value.selection)
            assertEquals(true, shouldCapitalizeMarkdownContent(value.text, value.selection))
        }
    }

    @Test
    fun headingLevelStopsAtSix() {
        val heading = "###### "
        val value = TextFieldValue(heading, TextRange(heading.length))

        assertEquals(value, value.applyMarkdownFormat(MarkdownFormat.HEADING))
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

    @Test
    fun continuesBulletListAfterNewline() {
        val result = continueMarkdownListOnNewline("- First", "- First\n")

        assertEquals("- First\n- ", result.toString())
    }

    @Test
    fun continuesNumberedListAfterNewline() {
        val result = continueMarkdownListOnNewline("1. First", "1. First\n")

        assertEquals("1. First\n1. ", result.toString())
    }

    @Test
    fun continuesListWhenNewlineIsInsertedInTheMiddle() {
        val result = continueMarkdownListOnNewline("- First second", "- First\n second")

        assertEquals("- First\n-  second", result.toString())
    }

    @Test
    fun leavesRegularParagraphUnchangedAfterNewline() {
        val proposed = "First\n"

        assertEquals(proposed, continueMarkdownListOnNewline("First", proposed))
    }

    @Test
    fun deletesBlockMarkersAtomicallyWithBackspace() {
        val markers = listOf("# ", "- ", "1. ", "> ", "---", "### ", "12. ")

        markers.forEach { marker ->
            val result = backspace(marker, marker.length)

            assertEquals("Expected $marker to be deleted atomically", "", result.text)
            assertEquals(TextRange.Zero, result.selection)
        }
    }

    @Test
    fun deletesDoubleInlineMarkersAtomically() {
        listOf("**", "~~").forEach { marker ->
            val text = "before ${marker}after"
            val result = backspace(text, "before $marker".length)

            assertEquals("before after", result.text)
            assertEquals(TextRange("before ".length), result.selection)
        }
    }

    @Test
    fun deletesClosingDoubleMarkerAtomically() {
        val result = backspace("~~text~~", "~~text~~".length)

        assertEquals("~~text", result.text)
        assertEquals(TextRange("~~text".length), result.selection)
    }

    @Test
    fun leavesRegularBackspaceUnchanged() {
        val result = backspace("text", "text".length)

        assertEquals("tex", result.text)
        assertEquals(TextRange(3), result.selection)
    }

    @Test
    fun doesNotApplyAtomicBackspaceToForwardDelete() {
        val previous = TextFieldValue("**text", TextRange.Zero)
        val proposed = TextFieldValue("*text", TextRange.Zero)

        assertEquals(proposed, proposed.applyAtomicMarkdownBackspace(previous))
    }

    private fun backspace(text: String, cursor: Int): TextFieldValue {
        val previous = TextFieldValue(text, TextRange(cursor))
        val proposed = TextFieldValue(
            text = text.removeRange(cursor - 1, cursor),
            selection = TextRange(cursor - 1),
        )
        return proposed.applyAtomicMarkdownBackspace(previous)
    }

}
