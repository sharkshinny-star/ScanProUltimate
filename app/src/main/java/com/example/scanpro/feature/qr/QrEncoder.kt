package com.example.scanpro.feature.qr

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** A square QR picture: [size] x [size] opaque ARGB pixels, black modules on white. */
class QrImage(val size: Int, val pixels: IntArray)

/** Draws QR codes with ZXing's encoder: black on white, error correction M, a 4-module quiet zone, no network. */
object QrEncoder {
    const val SIZE = 1024
    private const val QUIET_ZONE_MODULES = 4
    private const val BLACK = -0x1000000 // 0xFF000000
    private const val WHITE = -0x1 // 0xFFFFFFFF

    /** The code for [content] at [size] pixels, or null when it is empty or too long for one QR code. */
    fun encode(content: String, size: Int = SIZE): QrImage? {
        if (content.isEmpty()) return null
        val hints = HashMap<EncodeHintType, Any>()
        hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
        hints[EncodeHintType.MARGIN] = QUIET_ZONE_MODULES
        // Plain ASCII needs no character-set marker; anything else is written as UTF-8, which phones read.
        if (content.any { it.code > 127 }) hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
        val matrix = runCatching { QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints) }.getOrNull() ?: return null
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                pixels[row + x] = if (matrix.get(x, y)) BLACK else WHITE
            }
        }
        return QrImage(width, pixels)
    }
}

/** The picture as an Android bitmap, for showing and sharing. */
fun QrImage.toBitmap(): Bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
