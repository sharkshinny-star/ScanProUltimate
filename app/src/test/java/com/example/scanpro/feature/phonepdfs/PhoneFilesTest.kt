package com.example.scanpro.feature.phonepdfs

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhoneFilesTest {
    private fun file(name: String, kind: PhoneFileKind = PhoneFiles.kindOf(null, name)!!, size: Long = 0, modified: Long = 0, folder: String = "") =
        PhoneFile(Uri.parse("content://media/external/file/$name"), name, kind, PhoneFiles.mimeForName(name), size, modified, folder)

    private val bill = file("Bill.pdf", size = 300, modified = 3_000, folder = "Download")
    private val alpha = file("alpha.PDF", size = 900, modified = 1_000, folder = "Documents/Work")
    private val photo = file("IMG_001.jpg", size = 500, modified = 5_000, folder = "DCIM/Camera")
    private val report = file("Report.docx", size = 100, modified = 2_000, folder = "Download")
    private val sheet = file("Budget.xlsx", size = 50, modified = 4_000)
    private val notes = file("notes.txt", size = 10, modified = 500)
    private val all = listOf(bill, alpha, photo, report, sheet, notes)

    @Test fun kindsComeFromMimeTypeFirst() {
        assertEquals(PhoneFileKind.PDF, PhoneFiles.kindOf("application/pdf", "whatever.bin"))
        assertEquals(PhoneFileKind.IMAGE, PhoneFiles.kindOf("image/heic", "x"))
        assertEquals(PhoneFileKind.WORD, PhoneFiles.kindOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "x"))
        assertEquals(PhoneFileKind.EXCEL, PhoneFiles.kindOf("text/csv", "x"))
        assertEquals(PhoneFileKind.POWERPOINT, PhoneFiles.kindOf("application/vnd.ms-powerpoint", "x"))
        assertEquals(PhoneFileKind.TEXT, PhoneFiles.kindOf("TEXT/PLAIN", "x"))
    }

    @Test fun kindsFallBackToTheExtension() {
        assertEquals(PhoneFileKind.PDF, PhoneFiles.kindOf("application/octet-stream", "scan.PDF"))
        assertEquals(PhoneFileKind.POWERPOINT, PhoneFiles.kindOf(null, "deck.pptx"))
        assertNull(PhoneFiles.kindOf(null, "song.mp3"))
        assertNull(PhoneFiles.kindOf("video/mp4", "clip.mp4"))
    }

    @Test fun filterByKind() {
        assertEquals(all, PhoneFiles.filter(all, PhoneFileFilter.ALL, ""))
        assertEquals(listOf(bill, alpha), PhoneFiles.filter(all, PhoneFileFilter.PDF, ""))
        assertEquals(listOf(photo), PhoneFiles.filter(all, PhoneFileFilter.IMAGES, ""))
        assertEquals(listOf(report), PhoneFiles.filter(all, PhoneFileFilter.WORD, ""))
        assertEquals(listOf(sheet), PhoneFiles.filter(all, PhoneFileFilter.EXCEL, ""))
        assertEquals(listOf(notes), PhoneFiles.filter(all, PhoneFileFilter.TEXT, ""))
        assertEquals(emptyList<PhoneFile>(), PhoneFiles.filter(all, PhoneFileFilter.POWERPOINT, ""))
    }

    @Test fun searchMatchesEveryWordInNameOrFolderIgnoringCase() {
        assertEquals(listOf(bill, report), PhoneFiles.filter(all, PhoneFileFilter.ALL, "download"))
        assertEquals(listOf(bill), PhoneFiles.filter(all, PhoneFileFilter.ALL, "  BILL  download "))
        assertEquals(listOf(alpha), PhoneFiles.filter(all, PhoneFileFilter.PDF, "work"))
        assertEquals(emptyList<PhoneFile>(), PhoneFiles.filter(all, PhoneFileFilter.IMAGES, "bill"))
    }

    @Test fun sortsByNameDateAndSize() {
        val pdfs = listOf(bill, alpha)
        assertEquals(listOf(alpha, bill), PhoneFiles.sort(pdfs, PhoneFileSort.NAME))
        assertEquals(listOf(bill, alpha), PhoneFiles.sort(pdfs, PhoneFileSort.NAME, ascending = false))
        assertEquals(listOf(bill, alpha), PhoneFiles.sort(pdfs, PhoneFileSort.DATE))
        assertEquals(listOf(alpha, bill), PhoneFiles.sort(pdfs, PhoneFileSort.SIZE))
        assertEquals(listOf(bill, alpha), PhoneFiles.sort(pdfs, PhoneFileSort.SIZE, ascending = true))
        assertTrue(PhoneFiles.defaultAscending(PhoneFileSort.NAME))
        assertFalse(PhoneFiles.defaultAscending(PhoneFileSort.DATE))
    }

    @Test fun homeShowsTheNewestFirst() {
        assertEquals(listOf(photo, sheet, bill), PhoneFiles.newest(all, 3))
        assertEquals(all.size, PhoneFiles.newest(all).size)
    }

    @Test fun selectionActions() {
        assertTrue(PhoneFiles.canMerge(listOf(bill, alpha)))
        assertFalse(PhoneFiles.canMerge(listOf(bill)))
        assertFalse(PhoneFiles.canMerge(listOf(bill, photo)))
        assertTrue(PhoneFiles.canMakePdfFromImages(listOf(photo)))
        assertFalse(PhoneFiles.canMakePdfFromImages(listOf(photo, bill)))
        assertFalse(PhoneFiles.canMakePdfFromImages(emptyList()))
    }

    @Test fun shareTypeIsTheCommonOne() {
        assertEquals("application/pdf", commonMimeType(listOf(bill, alpha)))
        assertEquals("image/*", commonMimeType(listOf(photo, file("b.png"))))
        assertEquals("*/*", commonMimeType(listOf(photo, bill)))
    }

    @Test fun walkSkipsHiddenPrivateAndDeepFolders() {
        assertTrue(PhoneFiles.shouldEnterDir(""))
        assertTrue(PhoneFiles.shouldEnterDir("Download"))
        assertTrue(PhoneFiles.shouldEnterDir("Android/media/com.whatsapp"))
        assertFalse(PhoneFiles.shouldEnterDir("Android/data"))
        assertFalse(PhoneFiles.shouldEnterDir("Android/obb/game"))
        assertFalse(PhoneFiles.shouldEnterDir(".thumbnails"))
        assertFalse(PhoneFiles.shouldEnterDir("Documents/.secret"))
        assertFalse(PhoneFiles.shouldEnterDir("LOST.DIR"))
        assertFalse(PhoneFiles.shouldEnterDir((1..13).joinToString("/") { "d$it" }))
        assertTrue(PhoneFiles.isListedName("a.pdf"))
        assertFalse(PhoneFiles.isListedName(".hidden.pdf"))
        assertFalse(PhoneFiles.isListedName("movie.mkv"))
        assertEquals("Download/Bills", PhoneFiles.folderOf("Download/Bills/x.pdf"))
        assertEquals("", PhoneFiles.folderOf("x.pdf"))
    }
}
