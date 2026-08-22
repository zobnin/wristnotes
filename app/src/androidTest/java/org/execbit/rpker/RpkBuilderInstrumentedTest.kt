package org.execbit.rpker

import android.content.Intent
import android.graphics.BitmapFactory
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
        val markdown = "# Привет, VelaOS!\n\nКавычки: **\"тест\"**; slash: `\\`; emoji: 🚀"
        val first = RpkBuilder(context).build(markdown)
        val firstBytes = first.file.readBytes()
        assertTrue(firstBytes.containsAscii("RPK Sig Block 42"))

        ZipFile(first.file).use { zip ->
            val manifest = JSONObject(zip.getInputStream(zip.getEntry("manifest.json")).reader().readText())
            assertEquals("org.execbit.rpker", manifest.getString("package"))
            assertEquals("Wrist Note", manifest.getString("name"))
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
            assertTrue(page.contains("\"style\":\"strong\""))
            assertTrue(page.contains("\"style\":\"code\""))
            assertFalse(page.contains("__RPKER_MARKDOWN_BLOCKS__"))
            assertTrue(page.contains("condition: \"screen and (shape:circle)\""))
            assertTrue(page.contains("condition: \"screen and (shape:pill-shaped)\""))
            assertTrue(page.contains("paddingTop: \"144px\""))
            assertTrue(page.contains("paddingBottom: \"144px\""))
            assertTrue(page.contains("fontSize: \"60px\""))
            assertTrue(zip.getEntry("META-INF/CERT") != null)
        }

        val second = RpkBuilder(context).build("Вторая версия")
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
    fun gadgetbridgeIntentUsesNarrowFileProviderGrant() {
        val result = RpkBuilder(context).build("Intent test")
        val intent = GadgetbridgeInstaller.createIntent(context, result.file)

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

    private fun ByteArray.containsAscii(value: String): Boolean {
        val needle = value.toByteArray(Charsets.US_ASCII)
        return indices.any { offset ->
            offset + needle.size <= size && needle.indices.all { this[offset + it] == needle[it] }
        }
    }
}
