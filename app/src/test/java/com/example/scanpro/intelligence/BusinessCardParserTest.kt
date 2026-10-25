package com.example.scanpro.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessCardParserTest {
    @Test
    fun `reads a typical card`() {
        val card = BusinessCardParser.parse(
            """
            Northwind Technologies Pvt Ltd
            Priya Sharma
            Senior Product Manager
            Mob: +91 98450 12345
            Tel: 080-4123 4567
            priya.sharma@northwind.in
            www.northwind.in
            4th Floor, Prestige Tower, MG Road
            Bengaluru 560001
            """.trimIndent()
        )
        assertEquals("Priya Sharma", card.name)
        assertEquals("Senior Product Manager", card.jobTitle)
        assertEquals("Northwind Technologies Pvt Ltd", card.company)
        assertEquals(listOf("+91 98450 12345", "080-4123 4567"), card.phones)
        assertEquals(listOf("priya.sharma@northwind.in"), card.emails)
        assertEquals("www.northwind.in", card.website)
        assertEquals("4th Floor, Prestige Tower, MG Road, Bengaluru 560001", card.address)
        assertTrue(card.isUseful)
    }

    @Test
    fun `company falls back to the email domain`() {
        val card = BusinessCardParser.parse("John Carter\nFounder\njohn@acmecorp.com\n+1 415 555 0134")
        assertEquals("John Carter", card.name)
        assertEquals("Founder", card.jobTitle)
        assertEquals("Acmecorp", card.company)
    }

    @Test
    fun `webmail domains are never used as a company`() {
        val card = BusinessCardParser.parse("Anita Rao\nanita.rao@gmail.com\n98450 12345")
        assertNull(card.company)
        assertEquals("Anita Rao", card.name)
    }

    @Test
    fun `text with no contact details is not useful`() {
        assertFalse(BusinessCardParser.parse("meeting notes\nbuy milk").isUseful)
    }
}
