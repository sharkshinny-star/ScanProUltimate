package com.example.scanpro.data

import android.content.Context
import android.graphics.Bitmap
import com.example.scanpro.imaging.ImageProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Signatures the user chose to keep, so signing a page takes two taps instead of drawing again. They stay in
 * app-private storage, which is excluded from cloud and device-transfer backups.
 */
@Singleton
class SignatureLibrary @Inject constructor(@ApplicationContext context: Context) {
    private val dir = File(context.filesDir, "saved_signatures")
    private val _items = MutableStateFlow(scan())
    /** Saved signature images, newest first. */
    val items: StateFlow<List<File>> = _items.asStateFlow()

    @Synchronized
    fun save(signature: Bitmap): File {
        dir.mkdirs()
        val file = File(dir, "sig_${System.currentTimeMillis()}.png")
        ImageProcessor.saveSignature(signature, file)
        scan().drop(MAX_SAVED).forEach { it.delete() }
        _items.value = scan()
        return file
    }

    @Synchronized
    fun delete(file: File) {
        // Only files from this library, never a path handed in from elsewhere.
        if (file.parentFile?.canonicalPath == dir.canonicalPath) file.delete()
        _items.value = scan()
    }

    private fun scan(): List<File> =
        dir.listFiles { file -> file.isFile && file.name.endsWith(".png") }
            .orEmpty()
            .sortedByDescending { it.lastModified() }

    private companion object {
        const val MAX_SAVED = 6
    }
}
