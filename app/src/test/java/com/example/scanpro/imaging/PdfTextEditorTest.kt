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
            assertEquals(PdfTextEditor.Outcome(1, 1, 0, 0), outcome.copy(placed = emptyList()))
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
    @Test fun aCroppedPageKeepsTheNewWordsWhereTheOldOnesWere() {
        val doc0 = PDDocument()
        val page = PDPage(PDRectangle.A4); doc0.addPage(page)
        PDPageContentStream(doc0, page).use { cs -> cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 12f); cs.newLineAtOffset(150f, 400f); cs.showText("Cropped page"); cs.endText() }
        page.cropBox = PDRectangle(100f, 200f, 300f, 400f)
        reload(doc0).use { doc ->
            val before = PdfTextEditor.spans(doc, 0).single()
            assertEquals(50f, before.left, 0.5f)
            PdfTextEditor.apply(doc, listOf(PdfTextEditor.Edit(0, 0, "Cropped text", bold = false, italic = false, size = 12f)))
            reload(doc).use { d2 ->
                val after = PdfTextEditor.spans(d2, 0).single()
                assertEquals("Cropped text", after.text)
                assertEquals(before.left, after.left, 0.5f)
                assertEquals(before.bottom, after.bottom, 0.5f)
            }
        }
    }

    @Test fun aTurnedPageGivesBoxesAsThePageIsShown() {
        val doc0 = PDDocument()
        val page = PDPage(PDRectangle.A4); doc0.addPage(page)
        PDPageContentStream(doc0, page).use { cs -> cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 10f); cs.newLineAtOffset(72f, 700f); cs.showText("Sideways"); cs.endText() }
        page.rotation = 90
        reload(doc0).use { doc ->
            val span = PdfTextEditor.spans(doc, 0).single()
            // Turned a quarter clockwise for display: the page's height runs across, and the line runs down the screen.
            assertEquals(697.5f, span.left, 0.6f)
            assertEquals(709f, span.right, 0.6f)
            assertEquals(72f, span.top, 0.6f)
            assertTrue(span.bottom > 100f)
            PdfTextEditor.apply(doc, listOf(PdfTextEditor.Edit(0, 0, "Turned", bold = false, italic = false, size = 10f)))
            reload(doc).use { d2 -> assertEquals("Turned", PdfTextEditor.spans(d2, 0).single().text) }
        }
    }

    @Test fun textNoFontCanDrawLeavesTheLineAsItWas() {
        sample().use { doc0 ->
            val span = PdfTextEditor.spans(doc0, 0)[0]
            val outcome = PdfTextEditor.apply(doc0, listOf(PdfTextEditor.Edit(0, span.index, "नमस्ते", bold = false, italic = false, size = 12f)))
            assertEquals(1, outcome.skipped)
            assertEquals(0, outcome.applied)
            reload(doc0).use { doc -> assertTrue(text(doc).contains("Hello World")) }
        }
    }

    @Test fun aSuperscriptBesideTheLineIsNotCut() {
        val doc0 = PDDocument()
        val page = PDPage(PDRectangle.A4); doc0.addPage(page)
        PDPageContentStream(doc0, page).use { cs ->
            cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 12f); cs.newLineAtOffset(72f, 500f); cs.showText("Price"); cs.endText()
            cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 7f); cs.newLineAtOffset(103f, 503f); cs.showText("1"); cs.endText()
        }
        reload(doc0).use { doc ->
            val spans = PdfTextEditor.spans(doc, 0)
            val price = spans.first { it.text == "Price" }
            PdfTextEditor.apply(doc, listOf(PdfTextEditor.Edit(0, price.index, "Cost", bold = false, italic = false, size = 12f)))
            reload(doc).use { d2 -> val all = PdfTextEditor.spans(d2, 0).map { it.text }; assertTrue(all.toString(), "1" in all && "Cost" in all) }
        }
    }
    @Test fun newTextGoesWhereTappedInTheNearestLinesFont() {
        sample().use { doc0 ->
            val hello = PdfTextEditor.spans(doc0, 0)[0]
            // Below "Hello World", left-aligned with it.
            val outcome = PdfTextEditor.apply(doc0, listOf(PdfTextEditor.Edit(0, PdfTextEditor.NEW, "Added line", bold = false, italic = false, size = 12f, x = hello.left, y = hello.bottom + 6f)))
            assertEquals(1, outcome.sameFont)
            reload(doc0).use { doc ->
                val added = PdfTextEditor.spans(doc, 0).first { it.text == "Added line" }
                assertEquals("Helvetica", added.fontName)
                assertEquals(hello.left, added.left, 0.5f)
                assertEquals(hello.bottom + 6f + 3f, added.bottom, 0.5f)
                assertTrue(PdfTextEditor.spans(doc, 0).any { it.text == "Hello World" })
            }
        }
    }

    @Test fun newTextOnATurnedPageReadsAcrossTheShownPage() {
        val doc0 = PDDocument()
        val page = PDPage(PDRectangle.A4); doc0.addPage(page)
        page.rotation = 90
        reload(doc0).use { doc ->
            PdfTextEditor.apply(doc, listOf(PdfTextEditor.Edit(0, PdfTextEditor.NEW, "Across", bold = true, italic = false, size = 14f, x = 100f, y = 200f)))
            reload(doc).use { d2 ->
                val s = PdfTextEditor.spans(d2, 0).single()
                assertEquals("Across", s.text)
                assertEquals(100f, s.left, 1f)
                assertTrue("runs left to right as shown: ${'$'}s", s.right - s.left > s.bottom - s.top)
                assertEquals(200f, s.bottom - 14f * 0.25f, 1f)
                assertTrue(s.bold)
            }
        }
    }

    @Test fun aChosenColourIsUsed() {
        sample().use { doc0 ->
            val span = PdfTextEditor.spans(doc0, 0)[0]
            PdfTextEditor.apply(doc0, listOf(PdfTextEditor.Edit(0, span.index, "Red words", bold = false, italic = false, size = 12f, colour = 0xC62828)))
            reload(doc0).use { doc -> assertEquals(0xC62828, PdfTextEditor.spans(doc, 0).first { it.text == "Red words" }.colour) }
        }
    }
    @Test fun rightAlignedWordsEndWhereTheOldOnesEnded() {
        sample().use { doc0 ->
            val total = PdfTextEditor.spans(doc0, 0)[1]
            PdfTextEditor.apply(doc0, listOf(PdfTextEditor.Edit(0, total.index, "Total: 5", bold = true, italic = false, size = 18f, align = PdfTextEditor.Align.RIGHT)))
            reload(doc0).use { doc ->
                val after = PdfTextEditor.spans(doc, 0).first { it.text == "Total: 5" }
                assertEquals(total.right, after.right, 0.6f)
                assertTrue(after.left > total.left + 5f)
            }
        }
    }

    @Test fun centredWordsKeepTheMiddleOfTheOldOnes() {
        sample().use { doc0 ->
            val hello = PdfTextEditor.spans(doc0, 0)[0]
            PdfTextEditor.apply(doc0, listOf(PdfTextEditor.Edit(0, hello.index, "Hi", bold = false, italic = false, size = 12f, align = PdfTextEditor.Align.CENTRE)))
            reload(doc0).use { doc ->
                val after = PdfTextEditor.spans(doc, 0).first { it.text == "Hi" }
                assertEquals((hello.left + hello.right) / 2f, (after.left + after.right) / 2f, 0.6f)
            }
        }
    }

    @Test fun aLongLineWrapsAtThePagesRightEdge() {
        sample().use { doc0 ->
            val hello = PdfTextEditor.spans(doc0, 0)[0]
            val long = "This sentence is much longer than the two words it replaces and has to run onto a second line to stay on the page it is written on"
            // Without wrapping it runs off the page (A4 is 595 points wide).
            sample().use { plain ->
                PdfTextEditor.apply(plain, listOf(PdfTextEditor.Edit(0, hello.index, long, bold = false, italic = false, size = 12f)))
                reload(plain).use { d -> assertTrue(PdfTextEditor.spans(d, 0).maxOf { it.right } > 595f) }
            }
            PdfTextEditor.apply(doc0, listOf(PdfTextEditor.Edit(0, hello.index, long, bold = false, italic = false, size = 12f, wrap = true)))
            reload(doc0).use { doc ->
                val lines = PdfTextEditor.spans(doc, 0).filter { it.fontSize == 12f }.sortedBy { it.top }
                assertEquals(2, lines.size)
                assertEquals(long, lines.joinToString(" ") { it.text })
                lines.forEach { assertEquals(hello.left, it.left, 0.6f); assertTrue("right edge ${it.right}", it.right <= 523.5f) }
                assertEquals(12f * 1.2f, lines[1].bottom - lines[0].bottom, 0.3f)
            }
        }
    }

    @Test fun aTypedLineBreakStartsANewLine() {
        sample().use { doc0 ->
            val hello = PdfTextEditor.spans(doc0, 0)[0]
            PdfTextEditor.apply(doc0, listOf(PdfTextEditor.Edit(0, hello.index, "First line\nSecond", bold = false, italic = false, size = 12f, align = PdfTextEditor.Align.RIGHT)))
            reload(doc0).use { doc ->
                val spans = PdfTextEditor.spans(doc, 0)
                val first = spans.first { it.text == "First line" }
                val second = spans.first { it.text == "Second" }
                assertEquals(14.4f, second.bottom - first.bottom, 0.3f)
                // Both end where "Hello World" ended.
                assertEquals(hello.right, first.right, 0.6f)
                assertEquals(hello.right, second.right, 0.6f)
            }
        }
    }

    @Test fun newTextCanBeCentredOnTheTap() {
        sample().use { doc0 ->
            PdfTextEditor.apply(doc0, listOf(PdfTextEditor.Edit(0, PdfTextEditor.NEW, "Centred title", family = PdfTextEditor.Family.SANS, bold = true, italic = false, size = 16f, x = 297f, y = 300f, align = PdfTextEditor.Align.CENTRE)))
            reload(doc0).use { doc ->
                val s = PdfTextEditor.spans(doc, 0).first { it.text == "Centred title" }
                assertEquals(297f, (s.left + s.right) / 2f, 0.6f)
            }
        }
    }

    @Test fun layoutBreaksAtSpacesAndInsideAWordTooLongForALine() {
        val width: (String) -> Float = { it.length * 10f }
        assertEquals(listOf("one two", "three"), PdfTextEditor.layout("one two three", 75f, width))
        assertEquals(listOf("abcdefgh", "ij kl"), PdfTextEditor.layout("abcdefghij kl", 80f, width))
        assertEquals(listOf("a", "", "b c"), PdfTextEditor.layout("a\n\nb c", null, width))
        assertEquals(listOf("no wrapping here at all"), PdfTextEditor.layout("no wrapping here at all", null, width))
    }
    @Test fun aMovedLineSitsWhereItWasMovedTo() {
        sample().use { doc0 ->
            val hello = PdfTextEditor.spans(doc0, 0)[0]
            PdfTextEditor.apply(doc0, listOf(PdfTextEditor.Edit(0, hello.index, "Hello World", bold = false, italic = false, size = 12f, dx = 30f, dy = -8f)))
            reload(doc0).use { doc ->
                val after = PdfTextEditor.spans(doc, 0).filter { it.text == "Hello World" }
                assertEquals("the old words are gone, not doubled", 1, after.size)
                assertEquals(hello.left + 30f, after[0].left, 0.5f)
                assertEquals(hello.bottom - 8f, after[0].bottom, 0.5f)
            }
        }
    }

    @Test fun replaceInTakesTheQueryAsPlainText() {
        assertEquals("Total: 200 (200)", PdfTextEditor.replaceIn("Total: 100 (100)", "100", "200", matchCase = true))
        assertEquals("Dear Ms Rao", PdfTextEditor.replaceIn("Dear mr Rao", "MR", "Ms", matchCase = false))
        assertEquals(null, PdfTextEditor.replaceIn("Dear mr Rao", "MR", "Ms", matchCase = true))
        // Signs that mean something in a pattern are just letters here, in the query and in the replacement.
        assertEquals("cost: $5", PdfTextEditor.replaceIn("cost: (a.b)", "(a.b)", "$5", matchCase = true))
        assertEquals(null, PdfTextEditor.replaceIn("anything", "", "x", matchCase = false))
    }

    /** A paragraph of three lines 14 points apart, a line well below it and a footer. */
    private fun paragraph(): PDDocument {
        val doc = PDDocument()
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        PDPageContentStream(doc, page).use { cs ->
            for ((i, line) in listOf("First line of it", "Second line of it", "Third line of it").withIndex()) {
                cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 12f); cs.newLineAtOffset(72f, 700f - i * 14f); cs.showText(line); cs.endText()
            }
            cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 12f); cs.newLineAtOffset(72f, 560f); cs.showText("A heading further down"); cs.endText()
            cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 9f); cs.newLineAtOffset(72f, 40f); cs.showText("Footer"); cs.endText()
            cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 12f); cs.newLineAtOffset(400f, 686f); cs.showText("Side note"); cs.endText()
        }
        return reload(doc)
    }

    @Test fun theLinesBelowMoveDownWhenTheNewTextTakesMoreLines() {
        paragraph().use { doc0 ->
            val before = PdfTextEditor.spans(doc0, 0).associateBy { it.text }
            val first = before.getValue("First line of it")
            val long = "This sentence is much longer than the line it replaces and has to run onto a second line to stay on the page it is written on here"
            val outcome = PdfTextEditor.apply(doc0, listOf(PdfTextEditor.Edit(0, first.index, long, bold = false, italic = false, size = 12f, wrap = true)))
            reload(doc0).use { doc ->
                val after = PdfTextEditor.spans(doc, 0).associateBy { it.text }
                val extra = 12f * 1.2f
                // The rest of the paragraph (and the note beside it, which the wider text now reaches) moved down by one line.
                for (name in listOf("Second line of it", "Third line of it", "Side note")) {
                    assertEquals(name, before.getValue(name).top + extra, after.getValue(name).top, 0.3f)
                    assertEquals(before.getValue(name).left, after.getValue(name).left, 0.3f)
                    assertEquals("Helvetica", after.getValue(name).fontName)
                }
                // The gap above the heading took the extra line up: it and the footer stayed.
                assertEquals(before.getValue("A heading further down").top, after.getValue("A heading further down").top, 0.1f)
                assertEquals(before.getValue("Footer").top, after.getValue("Footer").top, 0.1f)
                assertEquals(3, outcome.moved.size)
                assertEquals(extra, outcome.moved.getValue(0 to before.getValue("Second line of it").index), 0.01f)
            }
        }
        // Asked not to: nothing below moves.
        paragraph().use { doc0 ->
            val before = PdfTextEditor.spans(doc0, 0).associateBy { it.text }
            val first = before.getValue("First line of it")
            val outcome = PdfTextEditor.apply(doc0, listOf(PdfTextEditor.Edit(0, first.index, "One\nTwo", bold = false, italic = false, size = 12f, pushBelow = false)))
            assertTrue(outcome.moved.isEmpty())
            reload(doc0).use { doc -> assertEquals(before.getValue("Third line of it").top, PdfTextEditor.spans(doc, 0).first { it.text == "Third line of it" }.top, 0.1f) }
        }
    }

    @Test fun aPushedLineThatIsAlsoEditedIsDrawnLower() {
        paragraph().use { doc0 ->
            val before = PdfTextEditor.spans(doc0, 0).associateBy { it.text }
            val first = before.getValue("First line of it")
            val second = before.getValue("Second line of it")
            PdfTextEditor.apply(doc0, listOf(
                PdfTextEditor.Edit(0, first.index, "One\nTwo\nThree", bold = false, italic = false, size = 12f),
                PdfTextEditor.Edit(0, second.index, "Second changed", bold = false, italic = false, size = 12f)
            ))
            reload(doc0).use { doc ->
                val after = PdfTextEditor.spans(doc, 0).associateBy { it.text }
                assertEquals(second.top + 28.8f, after.getValue("Second changed").top, 0.3f)
                assertEquals(before.getValue("Third line of it").top + 28.8f, after.getValue("Third line of it").top, 0.3f)
            }
        }
    }

    @Test fun placedSaysWhereTheWordsNowAre() {
        sample().use { doc0 ->
            val added = PdfTextEditor.Edit(0, PdfTextEditor.NEW, "Added words", family = PdfTextEditor.Family.SANS, bold = false, italic = false, size = 12f, x = 100f, y = 300f, id = 7)
            val outcome = PdfTextEditor.apply(doc0, listOf(added))
            val placed = outcome.placed.single()
            assertEquals(added, placed.edit)
            reload(doc0).use { doc ->
                val s = PdfTextEditor.spans(doc, 0).first { it.text == "Added words" }
                assertEquals(s.left, placed.left, 0.6f)
                assertEquals(s.right, placed.right, 0.6f)
                assertEquals(s.top, placed.top, 0.6f)
                assertEquals(s.bottom, placed.bottom, 0.6f)
            }
        }
    }

    private fun fills(doc: PDDocument): Int {
        val parser = com.tom_roush.pdfbox.pdfparser.PDFStreamParser(doc.getPage(0)); parser.parse()
        return parser.tokens.count { it is com.tom_roush.pdfbox.contentstream.operator.Operator && (it.name == "f" || it.name == "f*") }
    }

    @Test fun underlineStrikeAndHighlightAreDrawnWithTheWords() {
        sample().use { doc ->
            val hello = PdfTextEditor.spans(doc, 0)[0]
            PdfTextEditor.apply(doc, listOf(PdfTextEditor.Edit(0, hello.index, "Hello World", bold = false, italic = false, size = 12f, underline = true, strike = true, highlight = 0xFFF176)))
            assertEquals(3, fills(doc))
            assertTrue(text(doc).contains("Hello World"))
        }
    }

    @Test fun justifiedLinesReachBothEdgesButTheLastOne() {
        sample().use { doc0 ->
            val hello = PdfTextEditor.spans(doc0, 0)[0]
            val long = "This sentence is much longer than the two words it replaces and has to run onto a second line to stay on the page it is written on"
            PdfTextEditor.apply(doc0, listOf(PdfTextEditor.Edit(0, hello.index, long, bold = false, italic = false, size = 12f, wrap = true, align = PdfTextEditor.Align.JUSTIFY)))
            reload(doc0).use { doc ->
                val lines = PdfTextEditor.spans(doc, 0).filter { it.fontSize == 12f }.groupBy { Math.round(it.top) }.toSortedMap().values.toList()
                assertEquals(2, lines.size)
                assertEquals(523.3f, lines[0].maxOf { it.right }, 0.8f)
                assertTrue(lines[1].maxOf { it.right } < 500f)
                assertEquals(long, lines.joinToString(" ") { row -> row.sortedBy { it.left }.joinToString(" ") { it.text } })
            }
        }
    }

    @Test fun aNumberedListNumbersItsParagraphsAndIndentsThem() {
        sample().use { doc0 ->
            val hello = PdfTextEditor.spans(doc0, 0)[0]
            PdfTextEditor.apply(doc0, listOf(PdfTextEditor.Edit(0, hello.index, "Apples\nPears", bold = false, italic = false, size = 12f, list = PdfTextEditor.ListStyle.NUMBER)))
            reload(doc0).use { doc ->
                val spans = PdfTextEditor.spans(doc, 0)
                val all = text(doc)
                assertTrue(all, all.contains("1.") && all.contains("2."))
                val pears = spans.first { it.text.endsWith("Pears") }
                assertTrue(pears.right > hello.left + 18f)
            }
        }
        sample().use { doc ->
            val hello = PdfTextEditor.spans(doc, 0)[0]
            PdfTextEditor.apply(doc, listOf(PdfTextEditor.Edit(0, hello.index, "Apples\nPears", bold = false, italic = false, size = 12f, list = PdfTextEditor.ListStyle.BULLET)))
            assertEquals(2, fills(doc))
            PdfTextEditor.apply(doc, listOf(PdfTextEditor.Edit(0, PdfTextEditor.NEW, "To do", bold = false, italic = false, size = 12f, x = 80f, y = 400f, list = PdfTextEditor.ListStyle.CHECK)))
            assertEquals(3, fills(doc))
        }
    }

    @Test fun letterSpacingAndIndentsChangeWhereTheWordsSit() {
        fun placed(edit: (PdfTextEditor.Span) -> PdfTextEditor.Edit): PdfTextEditor.Placed = sample().use { doc ->
            PdfTextEditor.apply(doc, listOf(edit(PdfTextEditor.spans(doc, 0)[0]))).placed.single()
        }
        val plain = placed { PdfTextEditor.Edit(0, it.index, "Hello World", bold = false, italic = false, size = 12f) }
        val spaced = placed { PdfTextEditor.Edit(0, it.index, "Hello World", bold = false, italic = false, size = 12f, letterSpacing = 2f) }
        val indented = placed { PdfTextEditor.Edit(0, it.index, "Hello World", bold = false, italic = false, size = 12f, indentLeft = 20f, spaceBefore = 10f) }
        val raised = placed { PdfTextEditor.Edit(0, it.index, "Hello World", bold = false, italic = false, size = 12f, script = PdfTextEditor.Script.SUPER) }
        assertEquals(plain.right - plain.left + 22f, spaced.right - spaced.left, 0.1f)
        assertEquals(plain.left + 20f, indented.left, 0.1f)
        assertEquals(plain.top + 10f, indented.top, 0.1f)
        assertTrue(raised.right - raised.left < (plain.right - plain.left) * 0.7f)
    }

    @Test fun aShaperDrawsJoinedScriptsAsOutlines() {
        val shaper = object : PdfTextEditor.Shaper {
            override fun width(text: String, family: PdfTextEditor.Family, bold: Boolean, italic: Boolean) = text.length * 0.6f
            override fun outline(text: String, family: PdfTextEditor.Family, bold: Boolean, italic: Boolean) =
                text.indices.map { i -> floatArrayOf(i * 0.6f, 0f, i * 0.6f + 0.5f, 0f, i * 0.6f + 0.5f, 0.7f, i * 0.6f, 0.7f) }
        }
        assertTrue(PdfTextEditor.needsShaping("नमस्ते"))
        assertFalse(PdfTextEditor.needsShaping("Namaste 123"))
        sample().use { doc ->
            val hello = PdfTextEditor.spans(doc, 0)[0]
            val hindi = "नमस्ते"
            val outcome = PdfTextEditor.apply(doc, listOf(PdfTextEditor.Edit(0, hello.index, hindi, bold = false, italic = false, size = 12f)), shaper = shaper)
            assertEquals(1, outcome.shaped)
            assertEquals(0, outcome.skipped)
            assertEquals(1, fills(doc))
            assertFalse(text(doc).contains("Hello World"))
            val placed = outcome.placed.single()
            assertEquals(hello.left, placed.left, 0.1f)
            assertEquals(hello.left + hindi.length * 0.6f * 12f, placed.right, 0.1f)
            // Latin text is still written as text, in the PDF's font.
            val total = PdfTextEditor.spans(doc, 0).first { it.text == "Total: 100" }
            assertEquals(0, PdfTextEditor.apply(doc, listOf(PdfTextEditor.Edit(0, total.index, "Total: 200", bold = true, italic = false, size = 18f)), shaper = shaper).shaped)
        }
    }

    @Test fun aCarriedFontIsEmbeddedWhenAskedFor() {
        val fonts = CarriedFonts(org.robolectric.RuntimeEnvironment.getApplication())
        sample().use { doc0 ->
            val hello = PdfTextEditor.spans(doc0, 0)[0]
            // The rupee sign is in the carried fonts; no standard PDF font has it.
            val outcome = PdfTextEditor.apply(
                doc0, listOf(PdfTextEditor.Edit(0, hello.index, "Hello \u20B9500", family = PdfTextEditor.Family.ROBOTO, bold = true, italic = false, size = 12f)), fonts = fonts
            )
            assertEquals(1, outcome.sameFont)
            assertEquals(0, outcome.similarFont)
            assertEquals(0, outcome.skipped)
            reload(doc0).use { doc ->
                val span = PdfTextEditor.spans(doc, 0).first { it.text == "Hello \u20B9500" }
                assertTrue(span.fontName, span.fontName.contains("Roboto"))
                assertTrue(span.bold)
                assertEquals(hello.left, span.left, 0.6f)
            }
        }
        // Every family has a file for every weight and slant, and each loads.
        for (family in PdfTextEditor.Family.values().filter { it.carried }) for (bold in listOf(false, true)) for (italic in listOf(false, true)) {
            PDDocument().use { doc ->
                val font = fonts.load(doc, family, bold, italic)
                assertTrue("$family bold=$bold italic=$italic", font != null && PdfTextEditor.canShow(font, "Quick brown fox, 0123456789 \u00A3\u20AC\u20B9"))
            }
        }
        // Without the files (or for letters they lack) the edit still goes in, in a standard font.
        sample().use { doc ->
            val hello = PdfTextEditor.spans(doc, 0)[0]
            val outcome = PdfTextEditor.apply(doc, listOf(PdfTextEditor.Edit(0, hello.index, "Plain words", family = PdfTextEditor.Family.GELASIO, bold = false, italic = false, size = 12f)))
            assertEquals(1, outcome.applied)
            assertEquals("Times-Roman", PdfTextEditor.spans(doc, 0).first { it.text == "Plain words" }.fontName)
        }
    }
}
