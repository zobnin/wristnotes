package org.execbit.rpker

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.execbit.rpker.rpk.MarkdownDocument

internal fun MarkdownDocument.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    blocks.forEachIndexed { blockIndex, block ->
        if (blockIndex > 0) append('\n')
        val blockStart = length

        block.segments.forEach { segment ->
            val segmentStart = length
            append(segment.text)
            segment.style.split(' ').forEach { style ->
                previewSpanStyle(style)?.let { addStyle(it, segmentStart, length) }
            }
        }

        previewBlockStyle(block.type)?.let { addStyle(it, blockStart, length) }
    }
}

private fun previewSpanStyle(style: String): SpanStyle? = when (style) {
    "strong" -> SpanStyle(fontWeight = FontWeight.Bold)
    "emphasis" -> SpanStyle(fontStyle = FontStyle.Italic)
    "deleted" -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    "code" -> SpanStyle(fontFamily = FontFamily.Monospace)
    "link" -> SpanStyle(textDecoration = TextDecoration.Underline)
    else -> null
}

private fun previewBlockStyle(type: String): SpanStyle? = when (type) {
    "heading1", "heading2", "heading3" -> SpanStyle(fontWeight = FontWeight.Bold)
    "quote" -> SpanStyle(fontStyle = FontStyle.Italic)
    "code-block", "qrcode", "barcode" -> SpanStyle(fontFamily = FontFamily.Monospace)
    else -> null
}
