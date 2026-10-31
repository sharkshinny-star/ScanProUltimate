package com.example.scanpro.feature.document

import com.example.scanpro.data.AnnotationType
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.data.OcrScript
import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageAnnotation
import com.example.scanpro.data.PageFilter
import com.example.scanpro.data.PdfPageSize
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ScanMode
import com.example.scanpro.data.WatermarkPosition
import com.example.scanpro.data.WatermarkSize
import com.example.scanpro.data.WatermarkStyle
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentEditTest {
    private val every: List<DocumentEdit> = listOf(
        DocumentEdit.Rename("Tax return 2026"),
        DocumentEdit.SetTags("tax, 2026"),
        DocumentEdit.SetFolder("Finance"),
        DocumentEdit.ToggleFavorite,
        DocumentEdit.SetPdfQuality(PdfQuality.SMALL),
        DocumentEdit.SetPdfPageSize(PdfPageSize.LETTER),
        DocumentEdit.SetSearchablePdf(false),
        DocumentEdit.SetPageNumbers(true),
        DocumentEdit.SetScanMode(ScanMode.RECEIPT),
        DocumentEdit.ApplyFilter("p1", PageFilter.BW),
        DocumentEdit.RotatePage("p1"),
        DocumentEdit.ApplyCrop("p1", CropQuad(topLeft = NormalizedPoint(0.1f, 0.05f), bottomRight = NormalizedPoint(0.9f, 0.95f))),
        DocumentEdit.ApplyAdjustments("p1", PageAdjustments(brightness = 0.2f, contrast = 1.3f, removeShadows = true), allPages = true),
        DocumentEdit.ResetPage("p1"),
        DocumentEdit.MovePage("p1", -2),
        DocumentEdit.DuplicatePage("p1"),
        DocumentEdit.ExtractPage("p1"),
        DocumentEdit.SplitBookSpread("p1", 0.48f),
        DocumentEdit.DeletePage("p1"),
        DocumentEdit.DuplicateDocument,
        DocumentEdit.AddTextAnnotation("p1", "Paid"),
        DocumentEdit.AddHighlight("p1"),
        DocumentEdit.AddRedaction("p1"),
        DocumentEdit.RemoveLastAnnotation("p1"),
        DocumentEdit.ReplaceAnnotations(
            "p1",
            listOf(PageAnnotation(id = "a", type = AnnotationType.TEXT, x = 0.1f, y = 0.2f, width = 0.3f, height = 0.1f, text = "Hi"))
        ),
        DocumentEdit.AddWatermark(WatermarkStyle("CONFIDENTIAL", WatermarkSize.SMALL, WatermarkPosition.DIAGONAL, color = 0x7F112233, opacity = 0.5f)),
        DocumentEdit.MergeWith("doc-2"),
        DocumentEdit.UpdatePageOcrText("p1", "Line one\nLine two"),
        DocumentEdit.ReOcr(null),
        DocumentEdit.ReOcr(OcrScript.DEVANAGARI),
        DocumentEdit.SplitDocument,
        DocumentEdit.ReplaceText("p1", TextEdit(0.1f, 0.2f, 0.5f, 0.25f, "Total 42.00")),
        DocumentEdit.Erase(
            "p1",
            mapOf(0.02f to listOf(listOf(NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.2f, 0.15f))), 0.05f to emptyList())
        )
    )

    @Test
    fun `every journaled edit reads back exactly as written`() {
        every.forEach { edit ->
            // Through text, as on disk.
            val json = JSONObject(DocumentEdit.encode("doc-1", edit).toString())
            assertEquals(edit.type, "doc-1" to edit, DocumentEdit.decode(json))
        }
    }

    @Test
    fun `every edit type has its own name`() {
        assertEquals(every.map { it.type }.distinct().size + 1, every.size) // ReOcr appears twice.
    }

    @Test
    fun `records from another version or of an unknown kind are skipped`() {
        val record = DocumentEdit.encode("doc-1", DocumentEdit.RotatePage("p1"))
        assertNull(DocumentEdit.decode(JSONObject(record.toString()).put("v", 99)))
        assertNull(DocumentEdit.decode(JSONObject(record.toString()).put("type", "exportPassword")))
        assertNull(DocumentEdit.decode(JSONObject(record.toString()).put("args", JSONObject())))
    }
}
