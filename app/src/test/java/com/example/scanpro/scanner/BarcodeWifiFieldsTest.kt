package com.example.scanpro.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** R41: a network name or password containing an escaped `;` must not be read as a separate field. */
class BarcodeWifiFieldsTest {
    @Test
    fun anEscapedSemicolonStaysInsideItsField() {
        val fields = wifiFields("WIFI:T:WPA;S:cafe\\;T:SAE;P:secret;;")
        assertTrue("T:WPA" in fields)
        assertTrue("S:cafe\\;T:SAE" in fields)
        assertFalse("a fake T:SAE field from inside the name", "T:SAE" in fields)
    }

    @Test
    fun plainFieldsStillSplit() {
        val fields = wifiFields("WIFI:T:SAE;S:home;H:true;;")
        assertTrue("T:SAE" in fields)
        assertTrue("H:true" in fields)
    }
}
