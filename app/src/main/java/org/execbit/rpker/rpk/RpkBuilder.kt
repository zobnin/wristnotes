package org.execbit.rpker.rpk

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import org.execbit.rpker.Note
import org.execbit.rpker.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max

internal data class RpkBuildResult(
    val file: File,
    val versionCode: Int,
    val versionName: String,
)

internal class RpkBuildException(message: String) : Exception(message)

internal class RpkBuilder(private val context: Context) {
    companion object {
        const val PACKAGE_NAME = "org.execbit.rpker"
        const val OUTPUT_FILE_NAME = "$PACKAGE_NAME.rpk"
        private const val NOTES_MARKER = "\"__RPKER_NOTES__\""
        private const val MAX_TEXT_BYTES = 1_000_000
        private val VERSION_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd.HHmmss").withZone(ZoneOffset.UTC)
    }

    fun build(notes: List<Note>): RpkBuildResult = synchronized(this) {
        if (notes.isEmpty()) throw RpkBuildException(context.getString(R.string.error_no_notes))
        if (notes.any { it.markdown.isBlank() }) {
            throw RpkBuildException(context.getString(R.string.error_empty_note))
        }
        val totalTextBytes = notes.sumOf { it.markdown.toByteArray(Charsets.UTF_8).size.toLong() }
        if (totalTextBytes > MAX_TEXT_BYTES) {
            throw RpkBuildException(context.getString(R.string.error_notes_too_large))
        }

        val now = Instant.now()
        val preferences = context.getSharedPreferences("rpk_versions", Context.MODE_PRIVATE)
        val previous = preferences.getInt("last_version_code", 0)
        val epoch = now.epochSecond.coerceAtMost(Int.MAX_VALUE.toLong() - 1).toInt()
        val versionCode = max(previous + 1, epoch)
        val versionName = VERSION_FORMAT.format(Instant.ofEpochSecond(versionCode.toLong()))

        val comment = JSONObject()
            .put("toolkit", "Wrist Notes Android / aiotpack-compatible")
            .put("timeStamp", now.toString())
            .put("node", "Android")
            .put("platform", "android")
            .put("arch", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
            .put("component", true)
            .toString()
        val markdownStrings = MarkdownStrings(
            defaultImageAlt = context.getString(R.string.markdown_image_default_alt),
            imageLabel = context.getString(R.string.markdown_image_label),
        )
        val renderedNotes = JSONArray().apply {
            notes.forEach { note ->
                val document = MarkdownRenderer.render(note.markdown, markdownStrings)
                put(JSONObject().put("blocks", JSONArray(document.blocksJson())))
            }
        }.toString()
            .replace("\u2028", "\\u2028")
            .replace("\u2029", "\\u2029")

        val payload = linkedMapOf<String, ByteArray>()
        payload["manifest-watch.json"] = renderManifest(
            readAssetText("rpk_template/manifest-watch.json"), versionCode, versionName
        )
        payload["manifest.json"] = renderManifest(
            readAssetText("rpk_template/manifest.json"), versionCode, versionName
        )
        payload["app.js"] = readAssetBytes("rpk_template/app.js")
        payload["pages/index/index.js"] = renderPage(
            readAssetText("rpk_template/pages/index/index.js"), renderedNotes
        ).toByteArray(Charsets.UTF_8)
        payload["common/logo.png"] = readAssetBytes("rpk_template/common/logo.png")
        payload["META-INF/build.txt"] = buildInfo(comment).toByteArray(Charsets.UTF_8)

        val hashJson = JSONObject()
            .put("algorithm", "SHA-256")
            .put("digests", JSONObject().apply {
                payload.forEach { (path, bytes) -> put(path, RpkSigner.sha256(bytes).toHex()) }
            })
            .toString()
            .toByteArray(Charsets.UTF_8)

        val keyMaterial = RpkSigner.keyMaterial(
            readAssetText("rpk_signing/private.pem"),
            readAssetText("rpk_signing/certificate.pem"),
        )
        val unsignedMeta = createZip(listOf(RpkEntry("hash.json", hashJson)), comment)
        val signedMeta = RpkSigner.sign(
            unsignedMeta,
            listOf("hash.json" to RpkSigner.sha256(unsignedMeta)),
            keyMaterial,
        )

        val outerEntries = buildList {
            add(RpkEntry("META-INF/CERT", signedMeta))
            payload.forEach { (path, bytes) -> add(RpkEntry(path, bytes)) }
        }
        val unsignedRpk = createZip(outerEntries, comment)
        val signedRpk = RpkSigner.sign(
            unsignedRpk,
            outerEntries.map { it.name to RpkSigner.sha256(it.bytes) },
            keyMaterial,
        )
        RpkSigner.verifyOrThrow(signedRpk)

        val outputDirectory = File(context.cacheDir, "rpk").apply { mkdirs() }
        val output = File(outputDirectory, OUTPUT_FILE_NAME)
        val temporary = File.createTempFile("rpker-", ".tmp", outputDirectory)
        try {
            temporary.outputStream().use { it.write(signedRpk) }
            try {
                Files.move(
                    temporary.toPath(),
                    output.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                Files.move(temporary.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
        preferences.edit { putInt("last_version_code", versionCode) }
        RpkBuildResult(output, versionCode, versionName)
    }

    private fun renderManifest(template: String, versionCode: Int, versionName: String): ByteArray =
        JSONObject(template)
            .put("package", PACKAGE_NAME)
            .put("name", context.getString(R.string.app_name))
            .put("versionCode", versionCode)
            .put("versionName", versionName)
            .toString(2)
            .toByteArray(Charsets.UTF_8)

    internal fun renderPage(template: String, notesJson: String): String {
        if (template.windowed(NOTES_MARKER.length).count { it == NOTES_MARKER } != 1) {
            throw RpkBuildException(context.getString(R.string.error_invalid_rpk_template))
        }
        return template.replace(NOTES_MARKER, notesJson)
    }

    private fun createZip(entries: List<RpkEntry>, comment: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.setLevel(Deflater.BEST_COMPRESSION)
            zip.setComment(comment)
            entries.forEach { item ->
                val entry = ZipEntry(item.name).apply { time = 0L }
                zip.putNextEntry(entry)
                zip.write(item.bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun buildInfo(comment: String): String {
        val json = JSONObject(comment)
        return buildString {
            json.keys().forEach { key -> append(key).append('=').append(json.get(key)).append('\n') }
        }.trimEnd()
    }

    private fun readAssetText(path: String): String = context.assets.open(path).bufferedReader().use { it.readText() }
    private fun readAssetBytes(path: String): ByteArray = context.assets.open(path).use { it.readBytes() }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
