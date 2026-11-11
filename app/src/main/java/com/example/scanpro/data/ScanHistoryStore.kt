package com.example.scanpro.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * One scanned code kept in the history. [format] and [valueType] are ML Kit's Barcode.FORMAT_* and Barcode.TYPE_*
 * numbers; the code is shown again by parsing [raw].
 */
data class ScanHistoryEntry(val raw: String, val format: Int, val valueType: Int, val savedAt: Long, val favourite: Boolean = false)

/** The list rules of the scan history, as pure functions: newest first, no repeats, at most [LIMIT]. */
object ScanHistory {
    const val LIMIT = 200

    /**
     * [list] with [entry] at the front. An earlier entry with the same text is replaced (it keeps its favourite mark),
     * so a repeat moves to the top with the new time. Over [LIMIT], the oldest entries go first, favourites last.
     */
    fun add(list: List<ScanHistoryEntry>, entry: ScanHistoryEntry): List<ScanHistoryEntry> {
        val earlier = list.firstOrNull { it.raw == entry.raw }
        val front = if (earlier?.favourite == true) entry.copy(favourite = true) else entry
        val result = ArrayList<ScanHistoryEntry>(list.size + 1)
        result.add(front)
        list.filterTo(result) { it.raw != entry.raw }
        var index = result.lastIndex
        while (result.size > LIMIT && index > 0) {
            if (!result[index].favourite) result.removeAt(index)
            index--
        }
        return if (result.size > LIMIT) result.take(LIMIT) else result
    }

    fun setFavourite(list: List<ScanHistoryEntry>, raw: String, favourite: Boolean): List<ScanHistoryEntry> =
        list.map { if (it.raw == raw) it.copy(favourite = favourite) else it }

    fun remove(list: List<ScanHistoryEntry>, raw: String): List<ScanHistoryEntry> = list.filterNot { it.raw == raw }

    /**
     * The entries whose text contains [query], ignoring case and surrounding spaces; all of them for a blank query.
     * A Wi-Fi code is matched on its network name only, so searching never finds a password.
     */
    fun filter(list: List<ScanHistoryEntry>, query: String): List<ScanHistoryEntry> {
        val needle = query.trim()
        if (needle.isEmpty()) return list
        return list.filter { searchableText(it.raw).contains(needle, ignoreCase = true) }
    }

    private val WIFI_NAME = Regex("(?i)(?:^WIFI:|;)S:((?:\\\\.|[^;\\\\])*)")

    /** What a search may match: the whole text, except for Wi-Fi codes, where it is the network name. */
    internal fun searchableText(raw: String): String =
        if (raw.startsWith("WIFI:", ignoreCase = true)) WIFI_NAME.find(raw)?.groupValues?.get(1).orEmpty() else raw

    fun toJson(list: List<ScanHistoryEntry>): String {
        val array = JSONArray()
        list.forEach { entry ->
            array.put(
                JSONObject()
                    .put("raw", entry.raw)
                    .put("format", entry.format)
                    .put("type", entry.valueType)
                    .put("at", entry.savedAt)
                    .put("fav", entry.favourite)
            )
        }
        return array.toString()
    }

    /** The entries in [text], or an empty list when it is not valid; single damaged entries are skipped. */
    fun fromJson(text: String): List<ScanHistoryEntry> {
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        val result = ArrayList<ScanHistoryEntry>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val raw = item.optString("raw", "")
            if (raw.isEmpty()) continue
            result.add(ScanHistoryEntry(raw, item.optInt("format", 0), item.optInt("type", 0), item.optLong("at", 0L), item.optBoolean("fav", false)))
        }
        return result
    }
}

/**
 * The codes scanned in QR mode, kept on the device in one small JSON file (no database, no network). Every change is
 * applied and written on a background thread, in order, through a temporary file that then replaces the old one;
 * a failed write is ignored and the list stays correct in memory. Whether anything is recorded at all is the
 * "Save scan history" setting, which the caller checks before [add].
 */
@Singleton
class ScanHistoryStore internal constructor(private val file: File) {
    @Inject constructor(@ApplicationContext context: Context) : this(File(context.filesDir, FILE_NAME))

    private val worker: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "scan-history").apply { isDaemon = true }
    }
    private val state = MutableStateFlow<List<ScanHistoryEntry>>(emptyList())

    /** Newest first. Empty until the file has been read, a moment after start-up. */
    val entries: StateFlow<List<ScanHistoryEntry>> = state.asStateFlow()

    init {
        worker.execute { state.value = read() }
    }

    fun add(raw: String, format: Int, valueType: Int, now: Long = System.currentTimeMillis()) {
        if (raw.isEmpty()) return
        change { ScanHistory.add(it, ScanHistoryEntry(raw, format, valueType, now)) }
    }

    fun setFavourite(raw: String, favourite: Boolean) = change { ScanHistory.setFavourite(it, raw, favourite) }

    fun remove(raw: String) = change { ScanHistory.remove(it, raw) }

    fun clear() = change { emptyList() }

    /** The entries matching [query]; see [ScanHistory.filter]. */
    fun search(query: String): List<ScanHistoryEntry> = ScanHistory.filter(state.value, query)

    /** Waits until every change made so far has been applied and written. For tests. */
    internal fun awaitIdle() {
        worker.submit(Runnable { }).get()
    }

    private fun change(transform: (List<ScanHistoryEntry>) -> List<ScanHistoryEntry>) {
        worker.execute {
            val next = transform(state.value)
            state.value = next
            runCatching { write(next) }
        }
    }

    private fun read(): List<ScanHistoryEntry> =
        runCatching { if (file.exists()) ScanHistory.fromJson(file.readText()) else emptyList() }.getOrDefault(emptyList())

    private fun write(list: List<ScanHistoryEntry>) {
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeText(ScanHistory.toJson(list))
        if (!temporary.renameTo(file)) {
            file.delete()
            temporary.renameTo(file)
        }
    }

    private companion object {
        const val FILE_NAME = "scan_history.json"
    }
}
