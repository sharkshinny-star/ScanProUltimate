package com.example.scanpro.imaging

import org.junit.Assert.assertEquals
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
}
