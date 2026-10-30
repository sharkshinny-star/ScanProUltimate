package com.example.scanpro.backup

import com.example.scanpro.data.AnnotationType
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.data.OcrScript
import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageAnnotation
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ScanDocument
import com.example.scanpro.data.ScanMode
import com.example.scanpro.data.ScanPage
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Backup and restore, end to end on the JVM: the format, the editable settings, and what a bad or hostile file does. */
class EncryptedDocumentPackageTest {
    @get:Rule val tmp = TemporaryFolder()
    private val password = "correct horse"

    private fun bytes(seed: Int, size: Int) = ByteArray(size) { ((it * 31 + seed) % 251).toByte() }

    private val crop = CropQuad(
        topLeft = NormalizedPoint(0.1f, 0.2f), topRight = NormalizedPoint(0.9f, 0.15f),
        bottomRight = NormalizedPoint(0.95f, 0.85f), bottomLeft = NormalizedPoint(0.05f, 0.9f)
    )
    private val adjustments = PageAdjustments(brightness = 0.2f, contrast = 1.1f, saturation = 1.3f, removeShadows = true, removeFingers = false)

    private fun document(): ScanDocument {
        val signature = tmp.newFile("signature.png").also { it.writeBytes(bytes(9, 300)) }
        val pages = (0 until 3).map { i ->
            val image = tmp.newFile("page$i.jpg").also { it.writeBytes(bytes(i, 2_000 + i)) }
            val source = tmp.newFile("source$i").also { it.writeBytes(bytes(10 + i, 3_000 + i)) }
            ScanPage(
                id = "p$i", documentId = "d", position = i,
                sourcePath = source.absolutePath, imagePath = image.absolutePath,
                ocrText = "text of page $i", filter = if (i == 1) PageFilter.BW else PageFilter.COLOR,
                rotationDegrees = 90 * i, crop = crop, adjustments = adjustments,
                annotations = if (i != 0) emptyList() else listOf(
                    PageAnnotation(id = "t1", type = AnnotationType.TEXT, x = 0.1f, y = 0.2f, width = 0.3f, height = 0.1f, text = "Hello"),
                    PageAnnotation(id = "s1", type = AnnotationType.SIGNATURE, x = 0.5f, y = 0.6f, width = 0.2f, height = 0.1f, assetPath = signature.absolutePath)
                )
            )
        }
        return ScanDocument(
            id = "d", title = "Lease agreement", createdAt = 1, updatedAt = 2, pdfPath = "", pages = pages, sizeBytes = 0,
            tags = listOf("home", "legal"), folderName = "Housing",
            pdfQuality = PdfQuality.entries.first { it != PdfQuality.BALANCED }, pdfPageSize = PdfPageSize.entries.last(),
            searchablePdf = false, pageNumbers = true, scanMode = ScanMode.RECEIPT, ocrScript = OcrScript.entries.last()
        )
    }

    private fun exported(doc: ScanDocument = document(), pass: String = password): File {
        val file = File(tmp.root, "backup-${System.nanoTime()}.scanpro")
        EncryptedDocumentPackage.write(doc, pass.toCharArray(), file)
        return file
    }

    /** Restore reads and then deletes the file it is given, so each attempt gets its own copy. */
    private fun restore(backup: File, pass: String = password, outDir: File = File(tmp.root, "restore-${System.nanoTime()}")): EncryptedDocumentPackage.RestoredDocument {
        val copy = File(tmp.root, "copy-${System.nanoTime()}.scanpro").also { backup.copyTo(it) }
        return EncryptedDocumentPackage.read(copy, outDir, pass.toCharArray())
    }

    private fun assertRestoreFails(backup: File, pass: String = password, expectedCause: String? = null): File {
        val outDir = File(tmp.root, "restore-${System.nanoTime()}")
        try {
            restore(backup, pass, outDir)
            fail("the restore should have been refused")
        } catch (expected: IllegalArgumentException) {
            if (expectedCause != null) {
                assertTrue("cause was: ${expected.cause}", expected.cause?.message.orEmpty().contains(expectedCause))
            }
        }
        assertFalse("the half-restored files are removed", outDir.exists())
        return outDir
    }

    @Test
    fun `a restored document keeps everything it was backed up with`() {
        val original = document()
        val restored = restore(exported(original))

        assertEquals("Lease agreement", restored.title)
        assertEquals("Housing", restored.folderName)
        assertEquals(listOf("home", "legal"), restored.tags)
        assertEquals(original.pdfQuality, restored.quality)
        assertEquals(original.pdfPageSize, restored.pageSize)
        assertEquals(false, restored.searchablePdf)
        assertEquals(true, restored.pageNumbers)
        assertEquals(ScanMode.RECEIPT, restored.scanMode)
        assertEquals(original.ocrScript, restored.ocrScript)
        assertEquals(3, restored.pages.size)

        restored.pages.forEachIndexed { i, page ->
            val source = original.pages[i]
            assertArrayEquals("page $i image", File(source.imagePath).readBytes(), page.file.readBytes())
            assertArrayEquals("page $i original", File(source.sourcePath).readBytes(), page.sourceFile!!.readBytes())
            assertEquals("text of page $i", page.ocrText)
            assertEquals(source.filter, page.filter)
            assertEquals(90 * i, page.rotationDegrees)
            assertEquals(crop, page.crop)
            assertEquals(adjustments, page.adjustments)
        }
    }

    @Test
    fun `annotations come back and a signature image travels with the backup`() {
        val original = document()
        val restored = restore(exported(original))
        val annotations = restored.pages[0].annotations
        assertEquals(2, annotations.size)

        val text = annotations.single { it.type == AnnotationType.TEXT }
        assertEquals("Hello", text.text)

        val signature = annotations.single { it.type == AnnotationType.SIGNATURE }
        val originalSignature = File(original.pages[0].annotations.single { it.type == AnnotationType.SIGNATURE }.assetPath)
        assertNotEquals("the signature is a copy inside the restore folder", originalSignature.absolutePath, signature.assetPath)
        assertTrue(File(signature.assetPath).canonicalPath.startsWith(restored.tempDir.canonicalPath))
        assertArrayEquals(originalSignature.readBytes(), File(signature.assetPath).readBytes())
        assertTrue(restored.pages[1].annotations.isEmpty())
    }

    @Test
    fun `the backup does not contain the page text in the clear`() {
        val bytes = exported().readBytes()
        val haystack = String(bytes, Charsets.ISO_8859_1)
        assertFalse(haystack.contains("Lease agreement"))
        assertFalse(haystack.contains("text of page 0"))
        assertFalse(haystack.contains("manifest.json"))
    }

    @Test
    fun `a wrong password is refused and leaves nothing behind`() {
        assertRestoreFails(exported(), pass = "not the password")
    }

    @Test
    fun `one changed byte anywhere in the encrypted body is detected`() {
        val backup = exported()
        val data = backup.readBytes()
        for (position in listOf(data.size / 2, data.size - 3)) {
            val tampered = File(tmp.root, "tampered-$position.scanpro")
            tampered.writeBytes(data.copyOf().also { it[position] = (it[position].toInt() xor 0x01).toByte() })
            assertRestoreFails(tampered)
        }
    }

    @Test
    fun `a truncated backup is refused`() {
        val data = exported().readBytes()
        val truncated = File(tmp.root, "truncated.scanpro").also { it.writeBytes(data.copyOf(data.size - 40)) }
        assertRestoreFails(truncated)
    }

    @Test
    fun `a file that is not a backup is refused`() {
        val junk = File(tmp.root, "junk.scanpro").also { it.writeBytes(bytes(3, 5_000)) }
        assertRestoreFails(junk, expectedCause = "Not a ScanPro encrypted package")
    }

    @Test
    fun `short passwords are refused for backup and for restore`() {
        val doc = document()
        val target = File(tmp.root, "short.scanpro")
        try {
            EncryptedDocumentPackage.write(doc, "12345".toCharArray(), target)
            fail("a five-character password should be refused")
        } catch (expected: IllegalArgumentException) { /* the requirement is six */ }
        assertFalse(target.exists())
        try {
            restore(exported(doc), pass = "12345")
            fail("a five-character password should be refused")
        } catch (expected: IllegalArgumentException) { /* same rule on the way back */ }
    }

    @Test
    fun `a header asking for an absurd number of rounds is refused before any key is made`() {
        val file = craft(password, iterations = 10, entries = listOf("manifest.json" to "{}".toByteArray()))
        assertRestoreFails(file, expectedCause = "Invalid backup header")
    }

    @Test
    fun `an entry that tries to leave the restore folder is refused`() {
        val outside = File(tmp.root, "escape.txt")
        val file = craft(password, entries = listOf("../escape.txt" to "pwned".toByteArray(), "manifest.json" to "{}".toByteArray()))
        assertRestoreFails(file, expectedCause = "Unsafe backup entry")
        assertFalse("nothing was written outside", outside.exists())
    }

    @Test
    fun `a manifest pointing outside the restore folder is refused`() {
        val manifest = """{"title":"x","pages":[{"file":"../../outside.jpg"}]}"""
        val file = craft(password, entries = listOf("manifest.json" to manifest.toByteArray(), "pages/0000.jpg" to bytes(1, 10)))
        assertRestoreFails(file, expectedCause = "Unsafe backup path")
    }

    @Test
    fun `a backup with no pages is refused`() {
        val file = craft(password, entries = listOf("manifest.json" to """{"title":"empty","pages":[]}""".toByteArray()))
        assertRestoreFails(file, expectedCause = "Backup contains no pages")
    }

    // ---- building packages by hand, with the same container the app writes ----------------------------

    private fun craft(pass: String, iterations: Int = 210_000, entries: List<Pair<String, ByteArray>>): File {
        val file = File(tmp.root, "crafted-${System.nanoTime()}.scanpro")
        val salt = ByteArray(16) { it.toByte() }
        val prefix = ByteArray(ChunkedAead.PREFIX_BYTES) { (it + 1).toByte() }
        val spec = PBEKeySpec(pass.toCharArray(), salt, maxOf(iterations, 1_000), 256)
        val key = SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        FileOutputStream(file).use { raw ->
            val header = DataOutputStream(BufferedOutputStream(raw))
            header.write(byteArrayOf('S'.code.toByte(), 'P'.code.toByte(), 'K'.code.toByte(), '2'.code.toByte()))
            header.writeInt(iterations)
            header.write(salt)
            header.write(prefix)
            header.flush()
            ChunkedAead.Writer(raw, key, prefix).use { encrypted ->
                ZipOutputStream(BufferedOutputStream(encrypted)).use { zip ->
                    entries.forEach { (name, content) ->
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(content)
                        zip.closeEntry()
                    }
                }
            }
        }
        return file
    }
}
