package com.example.scanpro.backup

import com.example.scanpro.R
import com.example.scanpro.core.text.UserFacingException
import com.example.scanpro.data.AnnotationType
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.data.OcrWord
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
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject
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
                ocrEdited = i == 2, ocrWords = if (i == 2) emptyList() else listOf(OcrWord("page", 0.1f, 0.2f, 0.3f, 0.25f)),
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
            // A hand correction must survive, or the next edit re-runs OCR over it.
            assertEquals(source.ocrEdited, page.ocrEdited)
            assertEquals(source.ocrWords, page.ocrWords)
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
    fun `a wrong password says so, while a cut-off file does not`() {
        val backup = exported()
        val wrong = try {
            restore(backup, "not the password", File(tmp.root, "wrong-pass"))
            null
        } catch (expected: UserFacingException) {
            expected
        }
        assertEquals(R.string.err_backup_wrong_password, wrong!!.messageRes)

        val data = backup.readBytes()
        val cut = File(tmp.root, "cut.scanpro").also { it.writeBytes(data.copyOf(data.size - 40)) }
        val damaged = try {
            restore(cut, password, File(tmp.root, "cut-out"))
            null
        } catch (expected: UserFacingException) {
            expected
        }
        assertEquals(R.string.err_could_not_decrypt_backup, damaged!!.messageRes)
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
        } catch (expected: IllegalArgumentException) { /* the requirement is eight */ }
        assertFalse(target.exists())
        try {
            restore(exported(doc), pass = "12345")
            fail("a five-character password should be refused")
        } catch (expected: IllegalArgumentException) { /* restore accepts six (older backups), never fewer */ }
    }

    @Test
    fun `new backups need eight characters and use 600k rounds`() {
        val doc = document()
        val target = File(tmp.root, "seven.scanpro")
        try {
            EncryptedDocumentPackage.write(doc, "1234567".toCharArray(), target)
            fail("a seven-character password should be refused for a new backup")
        } catch (expected: IllegalArgumentException) { /* the requirement is eight */ }
        assertFalse(target.exists())
        val header = DataInputStream(exported(doc).inputStream()).use { it.skipBytes(4); it.readInt() }
        assertEquals(EncryptedDocumentPackage.ITERATIONS, header)
        assertEquals(600_000, header)
    }

    @Test
    fun `a backup made with a six-character password and 210k rounds still restores`() {
        val manifest = """{"title":"old","pages":[{"file":"pages/0000.jpg","ocr":"hi"}]}"""
        val file = craft("123456", iterations = 210_000, entries = listOf("manifest.json" to manifest.toByteArray(), "pages/0000.jpg" to bytes(1, 10)))
        val restored = restore(file, pass = "123456")
        assertEquals("old", restored.title)
        assertEquals("hi", restored.pages.single().ocrText)
    }

    @Test
    fun `a backup with too many entries is refused`() {
        val entries = (0..20_000).map { "e/$it" to ByteArray(0) } + ("manifest.json" to "{}".toByteArray())
        val file = craft(password, entries = entries)
        val outDir = File(tmp.root, "restore-many")
        try {
            restore(file, outDir = outDir)
            fail("a backup with more than 20,000 entries should be refused")
        } catch (expected: IllegalArgumentException) {
            assertEquals(R.string.err_backup_too_large, (expected as UserFacingException).messageRes)
        }
        assertFalse("the half-restored files are removed", outDir.exists())
    }

    @Test
    fun `an oversized version 1 backup is refused before it is decrypted`() {
        // Version 1 is one GCM stream, which Android buffers whole before releasing any plaintext, so a huge
        // one must be refused on its size alone. The limit follows this machine's heap, so the file is made one
        // byte past whatever it is. The file is sparse: only the header is really written.
        val file = File(tmp.root, "legacy-huge.scanpro")
        FileOutputStream(file).use { raw ->
            val header = DataOutputStream(BufferedOutputStream(raw))
            header.write(byteArrayOf('S'.code.toByte(), 'P'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte()))
            header.writeInt(100_000)
            header.write(ByteArray(16) { it.toByte() })
            header.flush()
        }
        java.io.RandomAccessFile(file, "rw").use { it.setLength(EncryptedDocumentPackage.legacyPackageLimit() + 1) }
        val outDir = File(tmp.root, "restore-legacy-huge")
        try {
            EncryptedDocumentPackage.read(file, outDir, password.toCharArray())
            fail("a version 1 backup over the limit should be refused")
        } catch (expected: UserFacingException) {
            assertEquals(R.string.err_backup_too_large, expected.messageRes)
        }
        assertFalse("the restore folder is removed", outDir.exists())
    }

    @Test
    fun `a wrong password on a version 1 backup says so`() {
        // Version 1 is one GCM stream: a wrong key fails its tag check, which surfaces as an authentication error
        // inside an IOException, not as the version 2 "wrong key" error, and used to read as a generic failure.
        val salt = ByteArray(16) { (it + 1).toByte() }
        val iv = ByteArray(12) { (it + 40).toByte() }
        val iterations = 100_000
        val sealingKey = SecretKeySpec(
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(PBEKeySpec("the right password".toCharArray(), salt, iterations, 256)).encoded,
            "AES"
        )
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(javax.crypto.Cipher.ENCRYPT_MODE, sealingKey, javax.crypto.spec.GCMParameterSpec(128, iv))
        }
        val file = File(tmp.root, "legacy-v1.scanpro")
        FileOutputStream(file).use { raw ->
            val header = DataOutputStream(BufferedOutputStream(raw))
            header.write(byteArrayOf('S'.code.toByte(), 'P'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte()))
            header.writeInt(iterations)
            header.write(salt)
            header.write(iv)
            header.write(cipher.doFinal(bytes(7, 4096)))
            header.flush()
        }
        val outDir = File(tmp.root, "restore-legacy-wrong")
        try {
            EncryptedDocumentPackage.read(file, outDir, "not the password".toCharArray())
            fail("a wrong password should be refused")
        } catch (expected: UserFacingException) {
            assertEquals(R.string.err_backup_wrong_password, expected.messageRes)
        }
        assertFalse("the restore folder is removed", outDir.exists())
    }

    @Test
    fun `a full phone is reported as not enough space, not as a bad password or file`() {
        val full = java.io.IOException("write failed: ENOSPC (No space left on device)")
        assertTrue(EncryptedDocumentPackage.isOutOfSpace(full))
        // Found anywhere in the chain of causes, whichever wrapper the unzip or the copy put around it.
        assertTrue(EncryptedDocumentPackage.isOutOfSpace(java.io.IOException("copy failed", RuntimeException("x", full))))
        val mapped = EncryptedDocumentPackage.asUserFacing(full)
        assertTrue(mapped is UserFacingException)
        assertEquals(R.string.err_backup_no_space, (mapped as UserFacingException).messageRes)
        assertTrue("the original error stays as the cause", mapped.cause === full)

        // Anything else is passed on untouched.
        val damaged = java.io.IOException("Backup is damaged")
        assertFalse(EncryptedDocumentPackage.isOutOfSpace(damaged))
        assertTrue(EncryptedDocumentPackage.asUserFacing(damaged) === damaged)
    }

    @Test
    fun `copying a picked backup stops at the size limit`() {
        val out = ByteArrayOutputStream()
        EncryptedDocumentPackage.copyCapped(ByteArray(100).inputStream(), out, limit = 100)
        assertEquals(100, out.size())
        try {
            EncryptedDocumentPackage.copyCapped(ByteArray(101).inputStream(), ByteArrayOutputStream(), limit = 100)
            fail("a file over the limit should be refused")
        } catch (expected: IllegalArgumentException) { /* too large */ }
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
    fun `a directory entry cannot escape into a sibling with the same prefix`() {
        val outDir = File(tmp.root, "restore")
        val sibling = File(tmp.root, "restore-sibling")
        val file = craft(password, entries = listOf("../restore-sibling/" to byteArrayOf()))
        try {
            restore(file, outDir = outDir)
            fail("unsafe directory entry accepted")
        } catch (expected: UserFacingException) {
            assertTrue(expected.cause?.message.orEmpty().contains("Unsafe backup entry"))
        }
        assertFalse(sibling.exists())
        assertFalse(outDir.exists())
    }

    @Test
    fun `a manifest pointing outside the restore folder is refused`() {
        val manifest = """{"title":"x","pages":[{"file":"../../outside.jpg"}]}"""
        val file = craft(password, entries = listOf("manifest.json" to manifest.toByteArray(), "pages/0000.jpg" to bytes(1, 10)))
        assertRestoreFails(file, expectedCause = "Unsafe backup path")
    }

    @Test
    fun `annotation file paths from a backup never point outside it`() {
        val annotations = JSONArray()
            .put(JSONObject().put("type", "TEXT").put("text", "hi").put("asset", "/data/data/com.scanpro.app/databases/library.db"))
            .put(JSONObject().put("type", "SIGNATURE").put("asset", "../../outside.png"))
            .toString()
        val page = JSONObject().put("file", "pages/0000.jpg").put("source", "sources/0000").put("annotations", annotations)
        val manifest = JSONObject().put("title", "x").put("pages", JSONArray().put(page)).toString()
        val file = craft(password, entries = listOf(
            "manifest.json" to manifest.toByteArray(), "pages/0000.jpg" to bytes(1, 10), "sources/0000" to bytes(2, 10)
        ))
        val restored = restore(file).pages.single().annotations
        // The text annotation survives without its path; the signature pointing outside the backup is dropped.
        assertEquals(listOf(AnnotationType.TEXT), restored.map { it.type })
        assertEquals("", restored.single().assetPath)
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
