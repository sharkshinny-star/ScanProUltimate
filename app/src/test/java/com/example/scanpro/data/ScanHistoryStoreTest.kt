package com.example.scanpro.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScanHistoryStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun entry(raw: String, at: Long = 1L, favourite: Boolean = false) = ScanHistoryEntry(raw, 256, 7, at, favourite)

    private fun newStore(file: File = File(folder.root, "scan_history.json")) = ScanHistoryStore(file)

    @Test
    fun `newest first and a repeat moves to the top with the new time`() {
        var list = emptyList<ScanHistoryEntry>()
        list = ScanHistory.add(list, entry("a", 1))
        list = ScanHistory.add(list, entry("b", 2))
        list = ScanHistory.add(list, entry("a", 3))
        assertEquals(listOf("a", "b"), list.map { it.raw })
        assertEquals(3L, list.first().savedAt)
    }

    @Test
    fun `a repeat keeps its favourite mark`() {
        var list = ScanHistory.add(emptyList(), entry("a", 1))
        list = ScanHistory.setFavourite(list, "a", true)
        list = ScanHistory.add(list, entry("a", 2))
        assertTrue(list.single().favourite)
    }

    @Test
    fun `capped at 200, dropping the oldest and keeping favourites`() {
        var list = ScanHistory.add(emptyList(), entry("keep", 0, favourite = true))
        (1..ScanHistory.LIMIT + 20).forEach { list = ScanHistory.add(list, entry("code $it", it.toLong())) }
        assertEquals(ScanHistory.LIMIT, list.size)
        assertEquals("code ${ScanHistory.LIMIT + 20}", list.first().raw)
        assertTrue("the favourite is still there", list.any { it.raw == "keep" })
        assertFalse("the oldest plain entry went", list.any { it.raw == "code 1" })
    }

    @Test
    fun `search ignores case and spaces around the query`() {
        val list = listOf(entry("https://Example.com/pay"), entry("WIFI:T:WPA;S:home;P:x;;"), entry("hello"))
        assertEquals(listOf("https://Example.com/pay"), ScanHistory.filter(list, "  example ").map { it.raw })
        assertEquals(3, ScanHistory.filter(list, "   ").size)
        assertEquals(0, ScanHistory.filter(list, "nothing like this").size)
    }

    @Test fun searchOnAWifiCodeMatchesTheNetworkNameButNeverThePassword() {
        val wifi = entry("WIFI:T:WPA;S:Cafe Net;P:hunter2secret;;")
        val list = listOf(wifi)
        assertEquals(1, ScanHistory.filter(list, "cafe").size)
        assertEquals(0, ScanHistory.filter(list, "hunter2").size)
        assertEquals(0, ScanHistory.filter(list, "WPA").size)
    }

    @Test
    fun `json round trip keeps every field and skips damaged entries`() {
        val list = listOf(entry("a\nb \"quoted\"", 5, favourite = true), entry("c", 6))
        assertEquals(list, ScanHistory.fromJson(ScanHistory.toJson(list)))
        assertEquals(emptyList<ScanHistoryEntry>(), ScanHistory.fromJson("not json"))
        assertEquals(listOf("ok"), ScanHistory.fromJson("""[{"raw":"ok"},{"nothing":1},5]""").map { it.raw })
    }

    @Test
    fun `the store saves to a file and a new store reads it back`() {
        val file = File(folder.root, "scan_history.json")
        val store = newStore(file)
        store.add("first", 256, 8, now = 10)
        store.add("second", 256, 7, now = 20)
        store.setFavourite("first", true)
        store.awaitIdle()
        assertEquals(listOf("second", "first"), store.entries.value.map { it.raw })

        val reopened = newStore(file)
        reopened.awaitIdle()
        assertEquals(store.entries.value, reopened.entries.value)
        assertTrue(reopened.entries.value.last().favourite)
        assertFalse("no temporary file is left behind", File(folder.root, "scan_history.json.tmp").exists())
    }

    @Test
    fun `remove and clear are written too`() {
        val file = File(folder.root, "scan_history.json")
        val store = newStore(file)
        store.add("a", 0, 7, now = 1)
        store.add("b", 0, 7, now = 2)
        store.remove("a")
        store.awaitIdle()
        assertEquals(listOf("b"), newStore(file).also { it.awaitIdle() }.entries.value.map { it.raw })
        store.clear()
        store.awaitIdle()
        assertEquals(emptyList<ScanHistoryEntry>(), newStore(file).also { it.awaitIdle() }.entries.value)
    }

    @Test
    fun `a corrupt file reads as empty and a missing folder does not crash`() {
        val file = File(folder.root, "scan_history.json")
        file.writeText("{{{ broken")
        val store = newStore(file)
        store.awaitIdle()
        assertTrue(store.entries.value.isEmpty())

        // A write that cannot succeed is ignored; the list is still right in memory.
        val orphan = newStore(File(File(folder.root, "missing-folder"), "scan_history.json"))
        orphan.add("kept in memory", 0, 7)
        orphan.awaitIdle()
        assertEquals(listOf("kept in memory"), orphan.entries.value.map { it.raw })
    }

    @Test
    fun `search on the store uses the current list`() {
        val store = newStore()
        store.add("alpha", 0, 7, now = 1)
        store.add("beta", 0, 7, now = 2)
        store.awaitIdle()
        assertEquals(listOf("beta"), store.search("BET").map { it.raw })
    }
}
