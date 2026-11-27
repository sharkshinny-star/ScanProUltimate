package com.example.scanpro.feature.phonepdfs

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** MediaStore's Files collection, as far as PhonePdfSource asks it: rows filtered by the MIME types in the selection. */
class FakeMediaProvider : ContentProvider() {
    var lastSelection: String? = null
    val rows = mutableListOf<Array<Any?>>()

    override fun onCreate() = true
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        lastSelection = selection
        val cursor = MatrixCursor(projection)
        val wanted = selectionArgs.orEmpty().toSet()
        // Rows are [id, name, size, modified seconds, mime, relative path]; columns follow the projection's order.
        rows.filter { it[4] in wanted }.sortedByDescending { it[3] as Long }.forEach { cursor.addRow(it) }
        return cursor
    }
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhonePdfSourceTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun queryAsksMediaStoreByMimeTypeAndMapsRows() {
        val provider = Robolectric.buildContentProvider(FakeMediaProvider::class.java).create(MediaStore.AUTHORITY).get()
        provider.rows += arrayOf<Any?>(1L, "Bill.pdf", 2048L, 1_700_000_000L, "application/pdf", "Download/")
        provider.rows += arrayOf<Any?>(2L, "photo.jpg", 4096L, 1_700_000_500L, "image/jpeg", "DCIM/Camera/")
        provider.rows += arrayOf<Any?>(3L, ".hidden.pdf", 1L, 1_700_000_100L, "application/pdf", "Download/")
        provider.rows += arrayOf<Any?>(4L, "Report.docx", 10L, 1_600_000_000L,
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Documents/Work/")
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver

        val docs = PhonePdfSource.query(resolver, listOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"), 10)

        assertTrue(provider.lastSelection.orEmpty().contains("mime_type IN (?,?)"))
        assertEquals(listOf("Bill.pdf", "Report.docx"), docs.map { it.name })
        val bill = docs.first()
        assertEquals(PhoneFileKind.PDF, bill.kind)
        assertEquals("Download", bill.folder)
        assertEquals(2048L, bill.size)
        assertEquals(1_700_000_000_000L, bill.modified)
        assertEquals(Uri.parse("content://media/external/file/1"), bill.uri)
        assertEquals(PhoneFileKind.WORD, docs[1].kind)
        assertEquals(1, PhonePdfSource.query(resolver, listOf("application/pdf", "image/jpeg"), 1).size)
    }

    @Test fun walkFindsListedFilesAndSkipsPrivateAndHiddenFolders() = runBlocking {
        val root = temp.newFolder("storage")
        fun put(path: String) = File(root, path).apply { parentFile!!.mkdirs(); writeText("x") }
        put("Download/Bill.pdf")
        put("Documents/Work/Report.docx")
        put("DCIM/Camera/IMG_1.jpg")
        put("Music/song.mp3")
        put("Android/data/com.other.app/files/secret.pdf")
        put(".hidden/private.pdf")
        put("Download/.partial.pdf")

        val found = PhonePdfSource.walk(root)

        assertEquals(setOf("Bill.pdf", "Report.docx", "IMG_1.jpg"), found.map { it.name }.toSet())
        val bill = found.single { it.name == "Bill.pdf" }
        assertEquals("Download", bill.folder)
        assertEquals("file", bill.uri.scheme)
        assertEquals(File(root, "Download/Bill.pdf").absolutePath, bill.path)
    }

    @Test fun nothingIsListedWithoutAccess() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(false, PhonePdfSource.hasAccess(context))
        assertEquals(emptyList<PhoneFile>(), PhonePdfSource.load(context))
    }
}
