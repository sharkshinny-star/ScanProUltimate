package com.example.scanpro.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.edit
import java.io.File
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.net.toUri

/** A PDF from the device or a cloud app that the user opened in ScanPro. */
data class RecentPdf(val uri: Uri, val name: String, val openedAt: Long)

/**
 * The PDFs opened recently from the device, so they can be reopened with one tap. Listing every PDF on the
 * phone would need the "All files access" permission, which ScanPro deliberately doesn't ask for; instead it
 * keeps a read grant for the files the user picked, and nothing else.
 */
@Singleton
class RecentPdfStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs by lazy { context.getSharedPreferences("recent_pdfs", Context.MODE_PRIVATE) }
    private val _items = MutableStateFlow<List<RecentPdf>>(emptyList())
    val items: StateFlow<List<RecentPdf>> = _items.asStateFlow()

    @Volatile private var loaded = false

    init {
        // Read off the main thread (StrictMode). Anything that changes the list loads first if this has not finished,
        // so an entry added in the first moments cannot overwrite the saved list with a list of one.
        CoroutineScope(Dispatchers.IO).launch { loadOnce() }
    }

    @Synchronized
    private fun loadOnce() {
        if (loaded) return
        _items.value = load()
        loaded = true
    }

    /**
     * Records [uri] as just opened. A picker result normally gives a read grant that survives a restart. Some providers
     * refuse that; their file is then copied into the app's own storage (if it is small enough), because a link that
     * only worked until the screen closed left a Recents row that could not be opened again.
     */
    @Synchronized
    fun add(uri: Uri, fallbackName: String = "PDF document") {
        loadOnce()
        if (uri.scheme != "content") return
        val name = displayName(uri) ?: fallbackName
        val usable = if (grantKept(uri)) uri else copyLocally(uri) ?: return
        val entry = RecentPdf(usable, name, System.currentTimeMillis())
        val kept = listOf(entry) + _items.value.filterNot { it.uri == usable }
        kept.drop(MAX_ITEMS).forEach { release(it.uri) }
        save(kept.take(MAX_ITEMS))
    }

    /** Whether a lasting read grant could be taken for a link; replaceable so a test can model a provider that refuses. */
    internal var grantKept: (Uri) -> Boolean = ::holdsPersistedGrant

    private fun holdsPersistedGrant(uri: Uri): Boolean {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        return context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    }

    private fun copiesDir() = File(context.filesDir, COPIES_DIR)

    private fun isCopy(uri: Uri) = uri.scheme == "file" && uri.path?.startsWith(copiesDir().path + File.separator) == true

    /** A private copy of [uri] named after the link, so opening the same file again replaces the copy instead of adding one. */
    private fun copyLocally(uri: Uri): Uri? = runCatching {
        val dir = copiesDir().apply { mkdirs() }
        val target = File(dir, "${uri.toString().hashCode().toUInt()}.pdf")
        val partial = File(dir, target.name + ".part")
        var written = 0L
        context.contentResolver.openInputStream(uri)?.use { input ->
            partial.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    written += read
                    check(written <= MAX_COPY_BYTES) { "too large to keep" }
                    output.write(buffer, 0, read)
                }
            }
        } ?: error("unreadable")
        check(partial.renameTo(target) || (target.delete() && partial.renameTo(target)))
        Uri.fromFile(target)
    }.onFailure { File(copiesDir(), uri.toString().hashCode().toUInt().toString() + ".pdf.part").delete() }.getOrNull()

    /** True when [uri] can be read now; used before opening a Recents row so a dead link gives a message, not a blank viewer. */
    fun canRead(uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.read(); true } ?: false
    }.getOrDefault(false)

    @Synchronized
    fun remove(uri: Uri) {
        loadOnce()
        release(uri)
        save(_items.value.filterNot { it.uri == uri })
    }

    @Synchronized
    fun clear() {
        loadOnce()
        _items.value.forEach { release(it.uri) }
        save(emptyList())
    }

    /** Drops entries whose file was deleted or whose access was revoked. */
    @Synchronized
    fun prune() {
        loadOnce()
        val granted = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }.toSet()
        // The grant can outlive the file; a provider that no longer knows the document returns no row.
        val alive = _items.value.filter {
            if (isCopy(it.uri)) File(it.uri.path.orEmpty()).exists() else it.uri in granted && displayName(it.uri) != null
        }
        if (alive.size != _items.value.size) save(alive)
        // A copy whose row is gone (removed, replaced or dropped off the end) is not kept.
        val kept = alive.mapNotNull { if (isCopy(it.uri)) File(it.uri.path.orEmpty()).name else null }.toSet()
        copiesDir().listFiles()?.filter { it.name !in kept }?.forEach { it.delete() }
    }

    private fun release(uri: Uri) {
        if (isCopy(uri)) {
            runCatching { File(uri.path.orEmpty()).delete() }
            return
        }
        runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun save(items: List<RecentPdf>) {
        _items.value = items
        val json = JSONArray().apply {
            items.forEach { put(JSONObject().put("uri", it.uri.toString()).put("name", it.name).put("at", it.openedAt)) }
        }
        prefs.edit { putString(KEY, json.toString()) }
    }

    private fun load(): List<RecentPdf> {
        val array = runCatching { JSONArray(prefs.getString(KEY, null) ?: return emptyList()) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            runCatching {
                val item = array.getJSONObject(index)
                RecentPdf(item.getString("uri").toUri(), item.optString("name", "PDF document"), item.optLong("at"))
            }.getOrNull()
        }
    }

    private companion object {
        const val KEY = "items"
        const val MAX_ITEMS = 12
        const val COPIES_DIR = "recent_pdfs"
        /** A provider that gives no lasting grant is kept only for files up to this size (12 of them at most). */
        const val MAX_COPY_BYTES = 30L * 1024 * 1024
    }
}
