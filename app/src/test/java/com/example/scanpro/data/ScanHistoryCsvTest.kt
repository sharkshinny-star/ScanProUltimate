package com.example.scanpro.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanHistoryCsvTest {
    @Test
    fun `rows hold UTC time, source, format, favourite and quoted content`() {
        val csv = ScanHistoryCsv.export(
            listOf(
                ScanHistoryEntry("hello, \"world\"\nline 2", 256, 7, 0L, favourite = true, created = true),
                ScanHistoryEntry("4006381333931", 32, 5, 86_400_000L)
            )
        )
        val expected = ScanHistoryCsv.HEADER + "\r\n" +
            "1970-01-01T00:00:00Z,created,QR_CODE,yes,\"hello, \"\"world\"\"\nline 2\"\r\n" +
            "1970-01-02T00:00:00Z,scanned,EAN_13,no,4006381333931\r\n"
        assertEquals(expected, csv)
    }

    @Test
    fun `a Wi-Fi password never reaches the file`() {
        val csv = ScanHistoryCsv.export(listOf(ScanHistoryEntry("WIFI:T:WPA;S:Home\\;Net;P:se\\;cret;H:true;;", 256, 9, 0L)))
        assertFalse(csv.contains("se\\;cret"))
        assertFalse(csv.contains("P:"))
        assertTrue(csv.contains("WIFI:T:WPA;S:Home\\;Net;H:true;;"))
    }

    @Test
    fun `formula-looking cells are made plain text`() {
        assertEquals("'=HYPERLINK(1)", ScanHistoryCsv.cell("=HYPERLINK(1)"))
        assertEquals("'+91 98450", ScanHistoryCsv.cell("+91 98450"))
        assertEquals("'@SUM(A1)", ScanHistoryCsv.cell("@SUM(A1)"))
        assertEquals("\"'-1,2\"", ScanHistoryCsv.cell("-1,2"))
        assertEquals("plain", ScanHistoryCsv.cell("plain"))
    }

    @Test
    fun `filters and undo restore`() {
        val a = ScanHistoryEntry("a", 256, 7, 3L, favourite = true)
        val b = ScanHistoryEntry("b", 256, 7, 2L, created = true)
        val c = ScanHistoryEntry("c", 256, 7, 1L)
        val list = listOf(a, b, c)
        assertEquals(listOf(a, c), ScanHistory.only(list, ScanHistoryFilter.SCANNED))
        assertEquals(listOf(b), ScanHistory.only(list, ScanHistoryFilter.CREATED))
        assertEquals(listOf(a), ScanHistory.only(list, ScanHistoryFilter.FAVOURITES))
        val removed = ScanHistory.remove(list, "b")
        assertEquals(list, ScanHistory.restore(removed, b, 1))
        assertEquals(listOf(a, c, b), ScanHistory.restore(removed, b, 99))
        // Scanned again before Undo: no duplicate.
        val again = ScanHistory.add(removed, b.copy(savedAt = 9L))
        assertEquals(again, ScanHistory.restore(again, b, 1))
    }

    @Test
    fun `the created mark survives JSON`() {
        val list = listOf(ScanHistoryEntry("x", 256, 7, 5L, created = true), ScanHistoryEntry("y", 256, 7, 4L))
        assertEquals(list, ScanHistory.fromJson(ScanHistory.toJson(list)))
        // Older files have no "made" key: read as scanned.
        assertFalse(ScanHistory.fromJson("[{\"raw\":\"z\",\"format\":256,\"type\":7,\"at\":1}]").single().created)
    }
}
