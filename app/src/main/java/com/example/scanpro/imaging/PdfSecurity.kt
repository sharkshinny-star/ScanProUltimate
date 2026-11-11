package com.example.scanpro.imaging

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File
import com.example.scanpro.R
import com.example.scanpro.core.text.UserFacingPluralException
import com.example.scanpro.core.text.userError

object PdfSecurity {
    /**
     * PDF passwords can be attacked offline, and the PDF key derivation is far cheaper to guess against than
     * the backup's PBKDF2, so short ones offer little protection.
     */
    const val MIN_PASSWORD_LENGTH = 8

    fun protect(source: File, destination: File, password: String) {
        if (password.length < MIN_PASSWORD_LENGTH) throw UserFacingPluralException(R.plurals.err_pdf_password_too_short, MIN_PASSWORD_LENGTH, MIN_PASSWORD_LENGTH)
        destination.parentFile?.mkdirs()
        // AES-256 first; a device whose crypto provider rejects it still gets AES-128 rather than a failure.
        var failure: Exception? = null
        for (keyLength in intArrayOf(256, 128)) {
            try {
                PDDocument.load(source, SCRATCH()).use { document ->
                    // The screen promises a password to open the file and nothing else, so whoever has the
                    // password can print, copy, edit and fill it. The same password is the owner password:
                    // a random one would make the restrictions impossible for the user to lift later.
                    val permissions = AccessPermission()
                    val policy = StandardProtectionPolicy(password, password, permissions).apply {
                        encryptionKeyLength = keyLength
                        isPreferAES = true
                    }
                    document.protect(policy)
                    document.save(destination)
                }
                return
            } catch (error: Exception) {
                failure = error
                destination.delete()
            }
        }
        failure?.localizedMessage?.let { userError(R.string.err_could_not_protect_pdf, it, cause = failure) }
        userError(R.string.err_could_not_protect_pdf_unsupported, cause = failure)
    }

    /**
     * How a PDF is protected, found with PDFBox rather than the system renderer: the renderer's errors
     * for protected files differ between Android versions, but this answer does not.
     */
    fun protection(file: File): PdfProtection = try {
        PDDocument.load(file, SCRATCH()).use { if (it.isEncrypted) PdfProtection.RESTRICTED_ONLY else PdfProtection.NONE }
    } catch (error: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
        PdfProtection.PASSWORD_REQUIRED
    }

    fun unlock(source: File, destination: File, password: String) {
        destination.parentFile?.mkdirs()
        PDDocument.load(source, password, SCRATCH()).use { document ->
            document.isAllSecurityToBeRemoved = true
            document.save(destination)
        }
    }

    /**
     * Streams go to a scratch file rather than the heap (as PdfToolsEngine.openPdf does): Protect and Unlock read and
     * rewrite the whole file, and a 50 MB PDF held in memory twice ran the app out of memory.
     */
    @Suppress("FunctionName")
    private fun SCRATCH() = com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()
}
