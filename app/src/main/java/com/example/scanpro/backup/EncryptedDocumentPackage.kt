package com.example.scanpro.backup

import android.content.Context
import android.net.Uri
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.OcrScript
import com.example.scanpro.data.ScanDocument
import com.example.scanpro.data.ScanMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-encrypted, portable ScanPro package. Visible page output + OCR metadata are preserved. */
class EncryptedDocumentPackage(private val context: Context) {
    data class RestoredPage(val file: File, val ocrText: String)
    data class RestoredDocument(
        val title: String,
        val folderName: String,
        val tags: List<String>,
        val quality: PdfQuality,
        val pageSize: PdfPageSize,
        val searchablePdf: Boolean,
        val pageNumbers: Boolean,
        val scanMode: ScanMode,
        val ocrScript: OcrScript,
        val pages: List<RestoredPage>,
        val tempDir: File
    )

    fun export(document: ScanDocument, password: CharArray, destination: File) {
        try {
            writePackage(document, password, destination)
        } finally {
            password.fill('\u0000')
        }
    }

    private fun writePackage(document: ScanDocument, password: CharArray, destination: File) {
        require(password.size >= 6) { "Use a backup password with at least 6 characters" }
        destination.parentFile?.mkdirs()
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val key = deriveKey(password, salt, ITERATIONS)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        }

        FileOutputStream(destination).use { raw ->
            val header = DataOutputStream(BufferedOutputStream(raw))
            header.write(MAGIC)
            header.writeInt(ITERATIONS)
            header.write(salt)
            header.write(iv)
            header.flush()

            CipherOutputStream(raw, cipher).use { encrypted ->
                ZipOutputStream(BufferedOutputStream(encrypted)).use { zip ->
                    val manifest = JSONObject().apply {
                        put("version", 1)
                        put("title", document.title)
                        put("folder", document.folderName)
                        put("tags", JSONArray(document.tags))
                        put("quality", document.pdfQuality.name)
                        put("pageSize", document.pdfPageSize.name)
                        put("searchablePdf", document.searchablePdf)
                        put("pageNumbers", document.pageNumbers)
                        put("scanMode", document.scanMode.name)
                        put("ocrScript", document.ocrScript.name)
                        put("pages", JSONArray().apply {
                            document.pages.sortedBy { it.position }.forEachIndexed { index, page ->
                                put(JSONObject().apply {
                                    put("file", "pages/${index.toString().padStart(4, '0')}.jpg")
                                    put("ocr", page.ocrText)
                                })
                            }
                        })
                    }
                    zip.putNextEntry(ZipEntry("manifest.json"))
                    zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    document.pages.sortedBy { it.position }.forEachIndexed { index, page ->
                        val file = File(page.imagePath)
                        zip.putNextEntry(ZipEntry("pages/${index.toString().padStart(4, '0')}.jpg"))
                        FileInputStream(file).use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
        }
    }

    fun import(source: Uri, password: CharArray): RestoredDocument {
        require(password.size >= 6) { "Backup password must be at least 6 characters" }
        val encrypted = File(context.cacheDir, "import_${UUID.randomUUID()}.scanpro")
        context.contentResolver.openInputStream(source).use { input ->
            requireNotNull(input) { "Could not open backup" }
            FileOutputStream(encrypted).use { input.copyTo(it) }
        }
        val outDir = File(context.cacheDir, "restore_${UUID.randomUUID()}").apply { mkdirs() }
        var rawInput: FileInputStream? = null
        try {
            rawInput = FileInputStream(encrypted)
            val input = DataInputStream(BufferedInputStream(rawInput))
            val magic = ByteArray(MAGIC.size)
            input.readFully(magic)
            require(magic.contentEquals(MAGIC)) { "Not a ScanPro encrypted package" }
            val iterations = input.readInt()
            require(iterations in 100_000..1_000_000) { "Invalid backup header" }
            val salt = ByteArray(16).also(input::readFully)
            val iv = ByteArray(12).also(input::readFully)
            val key = deriveKey(password, salt, iterations)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            }

            CipherInputStream(input, cipher).use { decrypted ->
                ZipInputStream(BufferedInputStream(decrypted)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val safe = File(outDir, entry.name).canonicalFile
                        require(safe.path.startsWith(outDir.canonicalPath + File.separator)) { "Unsafe backup entry" }
                        if (entry.isDirectory) safe.mkdirs() else {
                            safe.parentFile?.mkdirs()
                            FileOutputStream(safe).use { zip.copyTo(it) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            val manifestFile = File(outDir, "manifest.json")
            require(manifestFile.exists()) { "Backup manifest is missing" }
            val manifest = JSONObject(manifestFile.readText())
            val pageArray = manifest.getJSONArray("pages")
            val pages = buildList {
                for (index in 0 until pageArray.length()) {
                    val pageJson = pageArray.getJSONObject(index)
                    val file = File(outDir, pageJson.getString("file")).canonicalFile
                    require(file.path.startsWith(outDir.canonicalPath + File.separator)) { "Unsafe backup page path" }
                    require(file.isFile) { "Backup page is missing" }
                    add(RestoredPage(file, pageJson.optString("ocr")))
                }
            }
            require(pages.isNotEmpty()) { "Backup contains no pages" }
            return RestoredDocument(
                title = manifest.optString("title", "Restored scan"),
                folderName = manifest.optString("folder", ""),
                tags = buildList {
                    val array = manifest.optJSONArray("tags") ?: JSONArray()
                    for (index in 0 until array.length()) add(array.optString(index))
                }.filter { it.isNotBlank() },
                quality = enumOrDefault(manifest.optString("quality"), PdfQuality.BALANCED),
                pageSize = enumOrDefault(manifest.optString("pageSize"), PdfPageSize.A4),
                searchablePdf = manifest.optBoolean("searchablePdf", true),
                pageNumbers = manifest.optBoolean("pageNumbers", false),
                scanMode = enumOrDefault(manifest.optString("scanMode"), ScanMode.DOCUMENT),
                ocrScript = enumOrDefault(manifest.optString("ocrScript"), OcrScript.LATIN),
                pages = pages,
                tempDir = outDir
            )
        } catch (error: Throwable) {
            outDir.deleteRecursively()
            throw IllegalArgumentException("Could not decrypt backup. Check the password and file.", error)
        } finally {
            runCatching { rawInput?.close() }
            encrypted.delete()
            password.fill('\u0000')
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
        runCatching { enumValueOf<T>(value) }.getOrDefault(default)

    companion object {
        private val MAGIC = byteArrayOf('S'.code.toByte(), 'P'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte())
        private const val ITERATIONS = 210_000
    }
}
