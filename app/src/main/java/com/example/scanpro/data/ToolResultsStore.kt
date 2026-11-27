package com.example.scanpro.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A file a PDF or image tool made: compressed, converted, merged, signed and so on. */
data class ToolResultFile(val file: File, val sizeBytes: Long, val modifiedAt: Long) {
    val name: String get() = file.name
}

/**
 * Every file the tools have made, kept in the app's own storage until the user deletes it (iLovePDF's "Processed
 * files"). Tool outputs used to sit in the cache and were deleted after a day, so a result closed without Save or
 * Share was lost. Nothing here leaves the phone; the folder is private to ScanPro and shared only on request.
 */
@Singleton
class ToolResultsStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val _items = MutableStateFlow<List<ToolResultFile>>(emptyList())
    val items: StateFlow<List<ToolResultFile>> = _items.asStateFlow()

    /** Replaceable in tests, which have no Android context. */
    internal var dirProvider: () -> File = { File(context.filesDir, DIR) }

    init {
        CoroutineScope(Dispatchers.IO).launch { refresh() }
    }

    fun dir(): File = dirProvider().also { it.mkdirs() }

    /**
     * Moves a finished output into the results folder and returns where it now is. A name already taken gets " (2)",
     * so an earlier result is never replaced. Anything already kept, or that is not a plain file, is left where it is.
     */
    @Synchronized
    fun keep(output: File): File {
        if (!output.isFile) return output
        val dir = dir()
        if (output.parentFile?.canonicalPath == dir.canonicalPath) return output.also { refresh() }
        val target = freeName(dir, output.name)
        // Copied, not renamed: a rename keeps the cache folder's storage group, so Android would go on counting the
        // file as cache in the app's storage figures.
        val moved = runCatching {
            output.copyTo(target)
            output.delete()
            true
        }.getOrElse {
            target.delete()
            false
        }
        refresh()
        return if (moved) target else output
    }

    @Synchronized
    fun delete(file: File): Boolean {
        val inside = file.parentFile?.canonicalPath == dir().canonicalPath
        val gone = inside && file.delete()
        refresh()
        return gone
    }

    @Synchronized
    fun refresh() {
        _items.value = dir().listFiles { f -> f.isFile && !f.name.startsWith(".") }
            .orEmpty()
            .map { ToolResultFile(it, it.length(), it.lastModified()) }
            .sortedByDescending { it.modifiedAt }
    }

    companion object {
        const val DIR = "tool_results"

        internal fun freeName(dir: File, name: String): File {
            val plain = File(dir, name)
            if (!plain.exists()) return plain
            val base = name.substringBeforeLast('.')
            val extension = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
            var n = 2
            while (true) {
                val candidate = File(dir, "$base ($n)$extension")
                if (!candidate.exists()) return candidate
                n++
            }
        }
    }
}
