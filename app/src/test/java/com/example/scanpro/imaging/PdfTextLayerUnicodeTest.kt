package com.example.scanpro.imaging

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.scanpro.data.OcrWord
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Make searchable keeps text the page font cannot write (Devanagari, Chinese, ...) as characters, in a font with
 * a ToUnicode map, instead of dropping it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class PdfTextLayerUnicodeTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(context)
    }

    @Test
    fun charactersGetTwoByteCodesAndTheSameCharacterKeepsItsCode() {
        val font = UnicodeTextFont()
        assertEquals("<00010002>", font.hex("ab"))
        assertEquals("<000100020001>", font.hex("aba"))
        assertEquals(3, font.length("aba"))
    }

    @Test
    fun aCharacterOutsideTheBasicPlaneIsOneCodeAndAPairInTheMap() {
        val font = UnicodeTextFont()
        assertEquals("<0001>", font.hex("😀"))
        assertEquals(1, font.length("😀"))
        assertTrue(font.toUnicodeCMap().contains("<0001> <D83DDE00>"))
    }

    @Test
    fun theMapListsEveryCodeAndSplitsIntoBlocksOfAHundred() {
        val font = UnicodeTextFont()
        // 250 different characters (the Devanagari block and on): codes 1..250.
        font.hex(String(CharArray(250) { (0x0900 + it).toChar() }))
        val map = font.toUnicodeCMap()
        assertTrue(map.contains("<0001> <0900>"))
        assertTrue(map.contains("<00FA> <09F9>"))
        assertEquals(3, Regex("beginbfchar").findAll(map).count())
        assertTrue(map.contains("100 beginbfchar"))
        assertTrue(map.contains("50 beginbfchar"))
    }

    @Test
    fun wordsTheFontCannotWriteSurviveInTheSavedPdf() {
        val out = File(context.cacheDir, "unicode-layer-test.pdf")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val words = listOf(
                OcrWord("Hello", 0.10f, 0.10f, 0.30f, 0.14f),
                OcrWord("नमस्ते", 0.10f, 0.20f, 0.35f, 0.24f),
                OcrWord("你好", 0.10f, 0.30f, 0.25f, 0.34f)
            )
            assertEquals(3, PdfTextLayer.addInvisibleWords(doc, page, words, PDType1Font.HELVETICA))
            doc.save(out)
        }
        PDDocument.load(out).use { doc ->
            val text = PDFTextStripper().getText(doc)
            assertTrue(text, text.contains("Hello"))
            assertTrue(text, text.contains("नमस्ते"))
            assertTrue(text, text.contains("你好"))
        }
    }
}
