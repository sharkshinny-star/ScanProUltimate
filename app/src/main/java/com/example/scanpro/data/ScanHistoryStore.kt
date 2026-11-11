package com.example.scanpro.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
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
 * The codes scanned in QR mode, kept on the device in one small file (no database, no network). QR codes can hold
 * Wi-Fi passwords, tokens and addresses, so the file is encrypted with an Android Keystore AES-GCM key that never
 * leaves this phone ([ScanHistoryProtection]); the constructor without a protector exists for JVM tests.
 *
 * Every change is applied and written on a background thread, in order, through a temporary file that then replaces
 * the old one. A plaintext history from an older version is encrypted in place once it has been read successfully.
 * A file that cannot be read or decrypted (wrong key, damage) is never overwritten: new entries pause and the screen
 * says so, and only an explicit "Clear all" may replace it. Whether anything is recorded at all is the "Save scan
 * history" setting, which the caller checks before [add].
 */
@Singleton
class ScanHistoryStore internal constructor(
    private val file: File,
    private val protectorFactory: (() -> ScanHistoryProtection)? = null
) {
    @Inject constructor(@ApplicationContext context: Context) : this(
        File(context.filesDir, FILE_NAME),
        { ScanHistoryProtection.fromAndroidKeystore() }
    )

    private val worker: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "scan-history").apply { isDaemon = true }
    }
    private val state = MutableStateFlow<List<ScanHistoryEntry>>(emptyList())

    /** Created on the worker thread the first time it is needed (Keystore work must stay off the main thread). */
    private val protector by lazy(LazyThreadSafetyMode.NONE) { protectorFactory?.invoke() }

    /** Set when the saved file could not be read or a protected write failed: changes stop so nothing is overwritten. */
    private var blocked = false
    private val available = MutableStateFlow(true)

    /** False while the saved history cannot be opened or saved securely; the history screen explains it. */
    val storageAvailable: StateFlow<Boolean> = available.asStateFlow()

    /** Newest first. Empty until the file has been read, a moment after start-up. */
    val entries: StateFlow<List<ScanHistoryEntry>> = state.asStateFlow()

    init {
        worker.execute { load() }
    }

    fun add(raw: String, format: Int, valueType: Int, now: Long = System.currentTimeMillis()) {
        if (raw.isEmpty()) return
        change { ScanHistory.add(it, ScanHistoryEntry(raw, format, valueType, now)) }
    }

    fun setFavourite(raw: String, favourite: Boolean) = change { ScanHistory.setFavourite(it, raw, favourite) }

    fun remove(raw: String) = change { ScanHistory.remove(it, raw) }

    /** Empties the history. The one change allowed while the saved file cannot be opened: the user confirmed it. */
    fun clear() {
        worker.execute {
            state.value = emptyList()
            runCatching { write(emptyList()) }
                .onSuccess { unblock() }
                .onFailure { block() }
        }
    }

    /** The entries matching [query]; see [ScanHistory.filter]. */
    fun search(query: String): List<ScanHistoryEntry> = ScanHistory.filter(state.value, query)

    /** Waits until every change made so far has been applied and written. For tests. */
    internal fun awaitIdle() {
        worker.submit(Runnable { }).get()
    }

    private fun load() {
        if (!file.exists()) return
        val bytes = runCatching { file.readBytes() }.getOrElse { return block() }
        val encrypted = ScanHistoryProtection.isProtected(bytes)
        val text = runCatching {
            if (encrypted) requireNotNull(protector) { "No key for an encrypted history" }.open(bytes)
            else bytes.toString(Charsets.UTF_8)
        }.getOrElse { return block() } // wrong or missing key, truncated file, failed authentication
        // A damaged file must not be read (and later saved) as an empty list.
        if (runCatching { JSONArray(text) }.isFailure) return block()
        state.value = ScanHistory.fromJson(text)
        if (!encrypted && protectorFactory != null) {
            // Encrypt the old plaintext history in place, keeping every entry and favourite. If the Keystore cannot,
            // stop rather than go on writing plaintext.
            runCatching { write(state.value) }.onFailure { block() }
        }
    }

    private fun change(transform: (List<ScanHistoryEntry>) -> List<ScanHistoryEntry>) {
        worker.execute {
            if (blocked) return@execute
            val next = transform(state.value)
            state.value = next
            runCatching { write(next) }.onFailure { if (protectorFactory != null) block() }
        }
    }

    private fun block() {
        blocked = true
        available.value = false
    }

    private fun unblock() {
        blocked = false
        available.value = true
    }

    private fun write(list: List<ScanHistoryEntry>) {
        val json = ScanHistory.toJson(list)
        val bytes = protector?.seal(json) ?: json.toByteArray(Charsets.UTF_8)
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.outputStream().use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
        // Same-folder rename replaces the file in one step; the previous good file is never deleted first.
        if (!temporary.renameTo(file)) {
            temporary.delete()
            throw IOException("Could not replace the scan history file")
        }
    }

    private companion object {
        const val FILE_NAME = "scan_history.json"
    }
}
