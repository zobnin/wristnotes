package org.execbit.rpker

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.execbit.rpker.rpk.MarkdownRenderer
import org.execbit.rpker.rpk.MarkdownStrings
import org.execbit.rpker.rpk.RpkBuilder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class RpkBuilderInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun buildsSignedRpkWithExactUnicodeTextAndIncreasingVersion() {
        val firstNote = "# Привет, VelaOS!\n\nКавычки: **\"тест\"**; slash: `\\`; emoji: 🚀"
        val secondNote = "## Вторая заметка\n\nПерелистните экран"
        val first = RpkBuilder(context).build(
            listOf(
                Note(id = "first", markdown = firstNote),
                Note(id = "second", markdown = secondNote),
            )
        )
        val firstBytes = first.file.readBytes()
        assertTrue(firstBytes.containsAscii("RPK Sig Block 42"))

        ZipFile(first.file).use { zip ->
            val manifest = JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).reader().readText())
            assertEquals("org.execbit.rpker", manifest.getString("package"))
            assertEquals("Wrist Notes", manifest.getString("name"))
            assertEquals(first.versionCode, manifest.getInt("versionCode"))
            assertEquals("/common/logo.png", manifest.getString("icon"))

            val iconBytes = zip.getInputStream(requireNotNull(zip.getEntry("common/logo.png"))).readBytes()
            val icon = requireNotNull(BitmapFactory.decodeByteArray(iconBytes, 0, iconBytes.size))
            assertEquals(192, icon.width)
            assertEquals(192, icon.height)
            val iconPixels = IntArray(icon.width * icon.height)
            icon.getPixels(iconPixels, 0, icon.width, 0, 0, icon.width, icon.height)
            assertTrue(iconPixels.toSet().size >= 5)

            val page = zip.getInputStream(zip.getEntry("pages/index/index.js")).reader().readText()
            assertTrue(page.contains("VelaOS!"))
            assertTrue(page.contains("Вторая заметка"))
            assertTrue(page.contains("\"style\":\"strong\""))
            assertTrue(page.contains("\"style\":\"code\""))
            assertFalse(page.contains("__RPKER_NOTES__"))
            assertTrue(page.contains("\"notes-swiper\""))
            assertTrue(page.contains("onNoteChanged"))
            assertTrue(page.contains("currentNumber"))
            assertTrue(page.contains("exitApp"))
            assertTrue(page.contains("this.\$app.exit()"))
            assertTrue(page.contains("width: \"220px\""))
            assertTrue(page.contains("height: \"128px\""))
            assertTrue(page.contains("paddingTop: \"48px\""))
            assertTrue(page.contains("paddingBottom: \"48px\""))
            assertTrue(page.contains("condition: \"screen and (shape:circle)\""))
            assertTrue(page.contains("condition: \"screen and (shape:pill-shaped)\""))
            assertTrue(page.contains("height: \"144px\""))
            assertTrue(page.contains("fontSize: \"60px\""))
            assertTrue(zip.getEntry("META-INF/CERT") != null)
        }

        val second = RpkBuilder(context).build(listOf(Note(id = "only", markdown = "Новая версия")))
        assertTrue(second.versionCode > first.versionCode)
    }

    @Test
    fun convertsMarkdownThroughHtmlToVelaSupportedTextAndSpanStyles() {
        val document = MarkdownRenderer.render(
            """
            # Заголовок

            Обычный **жирный**, *курсив*, ~~зачёркнутый~~, `код` и [ссылка](https://example.com).

            > Цитата

            - пункт

            ```kotlin
            val answer = 42
            ```

            <u>сырой HTML</u>
            """.trimIndent(),
            MarkdownStrings(defaultImageAlt = "image", imageLabel = "Image"),
        )

        assertTrue(document.html.contains("<h1>Заголовок</h1>"))
        assertTrue(document.html.contains("<strong>жирный</strong>"))
        assertTrue(document.html.contains("<em>курсив</em>"))
        assertTrue(document.html.contains("<del>зачёркнутый</del>"))
        assertTrue(document.html.contains("<code>код</code>"))
        assertTrue(document.html.contains("href=\"https://example.com\""))
        assertTrue(document.html.contains("&lt;u&gt;сырой HTML&lt;/u&gt;"))

        assertTrue(document.blocks.any { it.type == "heading1" })
        assertTrue(document.blocks.any { it.type == "quote" })
        assertTrue(document.blocks.any { it.type == "list-item" })
        assertTrue(document.blocks.any { it.type == "code-block" })
        val styles = document.blocks.flatMap { block -> block.segments.map { it.style } }
        assertTrue(styles.any { "strong" in it })
        assertTrue(styles.any { "emphasis" in it })
        assertTrue(styles.any { "deleted" in it })
        assertTrue(styles.any { "code" in it })
        assertTrue(styles.any { "link" in it })
    }

    @Test
    fun preservesSignificantWhitespaceInsideCodeBlocks() {
        val trailingSpaces = "  "
        val document = MarkdownRenderer.render(
            """
            ```text
              first line
                second line$trailingSpaces
            ```
            """.trimIndent(),
            MarkdownStrings(defaultImageAlt = "image", imageLabel = "Image"),
        )

        val codeBlock = document.blocks.single { it.type == "code-block" }
        assertEquals(
            "  first line\n    second line  ",
            codeBlock.segments.joinToString(separator = "") { it.text },
        )
    }

    @Test
    fun markdownPreviewRemovesMarkersAndKeepsInlineFormatting() {
        val preview = MarkdownRenderer.render(
            markdown = "# Heading\n\n**bold** *italic* ~~gone~~ `code` [link](https://example.com)",
            strings = MarkdownStrings(defaultImageAlt = "image", imageLabel = "Image"),
        ).toAnnotatedString()

        assertEquals(
            "Heading\nbold italic gone code link (https://example.com)",
            preview.text,
        )
        assertTrue(preview.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(preview.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(preview.spanStyles.any { it.item.textDecoration == TextDecoration.LineThrough })
        assertTrue(preview.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
        assertTrue(preview.spanStyles.any { it.item.textDecoration == TextDecoration.Underline })
    }

    @Test
    fun gadgetbridgeBroadcastUsesNarrowFileProviderGrant() {
        val result = RpkBuilder(context).build(listOf(Note(id = "broadcast", markdown = "Intent test")))
        val uri = rpkFileUri(context, result.file)
        val intent = GadgetbridgeBroadcastInstaller.createInstallAppIntent(uri, "AA:BB:CC:DD:EE:FF")

        assertEquals(
            "nodomain.freeyourgadget.gadgetbridge.command.INSTALL_APP",
            intent.action,
        )
        assertEquals(null, intent.`package`)
        assertEquals(null, intent.data)
        assertEquals("AA:BB:CC:DD:EE:FF", intent.getStringExtra("device"))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val streamUri = requireNotNull(
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java),
        )
        assertEquals(streamUri, intent.clipData?.getItemAt(0)?.uri)
        assertEquals("content", streamUri.scheme)
        assertTrue(requireNotNull(streamUri.lastPathSegment).endsWith(".rpk"))
        context.contentResolver.openInputStream(streamUri).use { input ->
            assertTrue(requireNotNull(input).readBytes().isNotEmpty())
        }
    }

    @Test
    fun legacyGadgetbridgeActivityIntentUsesNarrowFileProviderGrant() {
        val result = RpkBuilder(context).build(listOf(Note(id = "intent", markdown = "Intent test")))
        val intent = GadgetbridgeActivityInstaller.createIntent(context, result.file)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("application/zip", intent.type)
        assertEquals("nodomain.freeyourgadget.gadgetbridge", intent.component?.packageName)
        assertEquals(
            "nodomain.freeyourgadget.gadgetbridge.activities.install.FileInstallerActivity",
            intent.component?.className,
        )
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals("content", intent.data?.scheme)
        context.contentResolver.openInputStream(requireNotNull(intent.data)).use { input ->
            assertTrue(requireNotNull(input).readBytes().isNotEmpty())
        }
    }

    @Test
    fun noteStorePreservesOrderAndMarkdown() {
        val preferencesName = "note_store_test_${System.nanoTime()}"
        try {
            val notes = listOf(
                Note(id = "one", markdown = "# First\n\nText"),
                Note(id = "two", markdown = "Вторая 🚀"),
            )
            NoteStore(context, preferencesName).save(notes)

            assertEquals(notes, NoteStore(context, preferencesName).load())
        } finally {
            context.deleteSharedPreferences(preferencesName)
        }
    }

    @Test
    fun gadgetbridgeInstallSettingsDefaultToActivityAndPersistSelection() {
        val preferencesName = "install_settings_test_${System.nanoTime()}"
        try {
            val settings = GadgetbridgeInstallSettings(context, preferencesName)
            assertEquals(GadgetbridgeInstallMethod.ACTIVITY, settings.load())

            settings.save(GadgetbridgeInstallMethod.BROADCAST)

            assertEquals(
                GadgetbridgeInstallMethod.BROADCAST,
                GadgetbridgeInstallSettings(context, preferencesName).load(),
            )
        } finally {
            context.deleteSharedPreferences(preferencesName)
        }
    }

    private fun ByteArray.containsAscii(value: String): Boolean {
        val needle = value.toByteArray(Charsets.US_ASCII)
        return indices.any { offset ->
            offset + needle.size <= size && needle.indices.all { this[offset + it] == needle[it] }
        }
    }
}
