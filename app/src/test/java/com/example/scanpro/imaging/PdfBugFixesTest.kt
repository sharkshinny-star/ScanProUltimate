package com.example.scanpro.imaging

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.data.PdfPageSize
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceEntry
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Regression tests for the 2026-11-11 bug hunt (F136): Fill form radio options, Compress PDF page sizes. Android's
 * PdfDocument has no native side under Robolectric, so the Compress check covers the page layout it is given. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class PdfBugFixesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
        dir = File(context.cacheDir, "pdf-bug-fixes").apply { deleteRecursively(); mkdirs() }
    }

    /** A radio group whose widgets are named "0" and "1", with the readable values in /Opt, as many form tools write. */
    private fun radioForm(storedValue: String): File {
        val file = File(dir, "form.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.LETTER)
            doc.addPage(page)
            val form = PDAcroForm(doc)
            doc.documentCatalog.acroForm = form
            val radio = PDRadioButton(form)
            radio.partialName = "agree"
            radio.exportValues = listOf("Yes", "No")
            val widgets = (0..1).map { index ->
                PDAnnotationWidget().apply {
                    rectangle = PDRectangle(72f + index * 72f, 600f, 20f, 20f)
                    this.page = page
                    val states = COSDictionary().apply {
                        setItem(COSName.getPDFName(index.toString()), PDAppearanceStream(doc).apply { bBox = PDRectangle(20f, 20f) })
                        setItem(COSName.Off, PDAppearanceStream(doc).apply { bBox = PDRectangle(20f, 20f) })
                    }
                    appearance = PDAppearanceDictionary().apply { normalAppearance =
                        PDAppearanceEntry(states) }
                    setAppearanceState("Off")
                }
            }
            radio.widgets = widgets
            page.annotations.addAll(widgets)
            form.fields.add(radio)
            radio.cosObject.setName(COSName.V, storedValue)
            doc.save(file)
        }
        return file
    }

    @Test
    fun radioOptionsWithOptKeepTheirOwnLabelsInFormOrder() = runBlocking {
        val field = PdfFormEngine.readFields(context, Uri.fromFile(radioForm(storedValue = "1"))).single()

        // Sorting used to pair "No" with the label "Yes", so choosing Yes saved No.
        assertEquals(listOf(FormOption("Yes", "Yes"), FormOption("No", "No")), field.options)
        // The stored state is the option's index; the choice shown is its value.
        assertEquals("No", field.value)
    }

    @Test
    fun compressRebuildKeepsEachPagesOwnSizeEdgeToEdge() {
        // A Letter page rendered at 1.3x: Compress passes the page's own size in points.
        val sized = PdfExporter.layoutPage(PdfPageSize.AUTO, 612 to 792, 796, 1030, pageNumbers = false)
        assertEquals(612, sized.pageWidth)
        assertEquals(792, sized.pageHeight)
        assertEquals(0f, sized.left, 0.5f)
        assertEquals(0f, sized.top, 0.5f)
        assertEquals(612f, sized.width, 0.5f)
        assertEquals(792f, sized.height, 0.5f)

        // Without it (what Compress used to do) the page came back as AUTO, long side 842 pt, inside an 18 pt margin.
        val auto = PdfExporter.layoutPage(PdfPageSize.AUTO, null, 796, 1030, pageNumbers = false)
        assertEquals(842, auto.pageHeight)
        assertTrue("inside the 18 pt margin, was ${auto.top}", auto.top >= 18f)

        // A poster page keeps its size too.
        val a0 = PdfExporter.layoutPage(PdfPageSize.AUTO, 2384 to 3370, 2830, 4000, pageNumbers = false)
        assertEquals(2384, a0.pageWidth)
        assertEquals(3370, a0.pageHeight)
    }
}
