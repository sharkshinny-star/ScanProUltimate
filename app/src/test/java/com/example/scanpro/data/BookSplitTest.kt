package com.example.scanpro.data

import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.robolectric.annotation.GraphicsMode

/** splitBookSpread needs a real picture to cut, so this runs with Robolectric's native graphics. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookSplitTest : RepositoryTestBase() {
    private fun writeJpeg(file: File, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
    }

    private fun spreadDocument(pages: Int = 3, width: Int = 800): ScanDocument {
        val doc = newDocument(pages)
        writeJpeg(File(doc.page(1).imagePath), width, 300)
        return doc
    }

    @Test fun splittingASpreadMakesTwoPagesInItsPlaceAndMarksTheDocumentAsABook() = runBlocking {
        val doc = spreadDocument()
        ocr.text = "half"
        repository.splitBookSpread(doc.id, doc.page(1).id)
        val after = load(doc.id)!!
        val pages = after.pages.sortedBy { it.position }
        assertEquals(4, pages.size)
        assertEquals(listOf(0, 1, 2, 3), pages.map { it.position })
        assertEquals(doc.page(0).id, pages[0].id)
        assertEquals(doc.page(2).id, pages[3].id)
        assertFalse(pages.any { it.id == doc.page(1).id })
        assertEquals(listOf("half", "half"), pages.subList(1, 3).map { it.ocrText })
        assertEquals(ScanMode.BOOK, after.scanMode)
        assertTrue(pages.all { File(it.imagePath).exists() && File(it.sourcePath).exists() })
    }

    @Test fun theOriginalSpreadFilesAreDeletedOnceTheHalvesAreSaved() = runBlocking {
        val doc = spreadDocument()
        val spread = doc.page(1)
        repository.splitBookSpread(doc.id, spread.id)
        assertFalse(File(spread.imagePath).exists())
        assertFalse(File(spread.sourcePath).exists())
    }

    @Test fun theSplitFractionDecidesTheWidthOfEachHalf() = runBlocking {
        val doc = spreadDocument(width = 800)
        repository.splitBookSpread(doc.id, doc.page(1).id, splitFraction = 0.25f)
        val halves = load(doc.id)!!.pages.sortedBy { it.position }.subList(1, 3)
        val widths = halves.map { page ->
            android.graphics.BitmapFactory.Options().also { android.graphics.BitmapFactory.decodeFile(page.imagePath, it.apply { inJustDecodeBounds = true }) }.outWidth
        }
        assertEquals(listOf(200, 600), widths)
    }

    @Test fun aPageThatIsTooNarrowIsRefusedAndNothingChanges() = runBlocking {
        val doc = spreadDocument(width = 400)
        try {
            repository.splitBookSpread(doc.id, doc.page(1).id)
            fail("expected a too-narrow error")
        } catch (expected: Exception) {
        }
        assertEquals(doc.order(), load(doc.id)!!.order())
        assertTrue(File(doc.page(1).imagePath).exists())
    }

    @Test fun anUnknownPageIsRefused() = runBlocking {
        val doc = spreadDocument()
        try {
            repository.splitBookSpread(doc.id, "no-such-page")
            fail("expected a page-not-found error")
        } catch (expected: Exception) {
        }
        assertEquals(doc.order(), load(doc.id)!!.order())
    }

    @Test fun aPageThatCannotBeDecodedIsRefused() = runBlocking {
        val doc = newDocument(2) // page files are plain text here
        try {
            repository.splitBookSpread(doc.id, doc.page(0).id)
            fail("expected a decode error")
        } catch (expected: Exception) {
        }
        assertEquals(2, load(doc.id)!!.pages.size)
    }
}
