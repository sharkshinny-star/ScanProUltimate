package com.example.scanpro.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormFieldRulesTest {
    @Test
    fun `labels prefer the tooltip, then a readable field name`() {
        assertEquals("Applicant's full name", FormFieldRules.label("Applicant's full name", "name1", "form.name1"))
        assertEquals("First name", FormFieldRules.label(null, "first_name", "page1.first_name"))
        assertEquals("Date of birth", FormFieldRules.label("  ", "dateOfBirth", "dateOfBirth"))
        assertEquals("Phone", FormFieldRules.label(null, "Phone[0]", "topmostSubform[0].Phone[0]"))
        assertEquals("Zip code", FormFieldRules.label(null, null, "address.zip-code"))
    }

    private val name = FormField("name", "Name", FormFieldKind.TEXT, "Old", maxLength = 5)
    private val agree = FormField("agree", "Agree", FormFieldKind.CHECKBOX, "")
    private val size = FormField("size", "Size", FormFieldKind.RADIO, "S", listOf(FormOption("S", "S"), FormOption("M", "M")))
    private val locked = FormField("id", "ID", FormFieldKind.TEXT, "123", readOnly = true)
    private val city = FormField("city", "City", FormFieldKind.CHOICE, "", listOf(FormOption("ber", "Berlin")))
    private val tags = FormField("tags", "Tags", FormFieldKind.MULTI_CHOICE, "", listOf(FormOption("a", "A"), FormOption("b", "B")))

    @Test
    fun `only changed, writable fields are written`() {
        val fields = listOf(name, agree, size, locked)
        val edited = mapOf("name" to "Old", "agree" to "on", "size" to "M", "id" to "999")
        assertEquals(mapOf("agree" to FormFieldRules.CHECKED, "size" to "M"), FormFieldRules.changes(fields, edited))
    }

    @Test
    fun `values are kept within what each field allows`() {
        assertEquals("Annab", FormFieldRules.clamp(name, "Annabel"))
        assertEquals("", FormFieldRules.clamp(size, "XL"))
        assertEquals("", FormFieldRules.clamp(city, "Paris"))
        assertEquals("Paris", FormFieldRules.clamp(city.copy(editable = true), "Paris"))
        assertEquals("a\nb", FormFieldRules.clamp(tags, "a\nz\nb"))
        assertEquals(FormFieldRules.CHECKED, FormFieldRules.clamp(agree, "yes"))
    }

    @Test
    fun `multiple choices round-trip`() {
        val joined = FormFieldRules.joinMulti(listOf("a", "b"))
        assertEquals(listOf("a", "b"), FormFieldRules.multiValues(joined))
        assertTrue(FormFieldRules.multiValues("").isEmpty())
    }
}
