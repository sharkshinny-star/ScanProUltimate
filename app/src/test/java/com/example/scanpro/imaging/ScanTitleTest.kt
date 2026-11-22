package com.example.scanpro.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class ScanTitleTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val created = 1_762_560_000_000L // 2025-11-08 00:00 UTC

    @Test
    fun `no pattern keeps the smart title, else the fallback`() {
        assertEquals("Invoice – Acme", ScanTitle.build("", "Invoice – Acme", "Document", created, "Scan 2025-11-08", utc))
        assertEquals("Scan 2025-11-08", ScanTitle.build("", null, "Document", created, "Scan 2025-11-08", utc))
        assertEquals("Scan 2025-11-08", ScanTitle.build("", "  ", "Document", created, "Scan 2025-11-08", utc))
    }

    @Test
    fun `tokens expand`() {
        assertEquals("Receipt 2025-11-08 00-00", ScanTitle.build("{mode} {date} {time}", null, "Receipt", created, "x", utc))
        assertEquals("Acme – 2025-11-08", ScanTitle.build("{text} – {date}", "Acme", "Document", created, "x", utc))
    }

    @Test
    fun `a missing text token leaves no dangling separator`() {
        assertEquals("2025-11-08", ScanTitle.build("{text} – {date}", null, "Document", created, "x", utc))
        assertEquals("Receipt – 2025-11-08", ScanTitle.build("{mode} – {text} – {date}", "", "Receipt", created, "x", utc))
        assertEquals("fallback", ScanTitle.build("{text}", null, "Document", created, "fallback", utc))
    }

    @Test
    fun `tokens match in any case and unknown ones stay as typed`() {
        assertEquals("Receipt 2025-11-08", ScanTitle.build("{Mode} {DATE}", null, "Receipt", created, "x", utc))
        assertEquals("{dat} Receipt", ScanTitle.build("{dat} {mode}", null, "Receipt", created, "x", utc))
        assertEquals(listOf("{dat}", "{foo}"), ScanTitle.unknownTokens("{dat} {mode} {foo} {Date} {dat}"))
        assertEquals(emptyList<String>(), ScanTitle.unknownTokens("{text} – {TIME}"))
    }

    @Test
    fun `page text is never expanded as a token`() {
        assertEquals("Due {date} – 2025-11-08", ScanTitle.build("{text} – {date}", "Due {date}", "Document", created, "x", utc))
    }

    @Test
    fun `inserting a token keeps words apart and puts the cursor after it`() {
        assertEquals("{date}" to 6, ScanTitle.insertToken("", 0, 0, "{date}"))
        assertEquals("Scan {date}" to 11, ScanTitle.insertToken("Scan", 4, 4, "{date}"))
        assertEquals("Scan {date} {mode}" to 12, ScanTitle.insertToken("Scan {mode}", 5, 5, "{date}"))
        assertEquals("a {date} b" to 8, ScanTitle.insertToken("a b", 1, 1, "{date}"))
        assertEquals("Scan {time}" to 11, ScanTitle.insertToken("Scan {date}", 5, 11, "{time}"))
        assertEquals("x {mode}" to 8, ScanTitle.insertToken("x", 99, 99, "{mode}"))
    }

    @Test
    fun `every preset gives a name`() {
        ScanTitle.PRESETS.forEach { preset ->
            assertTrue(preset, ScanTitle.build(preset, null, "Document", created, "", utc).isNotBlank())
            assertEquals(preset, emptyList<String>(), ScanTitle.unknownTokens(preset))
        }
    }
}
