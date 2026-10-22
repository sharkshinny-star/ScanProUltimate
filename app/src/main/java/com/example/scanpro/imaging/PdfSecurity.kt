package com.example.scanpro.imaging

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File
import java.util.UUID

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
                    val permissions = AccessPermission().apply {
                        setCanPrint(true)
                        setCanExtractContent(false)
                        setCanModify(false)
                        setCanModifyAnnotations(false)
                        setCanFillInForm(false)
                    }
                    val ownerPassword = UUID.randomUUID().toString() + UUID.randomUUID().toString()
                    val policy = StandardProtectionPolicy(ownerPassword, password, permissions).apply {
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

    fun unlock(source: File, destination: File, password: String) {
        destination.parentFile?.mkdirs()
        PDDocument.load(source, password).use { document ->
            document.isAllSecurityToBeRemoved = true
            document.save(destination)
        }
    }
}
