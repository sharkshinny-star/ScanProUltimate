package com.example.scanpro.imaging

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File
import java.util.UUID

object PdfSecurity {
    fun protect(source: File, destination: File, password: String) {
        require(password.length >= 4) { "Use a password with at least 4 characters" }
        destination.parentFile?.mkdirs()
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
                encryptionKeyLength = 256
                isPreferAES = true
            }
            document.protect(policy)
            document.save(destination)
        }
    }

    fun unlock(source: File, destination: File, password: String) {
        destination.parentFile?.mkdirs()
        PDDocument.load(source, password).use { document ->
            document.isAllSecurityToBeRemoved = true
            document.save(destination)
        }
    }
}
