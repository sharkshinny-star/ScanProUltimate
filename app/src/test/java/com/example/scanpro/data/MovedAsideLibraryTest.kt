package com.example.scanpro.data

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offering a library that an older build set aside back to the newer build that can read it. */
class MovedAsideLibraryTest : RepositoryTestBase() {
    private val mainName = "scanpro-room.db"

    private fun file(name: String) = context.getDatabasePath(name).also { it.parentFile?.mkdirs() }

    /** Writes [documents] into their own library file and moves it aside, as an older build does on a downgrade. */
    private fun moveAside(version: Int, vararg documents: ScanDocument) {
        val source = DocumentDatabase(context, "aside-source.db")
        runBlocking(Dispatchers.IO) {
            documents.forEach { source.saveDocument(it) }
            source.close()
        }
        renameDatabaseFiles(context, "aside-source.db", "$mainName.newer-v$version")
    }

    private fun forgetInCurrentLibrary(id: String) = runBlocking(Dispatchers.IO) { db.deleteDocument(id) }

    @Test fun onlyReadableMainFilesAreOfferedNewestFirst() {
        for (name in listOf("$mainName.newer-v2", "$mainName.newer-v3", "$mainName.newer-v4", "$mainName.newer-v3-wal", "$mainName.newer-vx")) {
            file(name).writeBytes(ByteArray(4))
        }
        val found = findMovedAsideLibraries(context, mainName, currentVersion = 3)
        assertEquals(listOf(3, 2), found.map { it.version })
        assertEquals(listOf("$mainName.newer-v3", "$mainName.newer-v2"), found.map { it.fileName })
    }

    @Test fun nothingIsOfferedWhenNothingWasMovedAside() = runBlocking {
        assertNull(repository.movedAsideLibrary())
    }

    @Test fun restoreBringsBackDocumentsTheLibraryDoesNotHave() = runBlocking {
        val lost = newDocument(2, title = "Lost")
        moveAside(3, lost)
        forgetInCurrentLibrary(lost.id)
        assertNull(load(lost.id))

        val offered = repository.movedAsideLibrary()
        assertNotNull(offered)
        assertEquals(1, repository.restoreMovedAsideLibrary(offered!!))

        val back = load(lost.id)!!
        assertEquals("Lost", back.title)
        assertEquals(lost.order(), back.order())
        // The restored file is renamed, not deleted, and no longer offered.
        assertNull(repository.movedAsideLibrary())
        assertTrue(file(mainName).parentFile!!.list().orEmpty().any { it.startsWith("$mainName.restored-v3") })
    }

    @Test fun restoreNeverOverwritesOrDuplicatesAnExistingDocument() = runBlocking {
        val current = newDocument(1, title = "Current title")
        moveAside(3, current.copy(title = "Old title"))

        assertEquals(0, repository.restoreMovedAsideLibrary(repository.movedAsideLibrary()!!))
        assertEquals("Current title", load(current.id)!!.title)
    }

    @Test fun aDocumentWhoseFilesAreGoneIsSkipped() = runBlocking {
        val gone = newDocument(1, title = "Gone")
        moveAside(3, gone)
        forgetInCurrentLibrary(gone.id)
        File(gone.pdfPath).parentFile!!.deleteRecursively()

        assertEquals(0, repository.restoreMovedAsideLibrary(repository.movedAsideLibrary()!!))
        assertNull(load(gone.id))
    }

    @Test fun decliningKeepsTheFilesButStopsTheOffer() = runBlocking {
        file("$mainName.newer-v3").writeBytes(ByteArray(8))
        file("$mainName.newer-v3-wal").writeBytes(ByteArray(2))
        repository.declineMovedAsideLibrary(repository.movedAsideLibrary()!!)

        assertNull(repository.movedAsideLibrary())
        val names = file(mainName).parentFile!!.list().orEmpty()
        assertTrue(names.any { it.startsWith("$mainName.declined-v3-") && !it.endsWith("-wal") })
        assertTrue(names.any { it.startsWith("$mainName.declined-v3-") && it.endsWith("-wal") })
        assertFalse(names.any { it.startsWith("$mainName.newer-v3") })
    }

    @Test fun notNowHidesTheOfferForTheRestOfTheSessionOnly() = runBlocking {
        file("$mainName.newer-v3").writeBytes(ByteArray(8))
        assertNotNull(repository.movedAsideLibrary())
        repository.deferMovedAsideLibrary()
        assertNull(repository.movedAsideLibrary())
        assertTrue(file("$mainName.newer-v3").exists())
    }
}
