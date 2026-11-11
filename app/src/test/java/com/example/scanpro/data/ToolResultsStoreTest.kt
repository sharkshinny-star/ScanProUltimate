package com.example.scanpro.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tool outputs move out of the one-day cache into Your results, never overwrite each other, and only results can be deleted. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ToolResultsStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = ToolResultsStore(context)
    private val exports = File(context.cacheDir, "exports").apply { mkdirs() }

    private fun output(name: String, text: String = "%PDF-1.4 $name") = File(exports, name).apply { writeText(text) }

    @Test
    fun `a finished output moves out of the cache into the results folder`() {
        val made = output("report compressed.pdf")
        val kept = store.keep(made)
        assertFalse("left in the cache, where it is deleted after a day", made.exists())
        assertEquals(File(context.filesDir, ToolResultsStore.DIR).canonicalPath, kept.parentFile!!.canonicalPath)
        assertEquals("%PDF-1.4 report compressed.pdf", kept.readText())
        assertEquals(listOf(kept.name), store.items.value.map { it.name })
    }

    @Test
    fun `a second result with the same name gets a number instead of replacing the first`() {
        val first = store.keep(output("a.pdf", "first"))
        val second = store.keep(output("a.pdf", "second"))
        assertEquals("a.pdf", first.name)
        assertEquals("a (2).pdf", second.name)
        assertEquals("first", first.readText())
        assertEquals(2, store.items.value.size)
    }

    @Test
    fun `keeping twice does not copy a kept file again`() {
        val kept = store.keep(output("b.pdf"))
        assertEquals(kept, store.keep(kept))
        assertEquals(1, store.items.value.size)
    }

    @Test
    fun `delete removes a result but refuses a file outside the results folder`() {
        val kept = store.keep(output("c.pdf"))
        val elsewhere = File(context.filesDir, "documents/keep-me.pdf").apply { parentFile!!.mkdirs(); writeText("x") }
        assertFalse(store.delete(elsewhere))
        assertTrue(elsewhere.exists())
        assertTrue(store.delete(kept))
        assertTrue(store.items.value.isEmpty())
    }

    @Test
    fun `free names number before the extension and handle names without one`() {
        val dir = File(context.cacheDir, "names").apply { mkdirs() }
        File(dir, "x.tar.gz").writeText("")
        File(dir, "notes").writeText("")
        assertEquals("x.tar (2).gz", ToolResultsStore.freeName(dir, "x.tar.gz").name)
        assertEquals("notes (2)", ToolResultsStore.freeName(dir, "notes").name)
    }
}
