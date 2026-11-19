package com.example.scanpro.imaging.metadata

import java.io.ByteArrayOutputStream

/**
 * Removes the details from a JPEG, PNG or WebP without touching the picture: the bytes of the image are copied as they
 * are, so nothing is decoded or compressed again and no quality is lost. What keeps the colours and the way the picture
 * is meant to be shown stays: the ICC colour profile, Adobe's colour marker, and PNG's colour and pixel-size chunks.
 * The orientation lives in EXIF, which goes; the caller writes it back when it was not upright. Pure Kotlin, unit tested.
 */
object MetadataStrip {
    /** JPEG: drops APP1 (EXIF, XMP), APP13 (IPTC, Photoshop), the other application blocks and comments. */
    fun jpeg(input: ByteArray): ByteArray {
        require(input.size >= 4 && (input[0].toInt() and 0xFF) == 0xFF && (input[1].toInt() and 0xFF) == 0xD8) { "Not a JPEG" }
        val out = ByteArrayOutputStream(input.size)
        out.write(input, 0, 2)
        var i = 2
        while (i + 4 <= input.size) {
            if ((input[i].toInt() and 0xFF) != 0xFF) break
            val marker = input[i + 1].toInt() and 0xFF
            if (marker == 0xFF) { i++; continue } // fill byte
            // Start of scan: the picture itself follows, up to the end; copied whole.
            if (marker == 0xDA || marker == 0xD9) break
            if (marker == 0x01 || marker in 0xD0..0xD7) { out.write(input, i, 2); i += 2; continue }
            val length = ((input[i + 2].toInt() and 0xFF) shl 8) or (input[i + 3].toInt() and 0xFF)
            val end = i + 2 + length
            require(length >= 2 && end <= input.size) { "Damaged JPEG" }
            val keep = when (marker) {
                0xE0 -> true // JFIF: pixel density, no personal details
                0xE2 -> isIcc(input, i + 4, end) // ICC profile keeps the colours; other APP2 (FlashPix) goes
                0xEE -> true // Adobe: how the colours are stored; removing it can turn CMYK pictures wrong
                in 0xE1..0xEF -> false
                0xFE -> false // comment
                else -> true // tables, frame header and the rest of the picture's own structure
            }
            if (keep) out.write(input, i, end - i)
            i = end
        }
        out.write(input, i, input.size - i)
        return out.toByteArray()
    }

    private fun isIcc(data: ByteArray, from: Int, to: Int): Boolean {
        val tag = "ICC_PROFILE"
        if (to - from < tag.length) return false
        return tag.indices.all { data[from + it].toInt() == tag[it].code }
    }

    /** Chunks that keep how a PNG looks; every other ancillary chunk (text, EXIF, times, XMP) goes. */
    private val PNG_KEEP_ANCILLARY = setOf("iCCP", "sRGB", "gAMA", "cHRM", "sBIT", "pHYs", "tRNS", "bKGD", "acTL", "fcTL", "fdAT", "cICP", "mDCV", "cLLI")

    fun png(input: ByteArray): ByteArray {
        require(input.size >= 8 && (input[0].toInt() and 0xFF) == 0x89 && input[1].toInt() == 'P'.code) { "Not a PNG" }
        val out = ByteArrayOutputStream(input.size)
        out.write(input, 0, 8)
        var i = 8
        while (i + 12 <= input.size) {
            val length = readInt(input, i)
            val type = String(input, i + 4, 4, Charsets.ISO_8859_1)
            val end = i + 12 + length
            require(length >= 0 && end <= input.size) { "Damaged PNG" }
            // A capital first letter marks a critical chunk (header, palette, picture data, end): always kept.
            val critical = type[0].isUpperCase()
            if (critical || type in PNG_KEEP_ANCILLARY) out.write(input, i, end - i)
            i = end
            if (type == "IEND") break
        }
        return out.toByteArray()
    }

    /** WebP: drops the EXIF and XMP chunks and clears their flags in the extended header; the sizes are put right. */
    fun webp(input: ByteArray): ByteArray {
        require(input.size >= 12 && String(input, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(input, 8, 4, Charsets.ISO_8859_1) == "WEBP") { "Not a WebP" }
        val out = ByteArrayOutputStream(input.size)
        out.write(input, 0, 12)
        var i = 12
        var vp8xAt = -1
        while (i + 8 <= input.size) {
            val type = String(input, i, 4, Charsets.ISO_8859_1)
            val length = readIntLe(input, i + 4)
            val padded = length + (length and 1)
            val end = i + 8 + padded
            require(length >= 0 && end <= input.size) { "Damaged WebP" }
            if (type != "EXIF" && type != "XMP ") {
                if (type == "VP8X") vp8xAt = out.size()
                out.write(input, i, end - i)
            }
            i = end
        }
        val bytes = out.toByteArray()
        // VP8X flags: bit 3 says EXIF is there, bit 2 says XMP is.
        if (vp8xAt >= 0) bytes[vp8xAt + 8] = (bytes[vp8xAt + 8].toInt() and 0b11110011).toByte()
        writeIntLe(bytes, 4, bytes.size - 8)
        return bytes
    }

    /**
     * Only the XMP and IPTC blocks of a JPEG, PNG or WebP, which can repeat the location and the author: Android's EXIF
     * editor cannot reach them, so Remove location and Remove personal take them out this way first. EXIF stays.
     */
    fun withoutXmpAndIptc(input: ByteArray): ByteArray = when {
        input.size >= 3 && (input[0].toInt() and 0xFF) == 0xFF && (input[1].toInt() and 0xFF) == 0xD8 -> jpegWithoutXmp(input)
        input.size >= 8 && (input[0].toInt() and 0xFF) == 0x89 && input[1].toInt() == 'P'.code -> pngWithoutXmp(input)
        input.size >= 12 && String(input, 8, 4, Charsets.ISO_8859_1) == "WEBP" -> webpWithoutXmp(input)
        else -> input
    }

    private fun jpegWithoutXmp(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(input.size)
        out.write(input, 0, 2)
        var i = 2
        while (i + 4 <= input.size) {
            if ((input[i].toInt() and 0xFF) != 0xFF) break
            val marker = input[i + 1].toInt() and 0xFF
            if (marker == 0xFF) { i++; continue }
            if (marker == 0xDA || marker == 0xD9) break
            if (marker == 0x01 || marker in 0xD0..0xD7) { out.write(input, i, 2); i += 2; continue }
            val length = ((input[i + 2].toInt() and 0xFF) shl 8) or (input[i + 3].toInt() and 0xFF)
            val end = i + 2 + length
            require(length >= 2 && end <= input.size) { "Damaged JPEG" }
            val isExif = marker == 0xE1 && startsWith(input, i + 4, end, "Exif")
            val drop = marker == 0xED || (marker == 0xE1 && !isExif)
            if (!drop) out.write(input, i, end - i)
            i = end
        }
        out.write(input, i, input.size - i)
        return out.toByteArray()
    }

    private fun pngWithoutXmp(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(input.size)
        out.write(input, 0, 8)
        var i = 8
        while (i + 12 <= input.size) {
            val length = readInt(input, i)
            val type = String(input, i + 4, 4, Charsets.ISO_8859_1)
            val end = i + 12 + length
            require(length >= 0 && end <= input.size) { "Damaged PNG" }
            val xmp = type == "iTXt" && startsWith(input, i + 8, end, "XML:com.adobe.xmp")
            if (!xmp) out.write(input, i, end - i)
            i = end
            if (type == "IEND") break
        }
        return out.toByteArray()
    }

    private fun webpWithoutXmp(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(input.size)
        out.write(input, 0, 12)
        var i = 12
        var vp8xAt = -1
        while (i + 8 <= input.size) {
            val type = String(input, i, 4, Charsets.ISO_8859_1)
            val length = readIntLe(input, i + 4)
            val end = i + 8 + length + (length and 1)
            require(length >= 0 && end <= input.size) { "Damaged WebP" }
            if (type != "XMP ") {
                if (type == "VP8X") vp8xAt = out.size()
                out.write(input, i, end - i)
            }
            i = end
        }
        val bytes = out.toByteArray()
        if (vp8xAt >= 0) bytes[vp8xAt + 8] = (bytes[vp8xAt + 8].toInt() and 0b11111011).toByte()
        writeIntLe(bytes, 4, bytes.size - 8)
        return bytes
    }

    private fun startsWith(data: ByteArray, from: Int, to: Int, text: String): Boolean =
        to - from >= text.length && text.indices.all { data[from + it].toInt() == text[it].code }

    private fun readInt(b: ByteArray, at: Int) =
        ((b[at].toInt() and 0xFF) shl 24) or ((b[at + 1].toInt() and 0xFF) shl 16) or ((b[at + 2].toInt() and 0xFF) shl 8) or (b[at + 3].toInt() and 0xFF)

    private fun readIntLe(b: ByteArray, at: Int) =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)

    private fun writeIntLe(b: ByteArray, at: Int, value: Int) {
        b[at] = value.toByte(); b[at + 1] = (value shr 8).toByte(); b[at + 2] = (value shr 16).toByte(); b[at + 3] = (value shr 24).toByte()
    }
}
