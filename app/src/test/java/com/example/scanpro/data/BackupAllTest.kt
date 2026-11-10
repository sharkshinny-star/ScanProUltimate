package com.example.scanpro.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import com.example.scanpro.backup.EncryptedDocumentPackage
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric

/**
 * A folder the user picked, reduced to the two calls the app makes on it: create a file, and delete one. Files live
 * in [dir]; [files] maps a document id to the name it was created with.
 */
class FakeTreeProvider : ContentProvider() {
    lateinit var dir: File
    val files = LinkedHashMap<String, String>()
    var refuseCreate = false
    var failOpenFor: (String) -> Boolean = { false }
    private var counter = 0

    override fun onCreate() = true

    @Suppress("DEPRECATION")
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? = when (method) {
        // A provider that refuses answers without a uri; a null reply would make DocumentsContract itself throw.
        "android:createDocument" -> if (refuseCreate) Bundle() else {
            val id = "doc${counter++}"
            files[id] = extras!!.getString("_display_name")!!
            Bundle().apply { putParcelable("uri", DocumentsContract.buildDocumentUri(AUTHORITY, id)) }
        }
        "android:deleteDocument" -> {
            val id = extras!!.getParcelable<Uri>("uri")!!.lastPathSegment!!
            files.remove(id)
            File(dir, id).delete()
            Bundle()
        }
        else -> null
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val id = uri.lastPathSegment!!
        if (failOpenFor(files[id].orEmpty())) throw FileNotFoundException("cannot write ${files[id]}")
        return ParcelFileDescriptor.open(File(dir, id), ParcelFileDescriptor.parseMode(mode))
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val AUTHORITY = "com.example.scanpro.test.tree"
    }
}

/** "Back up everything": what lands in the chosen folder, and what must never be left in it or in the cache. */
class BackupAllTest : RepositoryTestBase() {
    private val password = "correct horse"
    private lateinit var folder: FakeTreeProvider
    private val tree: Uri = DocumentsContract.buildTreeDocumentUri(FakeTreeProvider.AUTHORITY, "root")

    @Before fun setUpFolder() {
        folder = Robolectric.buildContentProvider(FakeTreeProvider::class.java).create(FakeTreeProvider.AUTHORITY).get()
        folder.dir = File(context.cacheDir, "picked-folder").apply { mkdirs() }
    }

    private fun cacheLeftovers() = context.cacheDir.listFiles().orEmpty().filter { it.name.startsWith("backup_") }

    private fun titleOf(id: String): String {
        val copy = File(context.cacheDir, "check_$id.scanpro")
        File(folder.dir, id).copyTo(copy, overwrite = true)
        val restored = EncryptedDocumentPackage.read(copy, File(context.cacheDir, "check_out_$id"), password.toCharArray())
        restored.tempDir.deleteRecursively()
        return restored.title
    }

    @Test fun writesOnePackagePerActiveDocumentAndReportsProgress() = runBlocking {
        val first = newDocument(2, title = "First")
        val second = newDocument(1, title = "Second")
        newDocument(1, title = "Gone", trashedAt = System.currentTimeMillis())
        val progress = mutableListOf<Pair<Int, Int>>()

        val written = repository.backupAll(tree, password) { done, total -> progress += done to total }

        assertEquals(2, written)
        assertEquals(listOf(1 to 2, 2 to 2), progress)
        assertEquals(2, folder.files.size)
        assertTrue(folder.files.values.all { it.endsWith(".scanpro") })
        assertEquals(setOf("First", "Second"), folder.files.keys.map(::titleOf).toSet())
        assertTrue(folder.files.values.none { it.startsWith("Gone") })
        assertTrue(cacheLeftovers().isEmpty())
        assertNotNull(first); assertNotNull(second)
    }

    @Test fun aFailedWriteDeletesTheEmptyFileAndLeavesNothingInTheCache() = runBlocking {
        newDocument(1, title = "First")
        newDocument(1, title = "Second")
        folder.failOpenFor = { it.startsWith("Second_") }

        try {
            repository.backupAll(tree, password)
            fail("expected the write error")
        } catch (expected: FileNotFoundException) {
        }

        assertTrue("the failed file was left in the folder", folder.files.values.none { it.startsWith("Second_") })
        assertTrue("an empty file was left in the folder", folder.files.keys.all { File(folder.dir, it).length() > 0 })
        assertTrue(cacheLeftovers().isEmpty())
    }

    @Test fun aFolderThatRefusesNewFilesFailsCleanly() = runBlocking {
        newDocument(1)
        folder.refuseCreate = true
        try {
            repository.backupAll(tree, password)
            fail("expected an error")
        } catch (expected: IllegalStateException) {
            assertEquals("Could not create a file in the chosen folder", expected.message)
        }
        assertTrue(folder.files.isEmpty())
        assertTrue(cacheLeftovers().isEmpty())
    }

    @Test fun aShortPasswordIsRefusedBeforeAnythingIsWritten() = runBlocking {
        newDocument(1)
        try {
            repository.backupAll(tree, "short")
            fail("expected an error")
        } catch (expected: IllegalArgumentException) {
        }
        assertTrue(folder.files.isEmpty())
        assertFalse(cacheLeftovers().any())
    }
}
