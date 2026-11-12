package com.example.scanpro.imaging

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.cos.COSStream
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A page that links to a page left out of a Split or Extract must not drag that page (and the rest of the source) into
 * the output; a link between two kept pages must keep working.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SplitLinksTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = PDFBoxResourceLoader.init(context)

    /** Three pages; page 3 carries 300 kB that only it refers to; page 1 links to page 3 and page 2 links to page 1. */
    private fun source(): PDDocument {
        val doc = PDDocument()
        val pages = List(3) { PDPage(PDRectangle.A4).also(doc::addPage) }
        val junk = COSStream()
        junk.createOutputStream().use { out -> out.write(ByteArray(300_000) { (it * 31 % 251).toByte() }) }
        pages[2].cosObject.setItem(COSName.getPDFName("Heavy"), junk)
        fun link(from: PDPage, to: PDPage) {
            val destination = PDPageFitDestination().apply { page = to }
            from.annotations = from.annotations + PDAnnotationLink().apply { this.destination = destination; page = from }
        }
        link(pages[0], pages[2])
        link(pages[1], pages[0])
        return doc
    }

    private fun bytes(doc: PDDocument): Int = ByteArrayOutputStream().also { doc.save(it) }.size()

    @Test
    fun `a page linking to a page left out no longer drags it into the output`() {
        source().use { src ->
            PDDocument().use { before ->
                with(PdfToolsEngine) { before.addImportedPage(src.getPage(0)) }
                assertTrue("without the fix the left-out page came along", bytes(before) > 250_000)
            }
        }
        source().use { src ->
            PDDocument().use { single ->
                with(PdfToolsEngine) {
                    single.addImportedPage(src.getPage(0))
                    single.dropLinksToMissingPages()
                }
                assertTrue("one empty page with a link: ${bytes(single)} bytes", bytes(single) < 20_000)
                val link = single.getPage(0).annotations.single() as PDAnnotationLink
                assertNull((link.destination as PDPageDestination).page)
            }
        }
    }

    @Test
    fun `a link between two kept pages still points at its page`() {
        source().use { src ->
            PDDocument().use { kept ->
                with(PdfToolsEngine) {
                    kept.addImportedPage(src.getPage(0))
                    kept.addImportedPage(src.getPage(1))
                    kept.dropLinksToMissingPages()
                }
                val link = kept.getPage(1).annotations.single() as PDAnnotationLink
                assertEquals(kept.getPage(0).cosObject, (link.destination as PDPageDestination).page.cosObject)
            }
        }
    }
}
