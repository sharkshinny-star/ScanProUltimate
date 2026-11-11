package com.example.scanpro.backup

import android.content.Context
import android.net.Uri
import com.example.scanpro.data.OcrWord
import com.example.scanpro.data.AnnotationType
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.JsonCodec
import com.example.scanpro.data.OcrScript
import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageAnnotation
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ScanDocument
import com.example.scanpro.data.ScanMode
import com.example.scanpro.data.ScanPage
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject
import com.example.scanpro.R
import com.example.scanpro.core.text.UserFacingException
import com.example.scanpro.core.text.userError
import com.example.scanpro.core.text.userRequire

/**
 * Password-encrypted, portable ScanPro package. Stores each page's rendered output, OCR text, original source
 * image, and edit settings (filter, rotation, crop, adjustments, annotations + signature images) so a restored
 * document stays fully editable. Older packages without the extra fields still restore as flattened pages.
 */
class EncryptedDocumentPackage(private val context: Context) {
    data class RestoredPage(
        val file: File,
        val ocrText: String,
        /** True when the text was corrected by hand; older backups don't record it. */
        val ocrEdited: Boolean = false,
        val ocrWords: List<OcrWord> = emptyList(),
        /** Original source image, or null for older backups that only contain the rendered page. */
        val sourceFile: File? = null,
        val filter: PageFilter = PageFilter.ORIGINAL,
        val rotationDegrees: Int = 0,
        val crop: CropQuad = CropQuad.DEFAULT,
        val adjustments: PageAdjustments = PageAdjustments.DEFAULT,
        /** Signature annotations point at extracted files inside the restore temp dir. */
        val annotations: List<PageAnnotation> = emptyList()
    )
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
            write(document, password, destination)
        } finally {
            password.fill('\u0000')
        }
    }

    fun import(source: Uri, password: CharArray): RestoredDocument {
        userRequire(password.size >= MIN_RESTORE_PASSWORD_LENGTH, R.string.err_backup_password_too_short, MIN_RESTORE_PASSWORD_LENGTH)
        val encrypted = File(context.cacheDir, "import_${UUID.randomUUID()}.scanpro")
        try {
            context.contentResolver.openInputStream(source).use { input ->
                if (input == null) userError(R.string.err_could_not_open_backup)
                FileOutputStream(encrypted).use { output -> copyCapped(input, output, MAX_PACKAGE_BYTES) }
            }
        } catch (error: Throwable) {
            encrypted.delete()
            // read() wipes the password on every path of its own; this one never reaches it.
            password.fill('\u0000')
            throw error
        }
        val outDir = File(context.cacheDir, "restore_${UUID.randomUUID()}")
        return read(encrypted, outDir, password)
    }

    companion object {
        private val MAGIC = byteArrayOf('S'.code.toByte(), 'P'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte())
        private val MAGIC_CHUNKED = byteArrayOf('S'.code.toByte(), 'P'.code.toByte(), 'K'.code.toByte(), '2'.code.toByte())
        /**
         * New backups need at least this many characters. The package can be attacked offline, so every
         * character counts; restore still accepts [MIN_RESTORE_PASSWORD_LENGTH] for backups made before.
         */
        const val MIN_NEW_PASSWORD_LENGTH = 8
        /** Backups written by versions up to 4.2.0 could use six characters; they must keep restoring. */
        const val MIN_RESTORE_PASSWORD_LENGTH = 6
        /**
         * PBKDF2-HMAC-SHA256 rounds for new backups (OWASP 2023 figure). Readers accept 100k to 1M, so
         * backups made with 210k still restore, and older app versions can restore these.
         */
        internal const val ITERATIONS = 600_000
        private const val MAX_RESTORE_BYTES = 2L * 1024 * 1024 * 1024
        /** The encrypted file itself: the unpacked limit plus room for chunk tags and zip headers. */
        private const val MAX_PACKAGE_BYTES = MAX_RESTORE_BYTES + MAX_RESTORE_BYTES / 16
        /** A 300-page document with sources and signatures needs a few thousand; millions of empty entries would exhaust the file system. */
        private const val MAX_ENTRIES = 20_000
        /** OCR text and edit settings of the largest documents fit in a few MB; the manifest is read into memory whole. */
        private const val MAX_MANIFEST_BYTES = 64L * 1024 * 1024

        /** Writes [document] as an encrypted package at [destination]; wipes [password] afterwards. */
        fun write(document: ScanDocument, password: CharArray, destination: File) {
            try {
                writePackage(document, password, destination)
            } finally {
                password.fill('\u0000')
            }
        }

        private fun writePackage(document: ScanDocument, password: CharArray, destination: File) {
            userRequire(password.size >= MIN_NEW_PASSWORD_LENGTH, R.string.err_backup_password_too_short, MIN_NEW_PASSWORD_LENGTH)
            destination.parentFile?.mkdirs()
            val salt = ByteArray(16).also(SecureRandom()::nextBytes)
            val noncePrefix = ByteArray(ChunkedAead.PREFIX_BYTES).also(SecureRandom()::nextBytes)
            val key = deriveKey(password, salt, ITERATIONS)

            val sortedPages = document.pages.sortedBy { it.position }
            fun pageEntry(index: Int) = "pages/${index.toString().padStart(4, '0')}.jpg"
            fun sourceEntry(index: Int) = "sources/${index.toString().padStart(4, '0')}"
            fun assetEntry(index: Int, annotation: Int) = "assets/${index.toString().padStart(4, '0')}_$annotation.png"
            // Signature images live in app storage, so annotations are rewritten to point at the package entries.
            fun portableAnnotations(index: Int, page: ScanPage): List<PageAnnotation> =
                page.annotations.mapIndexedNotNull { k, annotation ->
                    if (annotation.type != AnnotationType.SIGNATURE) annotation
                    else if (File(annotation.assetPath).isFile) annotation.copy(assetPath = assetEntry(index, k))
                    else null
                }

            FileOutputStream(destination).use { raw ->
                val header = DataOutputStream(BufferedOutputStream(raw))
                header.write(MAGIC_CHUNKED)
                header.writeInt(ITERATIONS)
                header.write(salt)
                header.write(noncePrefix)
                header.flush()

                // Chunked AES-GCM: a single GCM stream is buffered whole in memory on Android.
                ChunkedAead.Writer(raw, key, noncePrefix).use { encrypted ->
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
                                sortedPages.forEachIndexed { index, page ->
                                    put(JSONObject().apply {
                                        put("file", pageEntry(index))
                                        put("ocr", page.ocrText)
                                        // Without these a restored correction is overwritten by the next edit, and
                                        // the PDF text layer and Excel export lose word positions.
                                        put("ocrEdited", page.ocrEdited)
                                        if (page.ocrWords.isNotEmpty()) put("words", JsonCodec.encodeWords(page.ocrWords))
                                        if (File(page.sourcePath).isFile) put("source", sourceEntry(index))
                                        put("filter", page.filter.name)
                                        put("rotation", page.rotationDegrees)
                                        put("crop", JsonCodec.encodeCrop(page.crop))
                                        put("adjustments", JsonCodec.encodeAdjustments(page.adjustments))
                                        put("annotations", JsonCodec.encodeAnnotations(portableAnnotations(index, page)))
                                    })
                                }
                            })
                        }
                        zip.putNextEntry(ZipEntry("manifest.json"))
                        zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                        zip.closeEntry()

                        fun addFile(name: String, file: File) {
                            zip.putNextEntry(ZipEntry(name))
                            FileInputStream(file).use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                        sortedPages.forEachIndexed { index, page ->
                            addFile(pageEntry(index), File(page.imagePath))
                            val source = File(page.sourcePath)
                            if (source.isFile) addFile(sourceEntry(index), source)
                            page.annotations.forEachIndexed { k, annotation ->
                                if (annotation.type == AnnotationType.SIGNATURE && File(annotation.assetPath).isFile) {
                                    addFile(assetEntry(index, k), File(annotation.assetPath))
                                }
                            }
                        }
                    }
                }
            }
        }


        /**
         * Decrypts and unpacks a package file into [outDir] and describes it. Needs no Android context, so the unit tests
         * run it directly. On any failure [outDir] is removed; [encrypted] is always deleted and [password] wiped.
         */
        fun read(encrypted: File, outDir: File, password: CharArray): RestoredDocument {
            userRequire(password.size >= MIN_RESTORE_PASSWORD_LENGTH, R.string.err_backup_password_too_short, MIN_RESTORE_PASSWORD_LENGTH)
            outDir.mkdirs()
            var rawInput: FileInputStream? = null
            try {
                rawInput = FileInputStream(encrypted)
                val input = DataInputStream(BufferedInputStream(rawInput))
                val magic = ByteArray(MAGIC.size)
                input.readFully(magic)
                val chunked = magic.contentEquals(MAGIC_CHUNKED)
                require(chunked || magic.contentEquals(MAGIC)) { "Not a ScanPro encrypted package" }
                val iterations = input.readInt()
                require(iterations in 100_000..1_000_000) { "Invalid backup header" }
                val salt = ByteArray(16).also(input::readFully)
                val key = deriveKey(password, salt, iterations)
                val decryptedStream = if (chunked) {
                    val noncePrefix = ByteArray(ChunkedAead.PREFIX_BYTES).also(input::readFully)
                    ChunkedAead.Reader(input, key, noncePrefix)
                } else {
                    // Version 1 packages: one GCM stream (kept so older backups still restore).
                    val iv = ByteArray(12).also(input::readFully)
                    val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                        init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                    }
                    CipherInputStream(input, cipher)
                }

                decryptedStream.use { decrypted ->
                    val buffered = BufferedInputStream(decrypted)
                    ZipInputStream(buffered).let { zip ->
                        var entry = zip.nextEntry
                        var extractedBytes = 0L
                        var entries = 0
                        while (entry != null) {
                            if (++entries > MAX_ENTRIES) throw BackupTooLargeException()
                            val safe = File(outDir, entry.name).canonicalFile
                            require(safe.path.startsWith(outDir.canonicalPath + File.separator)) { "Unsafe backup entry" }
                            if (entry.isDirectory) {
                                safe.mkdirs()
                            } else {
                                safe.parentFile?.mkdirs()
                                FileOutputStream(safe).use { out ->
                                    // Cap total unpacked size so a crafted backup cannot fill the device (zip bomb).
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    while (true) {
                                        val read = zip.read(buffer)
                                        if (read < 0) break
                                        extractedBytes += read
                                        if (extractedBytes > MAX_RESTORE_BYTES) throw BackupTooLargeException()
                                        out.write(buffer, 0, read)
                                    }
                                }
                            }
                            zip.closeEntry()
                            entry = zip.nextEntry
                        }
                        // ZipInputStream stops at the central directory; read the rest so the final
                        // chunk's tag and the "nothing after the end" check are verified too.
                        val drain = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (buffered.read(drain) >= 0) { /* discard: only the authentication matters */ }
                        zip.close()
                    }
                }

                val manifestFile = File(outDir, "manifest.json")
                require(manifestFile.exists()) { "Backup manifest is missing" }
                require(manifestFile.length() <= MAX_MANIFEST_BYTES) { "Backup manifest is too large" }
                val manifest = JSONObject(manifestFile.readText())
                val pageArray = manifest.getJSONArray("pages")
                val pages = buildList {
                    for (index in 0 until pageArray.length()) {
                        val pageJson = pageArray.getJSONObject(index)
                        val file = safeBackupFile(outDir, pageJson.getString("file"))
                        require(file.isFile) { "Backup page is missing" }
                        // Everything below is optional so backups made before editable restore still import.
                        val sourceFile = pageJson.optString("source").takeIf { it.isNotBlank() }
                            ?.let { safeBackupFile(outDir, it) }?.takeIf { it.isFile }
                        val annotations = JsonCodec.decodeAnnotations(pageJson.optString("annotations"))
                            .mapNotNull { annotation ->
                                // Only signatures use a file; a path in any other annotation could only point
                                // outside the backup, so it is dropped.
                                if (annotation.type != AnnotationType.SIGNATURE) annotation.copy(assetPath = "")
                                else runCatching { safeBackupFile(outDir, annotation.assetPath) }.getOrNull()
                                    ?.takeIf { it.isFile }
                                    ?.let { annotation.copy(assetPath = it.absolutePath) }
                            }
                        add(
                            RestoredPage(
                                file = file,
                                ocrText = pageJson.optString("ocr"),
                                ocrEdited = pageJson.optBoolean("ocrEdited", false),
                                ocrWords = JsonCodec.decodeWords(pageJson.optString("words")),
                                sourceFile = sourceFile,
                                filter = enumOrDefault(pageJson.optString("filter"), PageFilter.ORIGINAL),
                                rotationDegrees = pageJson.optInt("rotation", 0),
                                crop = JsonCodec.decodeCrop(pageJson.optString("crop")),
                                adjustments = JsonCodec.decodeAdjustments(pageJson.optString("adjustments")),
                                annotations = annotations
                            )
                        )
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
            } catch (error: BackupTooLargeException) {
                outDir.deleteRecursively()
                throw error
            } catch (error: Throwable) {
                outDir.deleteRecursively()
                val wrongPassword = generateSequence(error) { it.cause }.any { it is ChunkedAead.WrongKeyException }
                throw UserFacingException(
                    if (wrongPassword) R.string.err_backup_wrong_password else R.string.err_could_not_decrypt_backup,
                    cause = error
                )
            } finally {
                runCatching { rawInput?.close() }
                encrypted.delete()
                password.fill('\u0000')
            }
        }

        private class BackupTooLargeException : UserFacingException(R.string.err_backup_too_large)

        /** Copies at most [limit] bytes, so a huge or endless file picked by mistake can't fill the device. */
        internal fun copyCapped(input: InputStream, output: OutputStream, limit: Long) {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw BackupTooLargeException()
                output.write(buffer, 0, read)
            }
        }

        /** Resolves a manifest-relative path and refuses anything that escapes the restore directory. */
        private fun safeBackupFile(outDir: File, relative: String): File {
            val file = File(outDir, relative).canonicalFile
            require(file.path.startsWith(outDir.canonicalPath + File.separator)) { "Unsafe backup path" }
            return file
        }

        private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
            val spec = PBEKeySpec(password, salt, iterations, 256)
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            spec.clearPassword()
            // SecretKeySpec keeps its own copy; don't leave a second one of the key on the heap.
            return SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        }

        private inline fun <reified T : Enum<T>> enumOrDefault(value: String, default: T): T =
            runCatching { enumValueOf<T>(value) }.getOrDefault(default)
    }
}
