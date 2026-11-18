package com.example.scanpro.imaging

import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Edit PDF text (F193): spans, cutting the old letters out, keeping the rest of a line in place, the PDF's own font
 * (also a cut-down embedded one), a bold or italic asked for, removal, and text inside a nested drawing (covered).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PdfTextEditorTest {
    @Before fun setUp() = PDFBoxResourceLoader.init(ApplicationProvider.getApplicationContext<Application>())

    private fun reload(doc: PDDocument): PDDocument {
        val bytes = ByteArrayOutputStream().also { doc.save(it) }.toByteArray()
        doc.close()
        return PDDocument.load(bytes)
    }
    private fun text(doc: PDDocument) = PDFTextStripper().getText(doc)

    private fun sample(): PDDocument {
        val doc = PDDocument()
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        PDPageContentStream(doc, page).use { cs ->
            cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 12f); cs.newLineAtOffset(72f, 700f); cs.showText("Hello World"); cs.endText()
            cs.beginText(); cs.setFont(PDType1Font.TIMES_BOLD, 18f); cs.newLineAtOffset(72f, 650f); cs.showText("Total: 100"); cs.endText()
            // One TJ with two runs far apart: two spans from one instruction.
            cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 10f); cs.newLineAtOffset(72f, 600f)
            cs.showTextWithPositioning(arrayOf<Any>("Left", -8000f, "Right")); cs.endText()
        }
        return reload(doc)
    }

    @Test fun spansCarryTextFontSizeAndWeight() {
        sample().use { doc ->
            val spans = PdfTextEditor.spans(doc, 0)
            assertEquals(listOf("Hello World", "Total: 100", "Left", "Right"), spans.map { it.text })
            val total = spans[1]
            assertEquals("Times-Bold", total.fontName)
            assertEquals(18f, total.fontSize, 0.01f)
            assertTrue(total.bold)
            assertFalse(spans[0].bold)
        }
    }

    @Test fun editReplacesTheWordsInTheSameFontAndRemovesTheOldOnes() {
        sample().use { doc0 ->
            val span = PdfTextEditor.spans(doc0, 0)[0]
            val outcome = PdfTextEditor.apply(doc0, listOf(PdfTextEditor.Edit(0, span.index, "Hello ScanPro", bold = false, italic = false, size = span.fontSize)))
            assertEquals(PdfTextEditor.Outcome(1, 1, 0, 0), outcome)
            reload(doc0).use { doc ->
                val all = text(doc)
                assertTrue(all, "Hello ScanPro" in all)
                assertFalse(all, "World" in all)
                val again = PdfTextEditor.spans(doc, 0).first { it.text == "Hello ScanPro" }
                assertEquals("Helvetica", again.fontName)
                assertEquals(span.left, again.left, 0.5f)
                assertEquals(span.bottom, again.bottom, 0.5f)
            }
        }
    }

    @Test fun cuttingPartOfAnInstructionLeavesTheRestWhereItWas() {
        sample().use { doc0 ->
            val before = PdfTextEditor.spans(doc0, 0)
            val left = before.first { it.text == "Left" }
            val right = before.first { it.text == "Right" }
            PdfTextEditor.apply(doc0, listOf(PdfTextEditor.Edit(0, left.index, "L", bold = false, italic = false, size = left.fontSize)))
            reload(doc0).use { doc ->
                val after = PdfTextEditor.spans(doc, 0)
                val moved = after.first { it.text == "Right" }
                assertEquals(right.left, moved.left, 0.2f)
                assertTrue(after.any { it.text == "L" })
                assertFalse(after.any { it.text == "Left" })
            }
        }
    }

    @Test fun boldAskedForUsesTheBoldFont() {
        sample().use { doc0 ->
            val span = PdfTextEditor.spans(doc0, 0)[0]
            val outcome = PdfTextEditor.apply(doc0, listOf(PdfTextEditor.Edit(0, span.index, "Hello World", bold = true, italic = false, size = 14f)))
            assertEquals(1, outcome.similarFont)
            reload(doc0).use { doc ->
                val again = PdfTextEditor.spans(doc, 0).first { it.text == "Hello World" }
                assertEquals("Helvetica-Bold", again.fontName)
                assertEquals(14f, again.fontSize, 0.01f)
                assertTrue(again.bold)
            }
        }
    }

    @Test fun anEmbeddedCutDownFontIsKeptWhenItHasTheLetters() {
        val ttf = File("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")
        Assume.assumeTrue("needs the DejaVu font (present on Ubuntu)", ttf.canRead())
        val doc0 = PDDocument()
        val page = PDPage(PDRectangle.A4); doc0.addPage(page)
        val font = PDType0Font.load(doc0, ttf)
        PDPageContentStream(doc0, page).use { cs -> cs.beginText(); cs.setFont(font, 16f); cs.newLineAtOffset(50f, 500f); cs.showText("Hello there"); cs.endText() }
        reload(doc0).use { doc ->
            val span = PdfTextEditor.spans(doc, 0).single()
            assertTrue(span.fontName.startsWith("DejaVuSans"))
            // "there hello" uses only letters already in the subset; "Jump" does not.
            val outcome = PdfTextEditor.apply(doc, listOf(PdfTextEditor.Edit(0, 0, "Hollo three", bold = false, italic = false, size = 16f)))
            assertEquals(1, outcome.sameFont)
            reload(doc).use { d2 ->
                val s = PdfTextEditor.spans(d2, 0).single()
                assertEquals("Hollo three", s.text)
                assertTrue(s.fontName, s.fontName.startsWith("DejaVuSans"))
                val outcome2 = PdfTextEditor.apply(d2, listOf(PdfTextEditor.Edit(0, 0, "Jump", bold = false, italic = false, size = 16f)))
                assertEquals(1, outcome2.similarFont)
                reload(d2).use { d3 -> assertEquals("Jump", PdfTextEditor.spans(d3, 0).single().text) }
            }
        }
    }

    @Test fun textInsideANestedDrawingIsCovered() {
        val doc0 = PDDocument()
        val page = PDPage(PDRectangle.A4); doc0.addPage(page)
        val form = PDFormXObject(doc0)
        form.bBox = PDRectangle(0f, 0f, 300f, 100f)
        form.resources = PDResources()
        PDPageContentStream(doc0, form, form.stream.createOutputStream()).use { cs ->
            cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 12f); cs.newLineAtOffset(10f, 50f); cs.showText("In a form"); cs.endText()
        }
        PDPageContentStream(doc0, page).use { cs -> cs.saveGraphicsState(); cs.transform(com.tom_roush.pdfbox.util.Matrix.getTranslateInstance(100f, 400f)); cs.drawForm(form); cs.restoreGraphicsState() }
        reload(doc0).use { doc ->
            val span = PdfTextEditor.spans(doc, 0).single()
            assertEquals("In a form", span.text)
            val outcome = PdfTextEditor.apply(doc, listOf(PdfTextEditor.Edit(0, 0, "Changed", bold = false, italic = false, size = 12f)))
            assertEquals(1, outcome.covered)
            // Covered, not cut out: the new words are drawn over a white box in the page's own content.
            reload(doc).use { d2 -> assertTrue(String(d2.getPage(0).contents.readAllBytes()).contains("(Changed) Tj")) }
        }
    }

    @Test fun italicAndRemoval() {
        sample().use { doc0 ->
            val spans = PdfTextEditor.spans(doc0, 0)
            PdfTextEditor.apply(doc0, listOf(
                PdfTextEditor.Edit(0, spans[1].index, "", bold = true, italic = false, size = 18f),
                PdfTextEditor.Edit(0, spans[0].index, "Slanted", family = PdfTextEditor.Family.SERIF, bold = false, italic = true, size = 12f)
            ))
            reload(doc0).use { doc ->
                val after = PdfTextEditor.spans(doc, 0)
                assertFalse(after.any { "Total" in it.text })
                val s = after.first { it.text == "Slanted" }
                assertEquals("Times-Italic", s.fontName)
                assertTrue(s.italic)
            }
        }
    }
}
