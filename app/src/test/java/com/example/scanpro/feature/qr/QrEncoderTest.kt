package com.example.scanpro.feature.qr

import com.example.scanpro.scanner.parseScannedCode
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Encodes with ZXing and decodes with ZXing's own reader (pure Java), to prove the picture really holds the text. */
class QrEncoderTest {
    private fun decode(image: QrImage): String {
        val source = RGBLuminanceSource(image.size, image.size, image.pixels)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    private fun roundTrip(text: String) {
        val image = QrEncoder.encode(text)
        assertNotNull("encodes: $text", image)
        assertEquals(text, decode(image!!))
    }

    @Test
    fun `the picture is 1024 pixels, black on white, with a quiet zone`() {
        val image = QrEncoder.encode("hello")!!
        assertEquals(1024, image.size)
        assertEquals(1024 * 1024, image.pixels.size)
        assertTrue("only black and white", image.pixels.all { it == -0x1000000 || it == -1 })
        // Every corner is inside the quiet zone, so it is white.
        val last = image.size - 1
        listOf(0, last).forEach { x -> listOf(0, last).forEach { y -> assertEquals(-1, image.pixels[y * image.size + x]) } }
        // The first dark column is at least four modules in (a version 1 module is 35 px here).
        var firstDark = image.size
        for (y in 0 until image.size) {
            for (x in 0 until minOf(firstDark, image.size)) {
                if (image.pixels[y * image.size + x] == -0x1000000) {
                    firstDark = x
                    break
                }
            }
        }
        assertTrue("quiet zone of $firstDark px", firstDark >= 4 * 35)
    }

    @Test
    fun `plain text, links and unicode survive the round trip`() {
        roundTrip("hello")
        roundTrip("https://example.com/path?a=1&b=two%20words")
        roundTrip("नमस्ते ₹250 café")
        roundTrip("line one\nline two")
    }

    @Test
    fun `every generator type survives the round trip`() {
        roundTrip(QrPayloads.wifi("cafe;guest", "p:w\\d\"x,y", WifiSecurity.WPA, hidden = true)!!)
        roundTrip(QrPayloads.phone("+91 98765 43210")!!)
        roundTrip(QrPayloads.email("a@b.com", "Hi there", "Body & more")!!)
        roundTrip(QrPayloads.contact("Asha Rao", "+91 98765 43210", "asha@example.com")!!)
        roundTrip(QrPayloads.upi("shop@okbank", "Fresh & Co", "250.50", "Order #12")!!)
        roundTrip(QrPayloads.link("example.com")!!)
    }

    @Test
    fun `what the generator makes is what the scanner reads back`() {
        val wifi = parseScannedCode(decode(QrEncoder.encode(QrPayloads.wifi("cafe;guest", "p:w\\d", WifiSecurity.WPA, hidden = true)!!)!!), Barcode.FORMAT_QR_CODE).wifi!!
        assertEquals("cafe;guest", wifi.ssid)
        assertEquals("p:w\\d", wifi.password)
        assertTrue(wifi.hidden)

        val contact = parseScannedCode(decode(QrEncoder.encode(QrPayloads.contact("Asha Rao", "+91 98765 43210", "asha@example.com")!!)!!), Barcode.FORMAT_QR_CODE).contact!!
        assertEquals("Asha Rao", contact.name)
        assertEquals(listOf("asha@example.com"), contact.emails)

        val mail = parseScannedCode(decode(QrEncoder.encode(QrPayloads.email("a@b.com", "Hi there", "Body & more")!!)!!), Barcode.FORMAT_QR_CODE).email!!
        assertEquals("Hi there", mail.subject)
        assertEquals("Body & more", mail.body)
    }

    @Test
    fun `empty and oversized content give no picture`() {
        assertNull(QrEncoder.encode(""))
        assertNull(QrEncoder.encode("x".repeat(5000)))
    }
}
