package com.example.scanpro.feature.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A multi-file tool result is shared as its files, never by writing outside the share folder (F146). */
class ResultShareTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun zipOf(vararg entries: Pair<String, String>): File {
        val zip = tmp.newFile("pages.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            entries.forEach { (name, body) ->
                out.putNextEntry(ZipEntry(name)); out.write(body.toByteArray()); out.closeEntry()
            }
        }
        return zip
    }

    @Test
    fun unpacksEveryFileInOrder() {
        val cache = tmp.newFolder("cache")
        val parts = unpackForSharing(cache, zipOf("page_1.jpg" to "a", "page_2.jpg" to "b"))!!
        assertEquals(listOf("page_1.jpg", "page_2.jpg"), parts.map { it.name })
        assertEquals("b", parts[1].readText())
        assertTrue(parts.all { it.parentFile == File(cache, "exports/shared_parts/pages") })
    }

    @Test
    fun entryNamesCannotEscapeTheFolder() {
        val cache = tmp.newFolder("cache")
        val parts = unpackForSharing(cache, zipOf("../../evil.pdf" to "x"))!!
        assertEquals(File(cache, "exports/shared_parts/pages/evil.pdf"), parts.single())
        assertTrue(!File(cache, "evil.pdf").exists() && !File(tmp.root, "evil.pdf").exists())
    }

    @Test
    fun tooManyFilesFallsBackToTheZip() {
        val cache = tmp.newFolder("cache")
        val entries = (1..MAX_SHARED_PARTS + 1).map { "p$it.png" to "x" }.toTypedArray()
        assertNull(unpackForSharing(cache, zipOf(*entries)))
    }
}
