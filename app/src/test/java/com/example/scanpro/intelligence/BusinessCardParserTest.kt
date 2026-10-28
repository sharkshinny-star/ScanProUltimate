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
    fun `company falls back to the website host without its scheme`() {
        val card = BusinessCardParser.parse("John Carter\nFounder\nhttps://www.acme.com\n+1 415 555 0134")
        assertEquals("https://www.acme.com", card.website)
        assertEquals("Acme", card.company)
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
    fun `names and titles starting with label letters keep their first letter`() {
        val card = BusinessCardParser.parse("Mark Taylor\nMarketing Manager\nTata Consultancy Services\nM: +44 20 7946 0958\nE: mark@tcs.com")
        assertEquals("Mark Taylor", card.name)
        assertEquals("Marketing Manager", card.jobTitle)
        assertEquals("Tata Consultancy Services", card.company)
        assertEquals(listOf("+44 20 7946 0958"), card.phones)
        val other = BusinessCardParser.parse("Emma Watson\nPhilip Wong\nTel 080 4123 4567")
        assertEquals("Emma Watson", other.name)
        assertEquals(listOf("080 4123 4567"), other.phones)
    }

    @Test
    fun `text with no contact details is not useful`() {
        assertFalse(BusinessCardParser.parse("meeting notes\nbuy milk").isUseful)
    }
}
