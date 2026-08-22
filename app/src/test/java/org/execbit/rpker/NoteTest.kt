package org.execbit.rpker

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.execbit.rpker.rpk.MarkdownDocument
import org.execbit.rpker.rpk.VelaBlock
import org.execbit.rpker.rpk.VelaSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTest {
    @Test
    fun previewUsesFirstTwoNonEmptyLines() {
        assertEquals(
            "# Heading\nFirst paragraph",
            notePreview("# Heading\n\nFirst paragraph\nThird line"),
        )
    }

    @Test
    fun movingItemChangesItsPositionWithoutLosingItems() {
        assertEquals(listOf("b", "c", "a"), listOf("a", "b", "c").moved(0, 2))
        assertEquals(listOf("c", "a", "b"), listOf("a", "b", "c").moved(2, 0))
    }

    @Test
    fun annotatedPreviewAppliesVelaBlockAndSpanStyles() {
        val preview = MarkdownDocument(
            html = "",
            blocks = listOf(
                VelaBlock("heading1", listOf(VelaSpan("Heading", "plain"))),
                VelaBlock(
                    "paragraph",
                    listOf(
                        VelaSpan("bold", "strong"),
                        VelaSpan(" italic", "emphasis"),
                        VelaSpan(" gone", "deleted"),
                        VelaSpan(" code", "code"),
                        VelaSpan(" link", "link"),
                    ),
                ),
            ),
        ).toAnnotatedString()

        assertEquals("Heading\nbold italic gone code link", preview.text)
        assertTrue(preview.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(preview.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(preview.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough })
        assertTrue(preview.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
        assertTrue(preview.spanStyles.any { it.item.textDecoration == TextDecoration.Underline })
    }
}
