package com.example.scanpro.data

import android.net.Uri
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** importEncryptedBackup: a backup written by the app comes back as a new, complete document. */
class ImportBackupTest : RepositoryTestBase() {
    private val password = "correct horse"

    private fun documentFolders() = File(context.filesDir, "documents").list().orEmpty().toSet()

    private fun backupOf(doc: ScanDocument): Uri =
        Uri.fromFile(runBlocking { repository.exportEncryptedBackup(doc.id, password) })

    @Test fun aBackupRestoresAsANewDocumentWithTitleTextAndPagesInOrder() = runBlocking {
        val original = newDocument(3, title = "Tax 2025")
        val backup = backupOf(original)
        val restored = repository.importEncryptedBackup(backup, password)
        assertNotEquals(original.id, restored.id)
        assertEquals("Tax 2025", restored.title)
        assertEquals(listOf("text 0", "text 1", "text 2"), restored.pages.sortedBy { it.position }.map { it.ocrText })
        assertTrue(restored.pages.all { File(it.imagePath).exists() && File(it.sourcePath).exists() })
        assertTrue(File(restored.pdfPath).exists())
        assertEquals(restored.id, load(restored.id)!!.id)
        // The original is untouched.
        assertEquals(original.order(), load(original.id)!!.order())
    }

    @Test fun restoringTwiceMakesTwoSeparateDocuments() = runBlocking {
        val backup = backupOf(newDocument(1))
        val first = repository.importEncryptedBackup(backup, password)
        val second = repository.importEncryptedBackup(backup, password)
        assertNotEquals(first.id, second.id)
        assertNotEquals(first.pages.single().imagePath, second.pages.single().imagePath)
    }

    @Test fun aWrongPasswordRestoresNothingAndLeavesNoFolderBehind() = runBlocking {
        val backup = backupOf(newDocument(1))
        val foldersBefore = documentFolders()
        try {
            repository.importEncryptedBackup(backup, "another password")
            fail("expected a wrong-password error")
        } catch (expected: Exception) {
        }
        assertEquals(foldersBefore, documentFolders())
    }

    @Test fun aTooShortPasswordIsRefusedBeforeTheFileIsRead() = runBlocking {
        val foldersBefore = documentFolders()
        try {
            repository.importEncryptedBackup(Uri.fromFile(File(context.cacheDir, "missing.scanpro")), "short")
            fail("expected a password error")
        } catch (expected: Exception) {
            assertFalse(expected is java.io.FileNotFoundException)
        }
        assertEquals(foldersBefore, documentFolders())
    }

    @Test fun aFileThatIsNotABackupIsRefusedAndCleanedUp() = runBlocking {
        val junk = File(context.cacheDir, "junk.scanpro").apply { writeBytes(ByteArray(300) { it.toByte() }) }
        val foldersBefore = documentFolders()
        try {
            repository.importEncryptedBackup(Uri.fromFile(junk), password)
            fail("expected a bad-file error")
        } catch (expected: Exception) {
        }
        assertEquals(foldersBefore, documentFolders())
    }

    @Test fun aFailureWhileBuildingThePdfRemovesTheRestoredFolder() = runBlocking {
        val backup = backupOf(newDocument(1))
        val foldersBefore = documentFolders()
        repository.pdfWriter = { _, _, _ -> throw java.io.IOException("disk full") }
        try {
            repository.importEncryptedBackup(backup, password)
            fail("expected the PDF error")
        } catch (expected: java.io.IOException) {
        }
        assertEquals(foldersBefore, documentFolders())
    }
}
