package org.execbit.rpker.rpk

import org.commonmark.Extension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.ArrayDeque

internal data class MarkdownDocument(
    val html: String,
    val blocks: List<VelaBlock>,
) {
    fun blocksJson(): String = JSONArray().apply {
        blocks.forEach { block ->
            put(JSONObject().apply {
                put("type", block.type)
                put("segments", JSONArray().apply {
                    block.segments.forEach { segment ->
                        put(JSONObject().put("style", segment.style).put("text", segment.text))
                    }
                })
                block.value?.let { put("value", it) }
            })
        }
    }.toString().replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
}

internal data class VelaBlock(
    val type: String,
    val segments: List<VelaSpan>,
    val value: String? = null,
)

internal data class VelaSpan(
    val text: String,
    val style: String,
)

internal data class MarkdownStrings(
    val defaultImageAlt: String,
    val imageLabel: String,
)

/**
 * Converts CommonMark to HTML first, then maps that controlled HTML to the text/span subset
 * implemented by VelaOS. Raw HTML in user input is escaped by CommonMark and never becomes UI.
 */
internal object MarkdownRenderer {
    private val extensions: List<Extension> = listOf(StrikethroughExtension.create())
    private val parser = Parser.builder().extensions(extensions).build()
    private val htmlRenderer = HtmlRenderer.builder()
        .extensions(extensions)
        .escapeHtml(true)
        .build()

    fun render(markdown: String, strings: MarkdownStrings): MarkdownDocument {
        val html = htmlRenderer.render(parser.parse(markdown))
        val blocks = VelaHtmlParser(html, strings).parse()
        return MarkdownDocument(html, blocks)
    }
}

private class VelaHtmlParser(
    private val html: String,
    private val strings: MarkdownStrings,
) {
    private data class InlineStyle(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val deleted: Boolean = false,
        val code: Boolean = false,
        val link: Boolean = false,
    ) {
        fun className(): String = buildList {
            if (bold) add("strong")
            if (italic) add("emphasis")
            if (deleted) add("deleted")
            if (code) add("code")
            if (link) add("link")
        }.joinToString(" ").ifEmpty { "plain" }
    }

    private data class MutableSpan(var text: String, val style: InlineStyle)
    private data class BlockOwner(val tag: String, val ownsBlock: Boolean)
    private data class ListContext(val ordered: Boolean, var nextNumber: Int)

    private val blocks = mutableListOf<VelaBlock>()
    private val spans = mutableListOf<MutableSpan>()
    private val styleStack = ArrayDeque<InlineStyle>()
    private val blockOwners = ArrayDeque<BlockOwner>()
    private val links = ArrayDeque<String>()
    private val lists = ArrayDeque<ListContext>()
    private var style = InlineStyle()
    private var blockType: String? = null
    private var quoteDepth = 0
    private var preDepth = 0

    fun parse(): List<VelaBlock> {
        val xml = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(StringReader("<document>$html</document>"))
        }
        var event = xml.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> startTag(xml)
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> appendRaw(xml.text.orEmpty())
                XmlPullParser.END_TAG -> endTag(xml.name.lowercase())
            }
            event = xml.next()
        }
        finishBlock()
        return blocks
    }

    private fun startTag(xml: XmlPullParser) {
        val tag = xml.name.lowercase()
        when (tag) {
            "p" -> startOwnedBlock(tag, defaultBlockType())
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                finishBlock()
                startOwnedBlock(tag, when (tag) {
                    "h1" -> "heading1"
                    "h2" -> "heading2"
                    else -> "heading3"
                })
            }
            "blockquote" -> quoteDepth++
            "ul" -> lists.addLast(ListContext(ordered = false, nextNumber = 1))
            "ol" -> lists.addLast(ListContext(
                ordered = true,
                nextNumber = xml.getAttributeValue(null, "start")?.toIntOrNull() ?: 1,
            ))
            "li" -> {
                finishBlock()
                startOwnedBlock(tag, if (quoteDepth > 0) "quote" else "list-item")
                val list = lists.peekLast()
                val marker = if (list?.ordered == true) "${list.nextNumber++}. " else "• "
                append("  ".repeat((lists.size - 1).coerceAtLeast(0)) + marker, InlineStyle(bold = true))
            }
            "pre" -> {
                finishBlock()
                preDepth++
                startOwnedBlock(tag, "code-block")
            }
            "strong", "b" -> pushStyle { copy(bold = true) }
            "em", "i" -> pushStyle { copy(italic = true) }
            "del", "s", "strike" -> pushStyle { copy(deleted = true) }
            "code" -> {
                if (preDepth > 0) {
                    fencedComponentType(xml.getAttributeValue(null, "class"))?.let { componentType ->
                        blockType = componentType
                    }
                }
                pushStyle { copy(code = true) }
            }
            "a" -> {
                links.addLast(xml.getAttributeValue(null, "href").orEmpty())
                pushStyle { copy(link = true) }
            }
            "img" -> {
                ensureBlock()
                val alt = xml.getAttributeValue(null, "alt").orEmpty().ifBlank { strings.defaultImageAlt }
                val source = xml.getAttributeValue(null, "src").orEmpty()
                val description = "${strings.imageLabel}: $alt"
                val imageText = if (source.isBlank()) "[$description]" else "[$description] ($source)"
                append(imageText, InlineStyle(italic = true, link = source.isNotBlank()))
            }
            "br" -> {
                ensureBlock()
                append("\n")
            }
            "hr" -> {
                finishBlock()
                blockType = "rule"
                append("────────")
                finishBlock()
            }
        }
    }

    private fun endTag(tag: String) {
        when (tag) {
            "p", "h1", "h2", "h3", "h4", "h5", "h6", "li", "pre" -> {
                val owner = blockOwners.removeLast()
                check(owner.tag == tag)
                if (owner.ownsBlock) finishBlock()
                if (tag == "pre") preDepth--
            }
            "blockquote" -> quoteDepth--
            "ul", "ol" -> lists.removeLast()
            "strong", "b", "em", "i", "del", "s", "strike", "code" -> popStyle()
            "a" -> {
                val href = links.removeLast()
                val linkStyle = style
                popStyle()
                if (href.isNotBlank()) append(" ($href)", linkStyle)
            }
        }
    }

    private fun startOwnedBlock(tag: String, type: String) {
        val owns = blockType == null
        if (owns) {
            blockType = type
            if (quoteDepth > 0) {
                blockType = "quote"
                append("│ ", InlineStyle(bold = true))
            }
        }
        blockOwners.addLast(BlockOwner(tag, owns))
    }

    private fun defaultBlockType(): String = if (quoteDepth > 0) "quote" else "paragraph"

    private fun ensureBlock() {
        if (blockType == null) {
            blockType = defaultBlockType()
            if (quoteDepth > 0) append("│ ", InlineStyle(bold = true))
        }
    }

    private fun appendRaw(raw: String) {
        if (raw.isEmpty()) return
        val value = if (preDepth > 0) raw else raw.replace(Regex("\\s+"), " ")
        if (blockType == null && value.isBlank()) return
        ensureBlock()
        append(value)
    }

    private fun append(value: String, valueStyle: InlineStyle = style) {
        if (value.isEmpty()) return
        val last = spans.lastOrNull()
        if (last?.style == valueStyle) {
            last.text += value
        } else {
            spans += MutableSpan(value, valueStyle)
        }
    }

    private fun pushStyle(change: InlineStyle.() -> InlineStyle) {
        styleStack.addLast(style)
        style = style.change()
    }

    private fun popStyle() {
        style = styleStack.removeLast()
    }

    private fun finishBlock() {
        val type = blockType ?: return
        if (type in fencedBlockTypes) {
            spans.lastOrNull()?.let { last ->
                if (last.text.endsWith('\n')) last.text = last.text.dropLast(1)
            }
        } else {
            while (spans.isNotEmpty() && spans.first().text.isBlank()) spans.removeAt(0)
            while (spans.isNotEmpty() && spans.last().text.isBlank()) spans.removeAt(spans.lastIndex)
            if (spans.isNotEmpty()) {
                spans.first().text = spans.first().text.trimStart()
                spans.last().text = spans.last().text.trimEnd()
            }
        }
        val rendered = spans
            .filter { it.text.isNotEmpty() }
            .map { VelaSpan(it.text, it.style.className()) }
        if (rendered.isNotEmpty()) {
            blocks += VelaBlock(
                type = type,
                segments = rendered,
                value = if (type in nativeCodeBlockTypes) {
                    rendered.joinToString(separator = "") { it.text }
                } else {
                    null
                },
            )
        }
        spans.clear()
        blockType = null
    }

    private fun fencedComponentType(className: String?): String? {
        val language = className
            ?.split(Regex("\\s+"))
            ?.firstOrNull { it.startsWith("language-") }
            ?.removePrefix("language-")
            ?.lowercase()
        return when (language) {
            "qrcode" -> "qrcode"
            "barcode" -> "barcode"
            else -> null
        }
    }

    private companion object {
        val nativeCodeBlockTypes = setOf("qrcode", "barcode")
        val fencedBlockTypes = nativeCodeBlockTypes + "code-block"
    }
}
