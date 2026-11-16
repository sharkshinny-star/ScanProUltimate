package com.example.scanpro.feature.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The owner's 2026-11-16 clean-up of the Tools landing. */
class ToolCatalogueTest {
    private val tileIds = TOOL_SECTIONS.flatMap { it.tiles }.map { it.id }
    private val allIds = TOOL_SECTIONS.flatMap { it.tools }.map { it.id }

    @Test
    fun searchOnlyToolsHaveNoTileButStayInTheCatalogue() {
        SEARCH_ONLY_TOOL_IDS.forEach { id ->
            assertFalse("$id should have no tile", id in tileIds)
            assertTrue("$id should still be searchable", id in allIds)
        }
    }

    @Test
    fun theImageToolsSitBehindOneTileInConvert() {
        assertEquals(listOf(IMAGE_TOOLS_TILE.id), tileIds.filter { it == IMAGE_TOOLS_TILE.id })
        assertTrue(TOOL_SECTIONS.first { it.id == "convert" }.tiles.contains(IMAGE_TOOLS_TILE))
        assertTrue(TOOL_SECTIONS.first { it.id == "image" }.tiles.isEmpty())
    }

    @Test
    fun powerPointIsGoneAndExcelIsBeta() {
        assertFalse("pdf2ppt" in allIds)
        assertTrue(TOOL_SECTIONS.flatMap { it.tools }.first { it.id == "pdf2excel" }.beta)
        assertTrue(TOOL_SECTIONS.flatMap { it.tools }.filter { it.id != "pdf2excel" }.none { it.beta })
    }

    @Test
    fun organiseIsStillATile() {
        assertTrue("organize" in tileIds)
    }
}
