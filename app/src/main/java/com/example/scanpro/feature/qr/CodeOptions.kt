package com.example.scanpro.feature.qr

import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** The kinds of code the generator draws. [mlKitFormat] is the Barcode.FORMAT_* number kept in the history. */
enum class CodeFormat(internal val zxing: BarcodeFormat, val mlKitFormat: Int, val isLinear: Boolean) {
    QR(BarcodeFormat.QR_CODE, Barcode.FORMAT_QR_CODE, false),
    EAN_13(BarcodeFormat.EAN_13, Barcode.FORMAT_EAN_13, true),
    CODE_128(BarcodeFormat.CODE_128, Barcode.FORMAT_CODE_128, true)
}

/** QR error correction: how much of the code can be dirty or covered and still scan. */
enum class QrCorrection(internal val zxing: ErrorCorrectionLevel, val percent: Int) {
    L(ErrorCorrectionLevel.L, 7),
    M(ErrorCorrectionLevel.M, 15),
    Q(ErrorCorrectionLevel.Q, 25),
    H(ErrorCorrectionLevel.H, 30)
}

/** How a code is drawn: opaque ARGB colours, the error correction (QR only) and the picture's width in pixels. */
data class CodeStyle(
    val foreground: Int = CodeColours.BLACK,
    val background: Int = CodeColours.WHITE,
    val correction: QrCorrection = QrCorrection.M,
    val size: Int = QrEncoder.SIZE
)

/** Whether a colour pair will still scan. */
enum class ContrastVerdict {
    /** Dark on light with plenty of contrast. */
    GOOD,

    /** Scans on most phones, but not reliably in poor light or on a screen. */
    LOW,

    /** Too little contrast: many scanners will not read it. */
    TOO_LOW,

    /** Light code on a dark background: many scanners cannot read inverted codes. */
    INVERTED
}

/** Colour maths for the generator's styling, as pure functions (WCAG relative luminance). */
object CodeColours {
    const val BLACK = -0x1000000 // 0xFF000000
    const val WHITE = -0x1 // 0xFFFFFFFF

    /** Below this ratio a warning is shown; below [MIN_RATIO] the code is called unreadable. */
    const val GOOD_RATIO = 4.5
    const val MIN_RATIO = 3.0

    private fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    /** The relative luminance of an ARGB colour, 0 (black) to 1 (white); alpha is ignored. */
    fun luminance(argb: Int): Double =
        0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)

    /** The WCAG contrast ratio of two colours, 1 (none) to 21 (black on white). */
    fun contrastRatio(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    fun verdict(foreground: Int, background: Int): ContrastVerdict {
        val ratio = contrastRatio(foreground, background)
        return when {
            ratio < MIN_RATIO -> ContrastVerdict.TOO_LOW
            luminance(foreground) > luminance(background) -> ContrastVerdict.INVERTED
            ratio < GOOD_RATIO -> ContrastVerdict.LOW
            else -> ContrastVerdict.GOOD
        }
    }

    /** The colours offered for the code itself (all dark) and for its background (all light). */
    val FOREGROUNDS = intArrayOf(BLACK, 0xFF1A237E.toInt(), 0xFF1B5E20.toInt(), 0xFF7F1D1D.toInt(), 0xFF4A148C.toInt(), 0xFF004D40.toInt(), 0xFFFFD54A.toInt())
    val BACKGROUNDS = intArrayOf(WHITE, 0xFFFFF8E1.toInt(), 0xFFE3F2FD.toInt(), 0xFFE8F5E9.toInt(), 0xFFFCE4EC.toInt(), 0xFF9E9E9E.toInt(), BLACK)
}

/** Why a typed EAN-13 number cannot be used; null from [BarcodeChecks.ean13Problem] when it can. */
enum class Ean13Problem { NOT_DIGITS, WRONG_LENGTH, WRONG_CHECK_DIGIT }

/** Why typed Code 128 text cannot be used. */
enum class Code128Problem { UNSUPPORTED_CHARACTERS, TOO_LONG }

/** Input checks for the linear barcodes, as pure functions. */
object BarcodeChecks {
    const val CODE_128_MAX = 80

    /** The EAN-13 check digit for the first 12 digits: weights 1 and 3 alternately, then up to the next ten. */
    fun ean13CheckDigit(first12: String): Int {
        require(first12.length == 12 && first12.all { it in '0'..'9' }) { "12 digits needed" }
        val sum = first12.mapIndexed { index, ch -> (ch - '0') * if (index % 2 == 0) 1 else 3 }.sum()
        return (10 - sum % 10) % 10
    }

    /** Spaces and dashes people type between digit groups are ignored. */
    private fun digitsOf(value: String): String = value.filterNot { it == ' ' || it == '-' }

    fun ean13Problem(value: String): Ean13Problem? {
        val digits = digitsOf(value)
        if (digits.isEmpty()) return null
        if (!digits.all { it in '0'..'9' }) return Ean13Problem.NOT_DIGITS
        if (digits.length != 12 && digits.length != 13) return Ean13Problem.WRONG_LENGTH
        if (digits.length == 13 && ean13CheckDigit(digits.take(12)) != digits[12] - '0') return Ean13Problem.WRONG_CHECK_DIGIT
        return null
    }

    /** The full 13 digits: 12 typed digits get their check digit added; null while the number is empty or invalid. */
    fun ean13(value: String): String? {
        val digits = digitsOf(value)
        if (digits.isEmpty() || ean13Problem(value) != null) return null
        return if (digits.length == 12) digits + ean13CheckDigit(digits) else digits
    }

    fun code128Problem(value: String): Code128Problem? = when {
        value.any { it.code !in 32..126 } -> Code128Problem.UNSUPPORTED_CHARACTERS
        value.length > CODE_128_MAX -> Code128Problem.TOO_LONG
        else -> null
    }

    /** Printable ASCII (letters, digits, punctuation and spaces), up to [CODE_128_MAX] characters; null otherwise. */
    fun code128(value: String): String? = value.takeIf { it.isNotEmpty() && code128Problem(it) == null }
}
