package com.example.scanpro.work

import com.example.scanpro.scanner.SESSION_PENDING_IMPORT
import com.example.scanpro.scanner.SESSION_ROOT
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CacheCleanerTest {
    @get:Rule val temp = TemporaryFolder()

    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000

    private fun session(root: File, name: String, ageMs: Long, pendingImport: Boolean = false): File =
        File(File(root, SESSION_ROOT), name).apply {
            mkdirs()
            File(this, "raw_1.jpg").writeText("photo")
            if (pendingImport) File(this, SESSION_PENDING_IMPORT).createNewFile()
            setLastModified(now - ageMs)
        }

    @Test
    fun `an abandoned session goes after a day`() {
        val files = temp.newFolder("files")
        val fresh = session(files, "fresh", ageMs = 2 * 60 * 60 * 1000)
        val old = session(files, "old", ageMs = 2 * day)
        CacheCleaner.purgeFolders(temp.newFolder("cache"), files, now)
        assertTrue(fresh.exists())
        assertFalse(old.exists())
    }

    @Test
    fun `a session waiting for its import is kept, up to a month`() {
        val files = temp.newFolder("files")
        val waiting = session(files, "waiting", ageMs = 10 * day, pendingImport = true)
        val forgotten = session(files, "forgotten", ageMs = 31 * day, pendingImport = true)
        CacheCleaner.purgeFolders(temp.newFolder("cache"), files, now)
        assertTrue(waiting.exists())
        assertFalse(forgotten.exists())
    }

    @Test
    fun `sessions left in the cache by older versions are still cleaned`() {
        val cache = temp.newFolder("cache")
        val legacy = session(cache, "legacy", ageMs = 2 * day)
        CacheCleaner.purgeFolders(cache, temp.newFolder("files"), now)
        assertFalse(legacy.exists())
    }

    @Test
    fun `only known temporary copies are removed from the cache, and only once stale`() {
        val cache = temp.newFolder("cache")
        val staleCopy = File(cache, "view_unlocked_1.pdf").apply { writeText("x"); setLastModified(now - 2 * 60 * 60 * 1000) }
        val freshCopy = File(cache, "view_unlocked_2.pdf").apply { writeText("x"); setLastModified(now - 60 * 1000) }
        val unrelated = File(cache, "keep_me.bin").apply { writeText("x"); setLastModified(now - 10 * day) }
        CacheCleaner.purgeFolders(cache, temp.newFolder("files"), now)
        assertFalse(staleCopy.exists())
        assertTrue(freshCopy.exists())
        assertTrue(unrelated.exists())
    }

    @Test
    fun `temp files the tools leave after a cancel are swept after an hour`() {
        val cache = temp.newFolder("cache")
        val leftovers = listOf("wm_in_1.pdf", "num_in_2.pdf", "resize_in_3.jpg", "crop_staged_4.jpg", "repair_5", "pdf2ppt_6", "PDFBox7.tmp")
            .map { name -> File(cache, name).apply { writeText("x"); setLastModified(now - 2 * 60 * 60 * 1000) } }
        val running = File(cache, "wm_in_running.pdf").apply { writeText("x"); setLastModified(now - 5 * 60 * 1000) }
        val unrelated = File(cache, "notes.txt").apply { writeText("x"); setLastModified(now - 2 * day) }
        CacheCleaner.purgeFolders(cache, temp.newFolder("files"), now)
        leftovers.forEach { assertFalse(it.name, it.exists()) }
        assertTrue("a run in progress keeps its file", running.exists())
        assertTrue("files the tools did not make are left alone", unrelated.exists())
    }
}
