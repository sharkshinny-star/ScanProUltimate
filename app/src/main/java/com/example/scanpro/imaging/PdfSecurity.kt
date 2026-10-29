package com.example.scanpro.imaging

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File

object PdfSecurity {
    /** PDF passwords can be attacked offline, so very short ones offer little protection. */
    const val MIN_PASSWORD_LENGTH = 6

    fun protect(source: File, destination: File, password: String) {
        require(password.length >= MIN_PASSWORD_LENGTH) { "Use a password with at least $MIN_PASSWORD_LENGTH characters" }
        destination.parentFile?.mkdirs()
        // AES-256 first; a device whose crypto provider rejects it still gets AES-128 rather than a failure.
        var failure: Exception? = null
        for (keyLength in intArrayOf(256, 128)) {
            try {
                PDDocument.load(source).use { document ->
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
        throw IllegalStateException("Could not protect this PDF: ${failure?.localizedMessage ?: "unsupported file"}", failure)
    }

    /**
     * How a PDF is protected, found with PDFBox rather than the system renderer: the renderer's errors
     * for protected files differ between Android versions, but this answer does not.
     */
    fun protection(file: File): PdfProtection = try {
        PDDocument.load(file).use { if (it.isEncrypted) PdfProtection.RESTRICTED_ONLY else PdfProtection.NONE }
    } catch (error: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
        PdfProtection.PASSWORD_REQUIRED
    }

    fun unlock(source: File, destination: File, password: String) {
        destination.parentFile?.mkdirs()
        PDDocument.load(source, password).use { document ->
            document.isAllSecurityToBeRemoved = true
            document.save(destination)
        }
    }
}
