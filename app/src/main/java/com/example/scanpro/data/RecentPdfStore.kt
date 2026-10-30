package com.example.scanpro.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** A PDF from the device or a cloud app that the user opened in ScanPro. */
data class RecentPdf(val uri: Uri, val name: String, val openedAt: Long)

/**
 * The PDFs opened recently from the device, so they can be reopened with one tap. Listing every PDF on the
 * phone would need the "All files access" permission, which ScanPro deliberately doesn't ask for; instead it
 * keeps a read grant for the files the user picked, and nothing else.
 */
@Singleton
class RecentPdfStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences("recent_pdfs", Context.MODE_PRIVATE)
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<RecentPdf>> = _items.asStateFlow()

    /** Records [uri] as just opened. Picker results can be kept across restarts; other links are kept only while they last. */
    @Synchronized
    fun add(uri: Uri, fallbackName: String = "PDF document") {
        if (uri.scheme != "content") return
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val entry = RecentPdf(uri, displayName(uri) ?: fallbackName, System.currentTimeMillis())
        val kept = listOf(entry) + _items.value.filterNot { it.uri == uri }
        kept.drop(MAX_ITEMS).forEach { release(it.uri) }
        save(kept.take(MAX_ITEMS))
    }

    @Synchronized
    fun remove(uri: Uri) {
        release(uri)
        save(_items.value.filterNot { it.uri == uri })
    }

    @Synchronized
    fun clear() {
        _items.value.forEach { release(it.uri) }
        save(emptyList())
    }

    /** Drops entries whose file was deleted or whose access was revoked. */
    @Synchronized
    fun prune() {
        val granted = context.contentResolver.persistedUriPermissions.filter { it.isReadPermission }.map { it.uri }.toSet()
        // The grant can outlive the file; a provider that no longer knows the document returns no row.
        val alive = _items.value.filter { it.uri in granted && displayName(it.uri) != null }
        if (alive.size != _items.value.size) save(alive)
    }

    private fun release(uri: Uri) {
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
                RecentPdf(Uri.parse(item.getString("uri")), item.optString("name", "PDF document"), item.optLong("at"))
            }.getOrNull()
        }
    }

    private companion object {
        const val KEY = "items"
        const val MAX_ITEMS = 12
    }
}
