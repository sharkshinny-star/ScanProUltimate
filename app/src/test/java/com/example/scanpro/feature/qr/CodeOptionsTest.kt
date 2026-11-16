package com.example.scanpro.feature.qr

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Barcode checks, colour contrast and styled encoding, decoded again with ZXing's own (pure Java) reader. */
class CodeOptionsTest {
    private fun decode(image: QrImage): String =
        MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(image.width, image.height, image.pixels)))).text

    @Test
    fun `EAN-13 check digit matches known numbers`() {
        assertEquals(1, BarcodeChecks.ean13CheckDigit("400638133393"))
        assertEquals(7, BarcodeChecks.ean13CheckDigit("978030640615"))
        assertEquals("9780306406157", BarcodeChecks.ean13("978-0-306-40615"))
        assertEquals("4006381333931", BarcodeChecks.ean13("4006381333931"))
    }

    @Test
    fun `EAN-13 problems are named`() {
        assertNull(BarcodeChecks.ean13Problem(""))
        assertEquals(Ean13Problem.NOT_DIGITS, BarcodeChecks.ean13Problem("97803064061A"))
        assertEquals(Ean13Problem.WRONG_LENGTH, BarcodeChecks.ean13Problem("12345"))
        assertEquals(Ean13Problem.WRONG_CHECK_DIGIT, BarcodeChecks.ean13Problem("4006381333932"))
        assertNull(BarcodeChecks.ean13("4006381333932"))
    }

    @Test
    fun `Code 128 takes printable ASCII up to 80 characters`() {
        assertEquals("INV-2026/0042", BarcodeChecks.code128("INV-2026/0042"))
        assertEquals(Code128Problem.UNSUPPORTED_CHARACTERS, BarcodeChecks.code128Problem("café"))
        assertEquals(Code128Problem.TOO_LONG, BarcodeChecks.code128Problem("x".repeat(81)))
        assertNull(BarcodeChecks.code128(""))
    }

    @Test
    fun `contrast ratio and verdicts`() {
        assertEquals(21.0, CodeColours.contrastRatio(CodeColours.BLACK, CodeColours.WHITE), 0.01)
        assertEquals(1.0, CodeColours.contrastRatio(0xFF777777.toInt(), 0xFF777777.toInt()), 0.0001)
        assertEquals(ContrastVerdict.GOOD, CodeColours.verdict(0xFF1A237E.toInt(), CodeColours.WHITE))
        assertEquals(ContrastVerdict.INVERTED, CodeColours.verdict(CodeColours.WHITE, CodeColours.BLACK))
        assertEquals(ContrastVerdict.TOO_LOW, CodeColours.verdict(0xFFFFD54A.toInt(), CodeColours.WHITE))
        assertEquals(ContrastVerdict.LOW, CodeColours.verdict(CodeColours.BLACK, 0xFF5E5E5E.toInt()))
    }

    @Test
    fun `every offered dark colour on every offered light background except the grey is good`() {
        val darks = CodeColours.FOREGROUNDS.filter { CodeColours.luminance(it) < 0.2 }
        val lights = CodeColours.BACKGROUNDS.filter { CodeColours.luminance(it) > 0.8 }
        for (f in darks) for (b in lights) assertEquals(ContrastVerdict.GOOD, CodeColours.verdict(f, b))
    }

    @Test
    fun `a coloured QR code with high correction still decodes`() {
        val style = CodeStyle(foreground = 0xFF1B5E20.toInt(), background = 0xFFFFF8E1.toInt(), correction = QrCorrection.H, size = 512)
        val image = QrEncoder.encode("https://example.com/menu", CodeFormat.QR, style)!!
        assertEquals(512, image.width)
        assertEquals(512, image.height)
        assertTrue(image.pixels.all { it == 0xFF1B5E20.toInt() || it == 0xFFFFF8E1.toInt() })
        assertEquals("https://example.com/menu", decode(image))
    }

    @Test
    fun `EAN-13 gets its check digit and decodes`() {
        val image = QrEncoder.encode("400638133393", CodeFormat.EAN_13, CodeStyle(size = 600))
        assertNotNull(image)
        assertEquals(300, image!!.height)
        assertEquals("4006381333931", decode(image))
        assertNull(QrEncoder.encode("4006381333932", CodeFormat.EAN_13))
    }

    @Test
    fun `Code 128 decodes`() {
        val image = QrEncoder.encode("INV-2026/0042", CodeFormat.CODE_128, CodeStyle(size = 800))!!
        assertEquals("INV-2026/0042", decode(image))
        assertNull(QrEncoder.encode("café", CodeFormat.CODE_128))
    }
}
