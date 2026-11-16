package com.example.scanpro.feature.qr

import android.graphics.Bitmap
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

/** A code picture: [width] x [height] opaque ARGB pixels. QR codes are square; linear barcodes are half as tall as wide. */
class QrImage(val width: Int, val height: Int, val pixels: IntArray) {
    constructor(size: Int, pixels: IntArray) : this(size, size, pixels)

    /** The width, which is also the height of a QR code. */
    val size: Int get() = width
}

/** Draws codes with ZXing's encoders, entirely on the device: a 4-module quiet zone, colours and correction from a [CodeStyle]. */
object QrEncoder {
    const val SIZE = 1024
    private const val QUIET_ZONE_MODULES = 4

    /** The QR code for [content] at [size] pixels, black on white with error correction M; null when empty or too long. */
    fun encode(content: String, size: Int = SIZE): QrImage? = encode(content, CodeFormat.QR, CodeStyle(size = size))

    /**
     * [content] drawn as [format] in [style]; null when it is empty, too long, or not valid for the format (EAN-13
     * needs 12 or 13 digits with a right check digit; Code 128 printable ASCII). Run it off the main thread: a
     * 2048-pixel code takes a moment.
     */
    fun encode(content: String, format: CodeFormat, style: CodeStyle = CodeStyle()): QrImage? {
        if (content.isEmpty()) return null
        val checked = when (format) {
            CodeFormat.QR -> content
            CodeFormat.EAN_13 -> BarcodeChecks.ean13(content)
            CodeFormat.CODE_128 -> BarcodeChecks.code128(content)
        } ?: return null
        val width = style.size
        val height = if (format.isLinear) style.size / 2 else style.size
        val hints = HashMap<EncodeHintType, Any>()
        if (format == CodeFormat.QR) {
            hints[EncodeHintType.ERROR_CORRECTION] = style.correction.zxing
            hints[EncodeHintType.MARGIN] = QUIET_ZONE_MODULES
            // Plain ASCII needs no character-set marker; anything else is written as UTF-8, which phones read.
            if (checked.any { it.code > 127 }) hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        }
        val matrix = runCatching { MultiFormatWriter().encode(checked, format.zxing, width, height, hints) }.getOrNull() ?: return null
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        val foreground = style.foreground or OPAQUE
        val background = style.background or OPAQUE
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                pixels[row + x] = if (matrix.get(x, y)) foreground else background
            }
        }
        return QrImage(w, h, pixels)
    }

    private const val OPAQUE = -0x1000000
}

/** The picture as an Android bitmap, for showing and sharing. */
fun QrImage.toBitmap(): Bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
