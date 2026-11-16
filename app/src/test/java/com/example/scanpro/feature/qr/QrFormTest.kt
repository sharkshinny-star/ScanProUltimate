package com.example.scanpro.feature.qr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class QrFormTest {
    @Test
    fun `each kind builds its payload from the shared map`() {
        val values = mapOf(
            QrForm.TEXT to "hi", QrForm.LINK to "example.com", QrForm.SSID to "Home", QrForm.SECURITY to "NONE",
            QrForm.PHONE to "123", QrForm.SMS_NUMBER to "123", QrForm.EMAIL to "a@b.in", QrForm.NAME to "Asha",
            QrForm.LATITUDE to "1", QrForm.LONGITUDE to "2", QrForm.EVENT_TITLE to "X", QrForm.START_DATE to "2026-01-02",
            QrForm.UPI_ADDRESS to "a@upi", QrForm.EAN to "400638133393", QrForm.CODE_128 to "ABC"
        )
        QrKind.entries.forEach { assertNotNull(it.name, QrForm.payload(it, values)) }
        assertEquals("WIFI:T:nopass;S:Home;;", QrForm.payload(QrKind.WIFI, values))
        assertEquals("4006381333931", QrForm.payload(QrKind.EAN_13, values))
        assertNull(QrForm.payload(QrKind.EMAIL, mapOf(QrForm.EMAIL to "not-an-address")))
    }

    @Test
    fun `problems are reported only for filled fields`() {
        assertNull(QrForm.problem(QrForm.LATITUDE, emptyMap()))
        assertEquals(FieldProblem.LATITUDE, QrForm.problem(QrForm.LATITUDE, mapOf(QrForm.LATITUDE to "91")))
        assertEquals(FieldProblem.EMAIL, QrForm.problem(QrForm.EMAIL, mapOf(QrForm.EMAIL to "x@")))
        assertEquals(FieldProblem.DATE, QrForm.problem(QrForm.START_DATE, mapOf(QrForm.START_DATE to "2026-02-30")))
        val event = mapOf(QrForm.START_DATE to "2026-01-02", QrForm.START_TIME to "10:00", QrForm.END_TIME to "09:00")
        assertEquals(FieldProblem.END_BEFORE_START, QrForm.problem(QrForm.END_TIME, event))
        assertEquals(FieldProblem.EAN_CHECK, QrForm.problem(QrForm.EAN, mapOf(QrForm.EAN to "4006381333932")))
        assertEquals(1, QrForm.eanExpectedCheckDigit(mapOf(QrForm.EAN to "4006381333932")))
        assertEquals(1, QrForm.eanCheckDigitToAdd(mapOf(QrForm.EAN to "400638133393")))
        assertEquals(FieldProblem.AMOUNT, QrForm.problem(QrForm.UPI_AMOUNT, mapOf(QrForm.UPI_AMOUNT to "1.234")))
        assertEquals(FieldProblem.CODE_128_CHARACTERS, QrForm.problem(QrForm.CODE_128, mapOf(QrForm.CODE_128 to "é")))
    }
}
