package com.example.scanpro.data

import java.io.File
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScanHistoryProtectionTest {
    @get:Rule val folder = TemporaryFolder()

    private fun cipher(seed: Byte = 5): ScanHistoryProtection =
        ScanHistoryProtection(SecretKeySpec(ByteArray(32) { (it + seed).toByte() }, "AES"))

    @Test fun `encrypted history round trips without exposing Wi-Fi passwords`() {
        val payload = "WIFI:T:WPA;S:Office;P:secret-123;;"
        val protected = cipher().seal(payload)
        assertTrue(ScanHistoryProtection.isProtected(protected))
        assertFalse(protected.toString(Charsets.UTF_8).contains("secret-123"))
        assertEquals(payload, cipher().open(protected))
        assertFalse("fresh GCM nonces are required", protected.contentEquals(cipher().seal(payload)))
    }

    @Test fun `tampering or wrong key cannot reveal history`() {
        val bytes = cipher().seal("sensitive")
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        assertTrue(runCatching { cipher().open(bytes) }.isFailure)
        assertTrue(runCatching { cipher(6).open(cipher().seal("secret")) }.isFailure)
        assertTrue(runCatching { cipher().open(byteArrayOf(83, 80, 72, 1)) }.isFailure)
    }

    @Test fun `legacy plaintext is migrated while keeping entries and favourites`() {
        val file = File(folder.root, "scan_history.json")
        val original = listOf(ScanHistoryEntry("WIFI:T:WPA;S:Home;P:do-not-leak;;", 256, 9, 10, true))
        file.writeText(ScanHistory.toJson(original))
        val store = ScanHistoryStore(file) { cipher() }
        store.awaitIdle()
        assertEquals(original, store.entries.value)
        assertTrue(ScanHistoryProtection.isProtected(file.readBytes()))
        assertFalse(file.readText().contains("do-not-leak"))
        val reopened = ScanHistoryStore(file) { cipher() }
        reopened.awaitIdle()
        assertEquals(original, reopened.entries.value)
    }

    @Test fun `malformed legacy history cannot be overwritten by a new scan`() {
        val file = File(folder.root, "scan_history.json")
        file.writeText("{broken-json")
        val original = file.readBytes()
        val store = ScanHistoryStore(file) { cipher() }
        store.awaitIdle()
        assertFalse(store.storageAvailable.value)
        store.add("new-secret", 256, 7)
        store.awaitIdle()
        assertTrue(file.readBytes().contentEquals(original))
    }

    @Test fun `encrypted write failures pause history instead of pretending it is saved`() {
        val file = File(File(folder.root, "missing-folder"), "scan_history.json")
        val store = ScanHistoryStore(file) { cipher() }
        store.add("WIFI:T:WPA;S:Office;P:private;;", 256, 7)
        store.awaitIdle()
        assertFalse(store.storageAvailable.value)
        assertFalse(file.exists())
    }

    @Test fun `key loss never replaces an unreadable encrypted history`() {
        val file = File(folder.root, "scan_history.json")
        file.writeBytes(cipher().seal(ScanHistory.toJson(listOf(ScanHistoryEntry("keep", 256, 7, 1)))))
        val untouched = file.readBytes()
        val store = ScanHistoryStore(file) { cipher(9) }
        store.awaitIdle()
        assertFalse(store.storageAvailable.value)
        store.add("must not replace", 256, 7)
        store.awaitIdle()
        assertTrue(file.readBytes().contentEquals(untouched))
        assertTrue(store.entries.value.isEmpty())
        // The user may explicitly confirm Clear all to reset an unreadable history.
        store.clear()
        store.awaitIdle()
        assertTrue(store.storageAvailable.value)
        assertEquals(emptyList<ScanHistoryEntry>(), ScanHistory.fromJson(cipher(9).open(file.readBytes())))
    }
}
