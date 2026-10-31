package com.example.scanpro.feature.document

import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.JsonCodec
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
import org.json.JSONArray
import org.json.JSONObject

/**
 * A change to one document, as data, so that [DocumentEditor] can write it to the [com.example.scanpro.core.operations.EditJournal]
 * while it waits in the queue and run it after a restart.
 *
 * Only edits whose inputs can be written down safely are here. Left out on purpose, and still run straight away:
 * exports and shares (the share sheet belongs to the moment they were asked for), anything with a password (never
 * written to disk), signatures (a drawn image), pages added from other apps (their read access ends with the process)
 * and undo/redo (the history lives in memory).
 */
sealed interface DocumentEdit {
    val type: String
    fun encodeArgs(json: JSONObject) {}

    data class Rename(val title: String) : DocumentEdit {
        override val type = "rename"
        override fun encodeArgs(json: JSONObject) { json.put("title", title) }
    }
    data class SetTags(val tags: String) : DocumentEdit {
        override val type = "tags"
        override fun encodeArgs(json: JSONObject) { json.put("tags", tags) }
    }
    data class SetFolder(val folder: String) : DocumentEdit {
        override val type = "folder"
        override fun encodeArgs(json: JSONObject) { json.put("folder", folder) }
    }
    data object ToggleFavorite : DocumentEdit { override val type = "favorite" }
    data class SetPdfQuality(val quality: PdfQuality) : DocumentEdit {
        override val type = "pdfQuality"
        override fun encodeArgs(json: JSONObject) { json.put("value", quality.name) }
    }
    data class SetPdfPageSize(val size: PdfPageSize) : DocumentEdit {
        override val type = "pageSize"
        override fun encodeArgs(json: JSONObject) { json.put("value", size.name) }
    }
    data class SetSearchablePdf(val enabled: Boolean) : DocumentEdit {
        override val type = "searchable"
        override fun encodeArgs(json: JSONObject) { json.put("enabled", enabled) }
    }
    data class SetPageNumbers(val enabled: Boolean) : DocumentEdit {
        override val type = "pageNumbers"
        override fun encodeArgs(json: JSONObject) { json.put("enabled", enabled) }
    }
    data class SetScanMode(val mode: ScanMode) : DocumentEdit {
        override val type = "scanMode"
        override fun encodeArgs(json: JSONObject) { json.put("value", mode.name) }
    }
    data class ApplyFilter(val pageId: String, val filter: PageFilter) : DocumentEdit {
        override val type = "filter"
        override fun encodeArgs(json: JSONObject) { json.put("page", pageId).put("value", filter.name) }
    }
    data class RotatePage(val pageId: String) : DocumentEdit {
        override val type = "rotate"
        override fun encodeArgs(json: JSONObject) { json.put("page", pageId) }
    }
    data class ApplyCrop(val pageId: String, val crop: CropQuad) : DocumentEdit {
        override val type = "crop"
        override fun encodeArgs(json: JSONObject) { json.put("page", pageId).put("crop", JsonCodec.encodeCrop(crop)) }
    }
    data class ApplyAdjustments(val pageId: String, val adjustments: PageAdjustments, val allPages: Boolean) : DocumentEdit {
        override val type = "adjust"
        override fun encodeArgs(json: JSONObject) {
            json.put("page", pageId).put("adjustments", JsonCodec.encodeAdjustments(adjustments)).put("all", allPages)
        }
    }
    data class ResetPage(val pageId: String) : DocumentEdit {
        override val type = "resetPage"
        override fun encodeArgs(json: JSONObject) { json.put("page", pageId) }
    }
    data class MovePage(val pageId: String, val delta: Int) : DocumentEdit {
        override val type = "movePage"
        override fun encodeArgs(json: JSONObject) { json.put("page", pageId).put("delta", delta) }
    }
    data class DuplicatePage(val pageId: String) : DocumentEdit {
        override val type = "duplicatePage"
        override fun encodeArgs(json: JSONObject) { json.put("page", pageId) }
    }
    data class ExtractPage(val pageId: String) : DocumentEdit {
        override val type = "extractPage"
        override fun encodeArgs(json: JSONObject) { json.put("page", pageId) }
    }
    data class SplitBookSpread(val pageId: String, val fraction: Float) : DocumentEdit {
        override val type = "splitSpread"
        override fun encodeArgs(json: JSONObject) { json.put("page", pageId).put("fraction", fraction.toDouble()) }
    }
    data class DeletePage(val pageId: String) : DocumentEdit {
        override val type = "deletePage"
        override fun encodeArgs(json: JSONObject) { json.put("page", pageId) }
    }
    data object DuplicateDocument : DocumentEdit { override val type = "duplicateDocument" }
    data class AddTextAnnotation(val pageId: String, val text: String) : DocumentEdit {
        override val type = "textNote"
        override fun encodeArgs(json: JSONObject) { json.put("page", pageId).put("text", text) }
    }
    data class AddHighlight(val pageId: String) : DocumentEdit {
        override val type = "highlight"
        override fun encodeArgs(json: JSONObject) { json.put("page", pageId) }
    }
    data class AddRedaction(val pageId: String) : DocumentEdit {
        override val type = "redaction"
        override fun encodeArgs(json: JSONObject) { json.put("page", pageId) }
    }
    data class RemoveLastAnnotation(val pageId: String) : DocumentEdit {
        override val type = "removeAnnotation"
        override fun encodeArgs(json: JSONObject) { json.put("page", pageId) }
    }
    data class ReplaceAnnotations(val pageId: String, val annotations: List<PageAnnotation>) : DocumentEdit {
        override val type = "markup"
        override fun encodeArgs(json: JSONObject) { json.put("page", pageId).put("annotations", JsonCodec.encodeAnnotations(annotations)) }
    }
    data class AddWatermark(val style: WatermarkStyle) : DocumentEdit {
        override val type = "watermark"
        override fun encodeArgs(json: JSONObject) {
            json.put("text", style.text).put("size", style.size.name).put("position", style.position.name)
                .put("color", style.color).put("opacity", style.opacity.toDouble())
        }
    }
    data class MergeWith(val otherId: String) : DocumentEdit {
        override val type = "merge"
        override fun encodeArgs(json: JSONObject) { json.put("other", otherId) }
    }
    data class UpdatePageOcrText(val pageId: String, val text: String) : DocumentEdit {
        override val type = "ocrText"
        override fun encodeArgs(json: JSONObject) { json.put("page", pageId).put("text", text) }
    }
    data class ReOcr(val script: OcrScript?) : DocumentEdit {
        override val type = "reOcr"
        override fun encodeArgs(json: JSONObject) { script?.let { json.put("script", it.name) } }
    }
    data object SplitDocument : DocumentEdit { override val type = "splitDocument" }
    data class ReplaceText(val pageId: String, val edit: TextEdit) : DocumentEdit {
        override val type = "replaceText"
        override fun encodeArgs(json: JSONObject) {
            json.put("page", pageId).put("left", edit.left.toDouble()).put("top", edit.top.toDouble())
                .put("right", edit.right.toDouble()).put("bottom", edit.bottom.toDouble()).put("text", edit.text)
        }
    }
    data class Erase(val pageId: String, val strokesByBrush: Map<Float, List<List<NormalizedPoint>>>) : DocumentEdit {
        override val type = "erase"
        override fun encodeArgs(json: JSONObject) {
            json.put("page", pageId).put("brushes", JSONArray().apply {
                strokesByBrush.forEach { (brush, strokes) ->
                    put(JSONObject().put("brush", brush.toDouble()).put("strokes", JSONArray().apply {
                        strokes.forEach { stroke ->
                            put(JSONArray().apply { stroke.forEach { put(it.x.toDouble()); put(it.y.toDouble()) } })
                        }
                    }))
                }
            })
        }
    }

    companion object {
        private const val VERSION = 1

        /** The journal record for [edit] on [documentId]. */
        fun encode(documentId: String, edit: DocumentEdit): JSONObject =
            JSONObject().put("v", VERSION).put("document", documentId).put("type", edit.type)
                .put("args", JSONObject().also(edit::encodeArgs))

        /** Reads a journal record back; null when it is from an unknown version or can't be read. */
        fun decode(json: JSONObject): Pair<String, DocumentEdit>? = runCatching {
            if (json.optInt("v") != VERSION) return null
            val documentId = json.getString("document")
            val a = json.getJSONObject("args")
            fun page() = a.getString("page")
            fun float(key: String) = a.getDouble(key).toFloat()
            val edit: DocumentEdit = when (json.getString("type")) {
                "rename" -> Rename(a.getString("title"))
                "tags" -> SetTags(a.getString("tags"))
                "folder" -> SetFolder(a.getString("folder"))
                "favorite" -> ToggleFavorite
                "pdfQuality" -> SetPdfQuality(PdfQuality.valueOf(a.getString("value")))
                "pageSize" -> SetPdfPageSize(PdfPageSize.valueOf(a.getString("value")))
                "searchable" -> SetSearchablePdf(a.getBoolean("enabled"))
                "pageNumbers" -> SetPageNumbers(a.getBoolean("enabled"))
                "scanMode" -> SetScanMode(ScanMode.valueOf(a.getString("value")))
                "filter" -> ApplyFilter(page(), PageFilter.valueOf(a.getString("value")))
                "rotate" -> RotatePage(page())
                "crop" -> ApplyCrop(page(), JsonCodec.decodeCrop(a.getString("crop")))
                "adjust" -> ApplyAdjustments(page(), JsonCodec.decodeAdjustments(a.getString("adjustments")), a.getBoolean("all"))
                "resetPage" -> ResetPage(page())
                "movePage" -> MovePage(page(), a.getInt("delta"))
                "duplicatePage" -> DuplicatePage(page())
                "extractPage" -> ExtractPage(page())
                "splitSpread" -> SplitBookSpread(page(), float("fraction"))
                "deletePage" -> DeletePage(page())
                "duplicateDocument" -> DuplicateDocument
                "textNote" -> AddTextAnnotation(page(), a.getString("text"))
                "highlight" -> AddHighlight(page())
                "redaction" -> AddRedaction(page())
                "removeAnnotation" -> RemoveLastAnnotation(page())
                "markup" -> ReplaceAnnotations(page(), JsonCodec.decodeAnnotations(a.getString("annotations")))
                "watermark" -> AddWatermark(
                    WatermarkStyle(
                        text = a.getString("text"),
                        size = WatermarkSize.valueOf(a.getString("size")),
                        position = WatermarkPosition.valueOf(a.getString("position")),
                        color = a.getInt("color"),
                        opacity = float("opacity")
                    )
                )
                "merge" -> MergeWith(a.getString("other"))
                "ocrText" -> UpdatePageOcrText(page(), a.getString("text"))
                "reOcr" -> ReOcr(a.optString("script").takeIf { it.isNotEmpty() }?.let(OcrScript::valueOf))
                "splitDocument" -> SplitDocument
                "replaceText" -> ReplaceText(page(), TextEdit(float("left"), float("top"), float("right"), float("bottom"), a.getString("text")))
                "erase" -> {
                    val brushes = a.getJSONArray("brushes")
                    Erase(page(), (0 until brushes.length()).associate { i ->
                        val entry = brushes.getJSONObject(i)
                        val strokes = entry.getJSONArray("strokes")
                        entry.getDouble("brush").toFloat() to (0 until strokes.length()).map { s ->
                            val flat = strokes.getJSONArray(s)
                            (0 until flat.length() / 2).map { p ->
                                NormalizedPoint(flat.getDouble(2 * p).toFloat(), flat.getDouble(2 * p + 1).toFloat())
                            }
                        }
                    })
                }
                else -> return null
            }
            documentId to edit
        }.getOrNull()
    }
}
