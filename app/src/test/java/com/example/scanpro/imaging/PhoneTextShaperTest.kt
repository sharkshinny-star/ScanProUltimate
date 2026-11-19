package com.example.scanpro.imaging

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The phone's text shaper (F206), as far as it can be tried without a phone: it gives closed outlines of the right
 * size that sit on the baseline. Whether Hindi and the other joined scripts come out joined depends on the phone's
 * fonts and is a device check (R73).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class PhoneTextShaperTest {
    @Test
    fun outlinesAreClosedShapesOfTheTextsSizeOnTheBaseline() {
        val width = PhoneTextShaper.width("Hi", PdfTextEditor.Family.SANS, bold = false, italic = false)
        assertTrue("width $width", width > 0.5f && width < 2f)
        val contours = PhoneTextShaper.outline("Hi", PdfTextEditor.Family.SANS, bold = false, italic = false)
        // H, the stem of i and its dot.
        assertEquals(3, contours.size)
        contours.forEach { assertTrue(it.size >= 6 && it.size % 2 == 0) }
        val xs = contours.flatMap { c -> c.indices.filter { it % 2 == 0 }.map { c[it] } }
        val ys = contours.flatMap { c -> c.indices.filter { it % 2 == 1 }.map { c[it] } }
        assertTrue("x ${xs.min()}..${xs.max()}", xs.min() >= -0.05f && xs.max() <= width + 0.05f)
        // Letters stand on the baseline and reach up about three quarters of the size.
        assertTrue("y ${ys.min()}..${ys.max()}", ys.min() > -0.05f && ys.max() > 0.6f && ys.max() < 1f)
    }

    @Test
    fun aShapedLineIsDrawnIntoThePdf() {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(org.robolectric.RuntimeEnvironment.getApplication())
        com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
            val page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
            doc.addPage(page)
            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
                cs.beginText(); cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12f); cs.newLineAtOffset(72f, 700f); cs.showText("Hello World"); cs.endText()
            }
            val hello = PdfTextEditor.spans(doc, 0).single()
            val hindi = "नमस्ते"
            val outcome = PdfTextEditor.apply(doc, listOf(PdfTextEditor.Edit(0, hello.index, hindi, bold = false, italic = false, size = 12f)), shaper = PhoneTextShaper)
            assertEquals(1, outcome.shaped)
            assertEquals(0, outcome.skipped)
            assertTrue(PdfTextEditor.spans(doc, 0).isEmpty())
            assertEquals(hello.left, outcome.placed.single().left, 0.1f)
        }
    }
}
