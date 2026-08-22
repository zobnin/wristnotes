package org.execbit.rpker.rpk

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.ZipInputStream

internal data class RpkEntry(val name: String, val bytes: ByteArray)

/**
 * Implements the RPK signature scheme used by @aiot-toolkit/aiotpack 2.0.5.
 * The wire format is derived from the ISC-licensed SignUtil implementation.
 */
internal object RpkSigner {
    private const val DEVELOPER_SIGNATURE_ID = 0x01000101
    private const val FILE_SIGNATURE_ID = 0x01000201
    private const val RSA_SHA256_ID = 0x0103
    private val MAGIC = "RPK Sig Block 42".toByteArray(Charsets.US_ASCII)

    data class KeyMaterial(
        val privateKey: PrivateKey,
        val certificate: X509Certificate,
    )

    fun keyMaterial(privateKeyPem: String, certificatePem: String): KeyMaterial {
        val pkcs1 = decodePem(privateKeyPem, "RSA PRIVATE KEY")
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(
            PKCS8EncodedKeySpec(wrapPkcs1AsPkcs8(pkcs1))
        )
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(decodePem(certificatePem, "CERTIFICATE"))) as X509Certificate
        return KeyMaterial(privateKey, certificate)
    }

    fun sign(
        unsignedZip: ByteArray,
        fileDigests: List<Pair<String, ByteArray>>,
        keyMaterial: KeyMaterial,
    ): ByteArray {
        val eocdOffset = findEocd(unsignedZip)
        val centralOffset = readIntLe(unsignedZip, eocdOffset + 16)
        require(centralOffset in 1 until eocdOffset) { "Invalid ZIP central directory" }

        val headerDigest = sectionDigest(unsignedZip, 0, centralOffset)
        val centralDigest = sectionDigest(unsignedZip, centralOffset, eocdOffset - centralOffset)
        val footerDigest = sectionDigest(unsignedZip, eocdOffset, unsignedZip.size - eocdOffset)

        val wholeDigestInput = LeWriter()
            .byte(0x5a)
            .int(3)
            .bytes(headerDigest)
            .bytes(centralDigest)
            .bytes(footerDigest)
            .toByteArray()
        val contentDigest = sha256(wholeDigestInput)

        val developerValue = developerSignatureValue(contentDigest, keyMaterial)
        val fileValue = fileSignatureValue(fileDigests, keyMaterial)
        val signingBlock = signingBlock(
            listOf(
                DEVELOPER_SIGNATURE_ID to developerValue,
                FILE_SIGNATURE_ID to fileValue,
            )
        )

        val result = ByteArray(unsignedZip.size + signingBlock.size)
        unsignedZip.copyInto(result, 0, 0, centralOffset)
        signingBlock.copyInto(result, centralOffset)
        unsignedZip.copyInto(result, centralOffset + signingBlock.size, centralOffset, unsignedZip.size)
        putIntLe(result, eocdOffset + signingBlock.size + 16, centralOffset + signingBlock.size)
        return result
    }

    fun verifyOrThrow(signedRpk: ByteArray) {
        val parsed = parseSigningBlock(signedRpk)
        verifyDeveloperSignature(signedRpk, parsed)
        verifyFileSignature(signedRpk, parsed)
    }

    private fun developerSignatureValue(
        contentDigest: ByteArray,
        keyMaterial: KeyMaterial,
    ): ByteArray {
        val digestBlockBody = LeWriter()
            .int(RSA_SHA256_ID)
            .int(contentDigest.size)
            .bytes(contentDigest)
            .toByteArray()
        val digestBlock = lengthPrefixed(digestBlockBody)

        val certificateDer = keyMaterial.certificate.encoded
        val certificateBlock = lengthPrefixed(certificateDer)
        val signedData = LeWriter()
            .int(digestBlock.size)
            .bytes(digestBlock)
            .int(certificateBlock.size)
            .bytes(certificateBlock)
            .int(0)
            .toByteArray()

        val signatureBytes = rsaSha256(signedData, keyMaterial.privateKey)
        val signatureBlockBody = LeWriter()
            .int(RSA_SHA256_ID)
            .int(signatureBytes.size)
            .bytes(signatureBytes)
            .toByteArray()
        val signatureBlock = lengthPrefixed(signatureBlockBody)
        val publicKey = keyMaterial.certificate.publicKey.encoded

        val signerBody = LeWriter()
            .int(signedData.size)
            .bytes(signedData)
            .int(signatureBlock.size)
            .bytes(signatureBlock)
            .int(publicKey.size)
            .bytes(publicKey)
            .toByteArray()
        val signerBlock = lengthPrefixed(signerBody)
        return LeWriter().int(signerBlock.size).bytes(signerBlock).toByteArray()
    }

    private fun fileSignatureValue(
        fileDigests: List<Pair<String, ByteArray>>,
        keyMaterial: KeyMaterial,
    ): ByteArray {
        val digestData = LeWriter().int(RSA_SHA256_ID)
        fileDigests.forEach { (name, hash) ->
            require(hash.size <= 0xffff) { "Digest is too long" }
            digestData.int(crc32(name)).short(hash.size).bytes(hash)
        }
        val digestBytes = digestData.toByteArray()
        val signatureBytes = rsaSha256(digestBytes, keyMaterial.privateKey)
        val digestChunk = LeWriter()
            .int(digestBytes.size)
            .bytes(digestBytes)
            .int(signatureBytes.size + 8)
            .int(RSA_SHA256_ID)
            .int(signatureBytes.size)
            .bytes(signatureBytes)
            .toByteArray()
        return LeWriter().int(digestChunk.size).bytes(digestChunk).toByteArray()
    }

    private fun signingBlock(values: List<Pair<Int, ByteArray>>): ByteArray {
        val keyValues = LeWriter()
        values.forEach { (id, value) ->
            keyValues.long(value.size.toLong() + 4L).int(id).bytes(value)
        }
        val keyValueBytes = keyValues.toByteArray()
        val sizeExcludingFirstField = keyValueBytes.size.toLong() + 24L
        return LeWriter()
            .long(sizeExcludingFirstField)
            .bytes(keyValueBytes)
            .long(sizeExcludingFirstField)
            .bytes(MAGIC)
            .toByteArray()
    }

    private data class ParsedSigningBlock(
        val startOffset: Int,
        val centralOffset: Int,
        val eocdOffset: Int,
        val totalSize: Int,
        val developerValue: ByteArray,
        val fileValue: ByteArray,
    )

    private fun parseSigningBlock(bytes: ByteArray): ParsedSigningBlock {
        val eocd = findEocd(bytes)
        val central = readIntLe(bytes, eocd + 16)
        require(central >= 24 && bytes.copyOfRange(central - 16, central).contentEquals(MAGIC)) {
            "RPK signature magic is missing"
        }
        val size = readLongLe(bytes, central - 24)
        require(size in 24..Int.MAX_VALUE.toLong()) { "Invalid RPK signature size" }
        val totalSize = (size + 8).toInt()
        val start = central - totalSize
        require(start >= 0 && readLongLe(bytes, start) == size) { "RPK signature sizes differ" }

        var position = start + 8
        val valuesEnd = central - 24
        var developer: ByteArray? = null
        var files: ByteArray? = null
        while (position < valuesEnd) {
            val pairSize = readLongLe(bytes, position).toInt()
            require(pairSize >= 4 && position + 8 + pairSize <= valuesEnd) { "Invalid RPK ID-value pair" }
            val id = readIntLe(bytes, position + 8)
            val valueStart = position + 12
            val valueEnd = position + 8 + pairSize
            val value = bytes.copyOfRange(valueStart, valueEnd)
            when (id) {
                DEVELOPER_SIGNATURE_ID -> developer = value
                FILE_SIGNATURE_ID -> files = value
            }
            position = valueEnd
        }
        require(position == valuesEnd) { "RPK signing block is misaligned" }
        return ParsedSigningBlock(
            startOffset = start,
            centralOffset = central,
            eocdOffset = eocd,
            totalSize = totalSize,
            developerValue = requireNotNull(developer) { "Developer signature is missing" },
            fileValue = requireNotNull(files) { "File signature is missing" },
        )
    }

    private fun verifyDeveloperSignature(bytes: ByteArray, parsed: ParsedSigningBlock) {
        val value = Cursor(parsed.developerValue)
        val signersSize = value.int()
        require(signersSize == value.remaining) { "Invalid signer sequence size" }
        val signerSize = value.int()
        require(signerSize == value.remaining) { "Invalid signer size" }
        val signedData = value.byteArray(value.int())
        val signaturesSize = value.int()
        val signaturesStart = value.position
        val signatureBlockSize = value.int()
        val signatureAlgorithm = value.int()
        val signature = value.byteArray(value.int())
        require(signatureAlgorithm == RSA_SHA256_ID && value.position - signaturesStart == signaturesSize) {
            "Unsupported or invalid developer signature"
        }
        require(signatureBlockSize == signature.size + 8) { "Invalid signature block size" }
        val publicKey = value.byteArray(value.int())
        require(value.remaining == 0 && publicKey.isNotEmpty()) { "Invalid public key block" }

        val signedDataCursor = Cursor(signedData)
        val digestsSize = signedDataCursor.int()
        val digestsStart = signedDataCursor.position
        val digestBlockSize = signedDataCursor.int()
        require(signedDataCursor.int() == RSA_SHA256_ID) { "Unsupported content digest" }
        val storedDigest = signedDataCursor.byteArray(signedDataCursor.int())
        require(digestBlockSize == storedDigest.size + 8)
        require(signedDataCursor.position - digestsStart == digestsSize)
        val certificatesSize = signedDataCursor.int()
        val certificatesStart = signedDataCursor.position
        val certificateBytes = signedDataCursor.byteArray(signedDataCursor.int())
        require(signedDataCursor.position - certificatesStart == certificatesSize)
        require(signedDataCursor.int() == 0 && signedDataCursor.remaining == 0)

        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certificateBytes)) as X509Certificate
        require(verifyRsaSha256(signedData, signature, certificate)) { "Developer RSA signature is invalid" }

        val unsigned = removeSigningBlock(bytes, parsed)
        val expectedDigest = zipContentDigest(unsigned)
        require(storedDigest.contentEquals(expectedDigest)) { "RPK content digest is invalid" }
    }

    private fun verifyFileSignature(bytes: ByteArray, parsed: ParsedSigningBlock) {
        val value = Cursor(parsed.fileValue)
        val blocksSize = value.int()
        require(blocksSize == value.remaining) { "Invalid file signer sequence size" }
        val digestData = value.byteArray(value.int())
        val signatureBlockSize = value.int()
        require(value.int() == RSA_SHA256_ID) { "Unsupported file signature" }
        val signatureBytes = value.byteArray(value.int())
        require(signatureBlockSize == signatureBytes.size + 8 && value.remaining == 0)

        val certificate = extractCertificate(parsed.developerValue)
        require(verifyRsaSha256(digestData, signatureBytes, certificate)) { "File-list RSA signature is invalid" }

        val expectedByCrc = linkedMapOf<Int, MutableList<ByteArray>>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                if (!entry.isDirectory) {
                    expectedByCrc.getOrPut(crc32(entry.name)) { mutableListOf() }.add(sha256(input.readBytes()))
                }
            }
        }
        val digestCursor = Cursor(digestData)
        require(digestCursor.int() == RSA_SHA256_ID)
        var records = 0
        while (digestCursor.remaining > 0) {
            val nameCrc = digestCursor.int()
            val hash = digestCursor.byteArray(digestCursor.short())
            val candidates = expectedByCrc[nameCrc] ?: error("Unknown file digest record")
            require(candidates.removeFirstMatching(hash)) { "File digest does not match ZIP content" }
            if (candidates.isEmpty()) expectedByCrc.remove(nameCrc)
            records++
        }
        require(records > 0 && expectedByCrc.isEmpty()) { "RPK file digest list is incomplete" }
    }

    private fun extractCertificate(developerValue: ByteArray): X509Certificate {
        val value = Cursor(developerValue)
        value.int()
        value.int()
        val signedData = Cursor(value.byteArray(value.int()))
        val digestSize = signedData.int()
        signedData.skip(digestSize)
        signedData.int()
        val cert = signedData.byteArray(signedData.int())
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(cert)) as X509Certificate
    }

    private fun removeSigningBlock(bytes: ByteArray, parsed: ParsedSigningBlock): ByteArray {
        val unsigned = ByteArray(bytes.size - parsed.totalSize)
        bytes.copyInto(unsigned, 0, 0, parsed.startOffset)
        bytes.copyInto(unsigned, parsed.startOffset, parsed.centralOffset, bytes.size)
        val unsignedEocd = parsed.eocdOffset - parsed.totalSize
        putIntLe(unsigned, unsignedEocd + 16, parsed.startOffset)
        return unsigned
    }

    private fun zipContentDigest(unsignedZip: ByteArray): ByteArray {
        val eocd = findEocd(unsignedZip)
        val central = readIntLe(unsignedZip, eocd + 16)
        val input = LeWriter()
            .byte(0x5a)
            .int(3)
            .bytes(sectionDigest(unsignedZip, 0, central))
            .bytes(sectionDigest(unsignedZip, central, eocd - central))
            .bytes(sectionDigest(unsignedZip, eocd, unsignedZip.size - eocd))
            .toByteArray()
        return sha256(input)
    }

    private fun sectionDigest(bytes: ByteArray, offset: Int, length: Int): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(0xa5.toByte())
        digest.update(leIntBytes(length))
        digest.update(bytes, offset, length)
        return digest.digest()
    }

    private fun rsaSha256(bytes: ByteArray, privateKey: PrivateKey): ByteArray =
        Signature.getInstance("SHA256withRSA").run {
            initSign(privateKey)
            update(bytes)
            sign()
        }

    private fun verifyRsaSha256(bytes: ByteArray, signature: ByteArray, certificate: X509Certificate): Boolean =
        Signature.getInstance("SHA256withRSA").run {
            initVerify(certificate.publicKey)
            update(bytes)
            verify(signature)
        }

    internal fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun crc32(name: String): Int = CRC32().run {
        update(name.toByteArray(Charsets.UTF_8))
        value.toInt()
    }

    private fun findEocd(bytes: ByteArray): Int {
        for (offset in bytes.size - 22 downTo maxOf(0, bytes.size - 22 - 0xffff)) {
            if (readIntLe(bytes, offset) == 0x06054b50) return offset
        }
        error("ZIP end-of-central-directory record is missing")
    }

    private fun lengthPrefixed(bytes: ByteArray): ByteArray = LeWriter().int(bytes.size).bytes(bytes).toByteArray()

    private fun decodePem(pem: String, type: String): ByteArray {
        val base64 = pem
            .replace("-----BEGIN $type-----", "")
            .replace("-----END $type-----", "")
            .filterNot(Char::isWhitespace)
        return Base64.getDecoder().decode(base64)
    }

    private fun wrapPkcs1AsPkcs8(pkcs1: ByteArray): ByteArray {
        val version = byteArrayOf(0x02, 0x01, 0x00)
        val rsaAlgorithm = byteArrayOf(
            0x30, 0x0d, 0x06, 0x09,
            0x2a, 0x86.toByte(), 0x48, 0x86.toByte(), 0xf7.toByte(), 0x0d, 0x01, 0x01, 0x01,
            0x05, 0x00,
        )
        val privateKey = der(0x04, pkcs1)
        return der(0x30, version + rsaAlgorithm + privateKey)
    }

    private fun der(tag: Int, content: ByteArray): ByteArray =
        byteArrayOf(tag.toByte()) + derLength(content.size) + content

    private fun derLength(length: Int): ByteArray {
        if (length < 0x80) return byteArrayOf(length.toByte())
        var value = length
        val reversed = ArrayList<Byte>()
        while (value > 0) {
            reversed.add((value and 0xff).toByte())
            value = value ushr 8
        }
        return byteArrayOf((0x80 or reversed.size).toByte()) + reversed.asReversed().toByteArray()
    }

    private fun leIntBytes(value: Int): ByteArray = byteArrayOf(
        value.toByte(),
        (value ushr 8).toByte(),
        (value ushr 16).toByte(),
        (value ushr 24).toByte(),
    )

    private fun readIntLe(bytes: ByteArray, offset: Int): Int {
        require(offset >= 0 && offset + 4 <= bytes.size)
        return (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            (bytes[offset + 3].toInt() shl 24)
    }

    private fun readLongLe(bytes: ByteArray, offset: Int): Long {
        require(offset >= 0 && offset + 8 <= bytes.size)
        var result = 0L
        for (index in 0 until 8) {
            result = result or ((bytes[offset + index].toLong() and 0xffL) shl (index * 8))
        }
        return result
    }

    private fun putIntLe(bytes: ByteArray, offset: Int, value: Int) {
        leIntBytes(value).copyInto(bytes, offset)
    }

    private class LeWriter {
        private val output = ByteArrayOutputStream()

        fun byte(value: Int) = apply { output.write(value) }
        fun short(value: Int) = apply {
            output.write(value)
            output.write(value ushr 8)
        }
        fun int(value: Int) = apply { output.write(leIntBytes(value)) }
        fun long(value: Long) = apply {
            repeat(8) { output.write((value ushr (it * 8)).toInt()) }
        }
        fun bytes(value: ByteArray) = apply { output.write(value) }
        fun toByteArray(): ByteArray = output.toByteArray()
    }

    private class Cursor(private val bytes: ByteArray) {
        var position: Int = 0
            private set
        val remaining: Int get() = bytes.size - position

        fun int(): Int = readIntLe(bytes, position).also { position += 4 }
        fun short(): Int {
            require(position + 2 <= bytes.size)
            return ((bytes[position].toInt() and 0xff) or
                ((bytes[position + 1].toInt() and 0xff) shl 8)).also { position += 2 }
        }
        fun byteArray(size: Int): ByteArray {
            require(size >= 0 && position + size <= bytes.size)
            return bytes.copyOfRange(position, position + size).also { position += size }
        }
        fun skip(size: Int) {
            require(size >= 0 && position + size <= bytes.size)
            position += size
        }
    }

    private fun MutableList<ByteArray>.removeFirstMatching(expected: ByteArray): Boolean {
        val index = indexOfFirst { it.contentEquals(expected) }
        if (index < 0) return false
        removeAt(index)
        return true
    }
}
