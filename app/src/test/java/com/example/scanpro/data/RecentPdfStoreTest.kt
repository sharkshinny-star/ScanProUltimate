package com.example.scanpro.data

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A picked PDF whose provider gives no lasting read grant is kept as a private copy, so the Recents row still opens
 * after the screen it came from has closed (the report: "from Recents, once viewed, the pages won't open again").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentPdfStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pdf = "%PDF-1.4 test".toByteArray()

    private fun provider(path: String): Uri {
        val uri = "content://com.example.provider/doc/$path".toUri()
        shadowOf(context.contentResolver).registerInputStream(uri, ByteArrayInputStream(pdf))
        return uri
    }

    @Test
    fun `a file without a lasting grant is kept as a readable private copy`() {
        val store = RecentPdfStore(context).apply { grantKept = { false } }
        val uri = provider("a.pdf")
        store.add(uri, "a.pdf")
        val kept = store.items.value.single()
        assertEquals("file", kept.uri.scheme)
        assertTrue(store.canRead(kept.uri))
        assertEquals(pdf.size.toLong(), File(kept.uri.path!!).length())
    }

    @Test
    fun `removing a row deletes its copy`() {
        val store = RecentPdfStore(context).apply { grantKept = { false } }
        store.add(provider("b.pdf"), "b.pdf")
        val kept = store.items.value.single()
        store.remove(kept.uri)
        assertFalse(File(kept.uri.path!!).exists())
        assertTrue(store.items.value.isEmpty())
    }

    @Test
    fun `a file with a lasting grant is kept by its own link`() {
        val store = RecentPdfStore(context).apply { grantKept = { true } }
        val uri = provider("c.pdf")
        store.add(uri, "c.pdf")
        assertEquals(uri, store.items.value.single().uri)
    }

    @Test
    fun `an unreadable link is reported as unreadable`() {
        val store = RecentPdfStore(context)
        assertFalse(store.canRead("content://com.example.gone/doc/none.pdf".toUri()))
    }
}
