package com.example.scanpro.feature.tools

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.BrandingWatermark
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.TileTone

data class PdfToolCard(
    val id: String,
    /** Full tool name: read out by screen readers and shown in the "Use with a tool" menu. */
    @StringRes val title: Int,
    val icon: ImageVector,
    /** Colour family of the icon tile, so related tools share a hue. */
    val tone: TileTone = TileTone.Indigo,
    /** Short label under the tile; the full [title] when no shorter word fits. */
    @StringRes val label: Int = title,
    /** Shown with a "Beta" badge: works, but results vary with the PDF (e.g. table detection). */
    val beta: Boolean = false
)

internal class ToolSection(val id: String, @StringRes val title: Int, val tools: List<PdfToolCard>) {
    /**
     * What the landing shows as tiles. Tools in [SEARCH_ONLY_TOOL_IDS] stay in [tools] (search, pins, recents,
     * introductions, "Try again" and "Next tools" still find them) but get no tile of their own; the Convert section
     * gains the one [IMAGE_TOOLS_TILE] in place of the four image tools.
     */
    val tiles: List<PdfToolCard>
        get() = tools.filter { it.id !in SEARCH_ONLY_TOOL_IDS } + (if (id == "convert") listOf(IMAGE_TOOLS_TILE) else emptyList())
}

/**
 * Tools with no tile on the landing (owner's clean-up, 2026-11-16): Organise pages already rotates and extracts,
 * Repair is offered from the failure dialog when a PDF can't be read, and the four image tools sit behind one
 * "Image tools" tile. PDF to Excel is one of the formats of PDF to Word, Excel or text (F160). All of them are still
 * found by search; Rotate and Extract open Organise in that mode, with its Arrange/Rotate/Extract switch.
 */
internal val SEARCH_ONLY_TOOL_IDS = setOf("rotate", "extract", "repair", "pdf2excel", "crop_img", "compress_img", "convert_img", "resize_img")

/** The one tile that opens a chooser of the image tools (crop, compress, convert, resize). */
internal val IMAGE_TOOLS_TILE = PdfToolCard("image_tools", R.string.tools_card_image_tools, Icons.Default.PhotoLibrary, TileTone.Coral)

/** Tiles grouped the way the leading scanners do: what you do to a PDF, what you turn it into, how you lock it. */
internal val TOOL_SECTIONS = listOf(
    ToolSection(
        "edit", R.string.tools_section_edit, listOf(
            PdfToolCard("merge", R.string.tools_card_merge, Icons.Default.Merge, TileTone.Indigo),
            PdfToolCard("split", R.string.tools_card_split, Icons.AutoMirrored.Filled.CallSplit, TileTone.Indigo),
            PdfToolCard("organize", R.string.tools_card_organize, Icons.Default.Description, TileTone.Sky, label = R.string.tools_tile_organize),
            PdfToolCard("rotate", R.string.tools_card_rotate, Icons.AutoMirrored.Filled.RotateRight, TileTone.Sky, label = R.string.tools_tile_rotate),
            PdfToolCard("extract", R.string.tools_card_extract, Icons.AutoMirrored.Filled.DriveFileMove, TileTone.Indigo, label = R.string.tools_tile_extract),
            PdfToolCard("compress", R.string.tools_card_compress, Icons.Default.Compress, TileTone.Amber),
            PdfToolCard("repair", R.string.tools_card_repair, Icons.Default.Build, TileTone.Amber),
            PdfToolCard("blank_pages", R.string.tools_card_blank_pages, Icons.Default.DeleteSweep, TileTone.Sky, label = R.string.tools_tile_blank_pages),
            PdfToolCard("greyscale", R.string.tools_card_greyscale, Icons.Default.Contrast, TileTone.Slate, label = R.string.tools_tile_greyscale),
            PdfToolCard("invert_pdf", R.string.tools_card_invert_pdf, Icons.Default.InvertColors, TileTone.Slate, label = R.string.tools_tile_invert_pdf),
            PdfToolCard("flatten", R.string.tools_card_flatten, Icons.Default.Layers, TileTone.Violet, label = R.string.tools_tile_flatten),
            PdfToolCard("crop_pdf", R.string.tools_card_crop_pdf, Icons.Default.Crop, TileTone.Sky, label = R.string.tools_tile_crop_pdf),
            PdfToolCard("redact", R.string.tools_card_redact, Icons.Default.VisibilityOff, TileTone.Rose, label = R.string.tools_tile_redact),
            PdfToolCard("redact_box", R.string.tools_card_redact_box, Icons.Default.HighlightAlt, TileTone.Rose, label = R.string.tools_tile_redact_box),
            PdfToolCard("nup", R.string.tools_card_nup, Icons.Default.GridView, TileTone.Amber, label = R.string.tools_tile_nup),
            PdfToolCard("header_footer", R.string.tools_card_header_footer, Icons.Default.VerticalAlignTop, TileTone.Slate, label = R.string.tools_tile_header_footer),
            PdfToolCard("page_numbers", R.string.tools_card_page_numbers, Icons.Default.FormatListNumbered, TileTone.Sky, label = R.string.tools_tile_page_numbers),
            PdfToolCard("watermark", R.string.tools_card_watermark, Icons.AutoMirrored.Filled.BrandingWatermark, TileTone.Rose, label = R.string.tools_tile_watermark),
            PdfToolCard("sign", R.string.tools_card_sign, Icons.Default.Draw, TileTone.Violet)
        )
    ),
    ToolSection(
        "convert", R.string.tools_section_convert, listOf(
            PdfToolCard("img2pdf", R.string.tools_images_to_pdf, Icons.Default.Image, TileTone.Coral),
            PdfToolCard("office", R.string.tools_card_office, Icons.AutoMirrored.Filled.Article, TileTone.Sky, label = R.string.tools_tile_office),
            PdfToolCard("ocr", R.string.tools_card_ocr, Icons.Default.FindInPage, TileTone.Teal),
            PdfToolCard("fill", R.string.tools_card_fill, Icons.Default.EditNote, TileTone.Violet),
            PdfToolCard("pdf2img", R.string.tools_card_pdf2img, Icons.Default.PictureAsPdf, TileTone.Coral),
            PdfToolCard("pdf2text", R.string.tools_card_pdf2text, Icons.AutoMirrored.Filled.Article, TileTone.Teal, label = R.string.tools_tile_pdf2text),
            PdfToolCard("pdf2excel", R.string.tools_card_pdf2excel, Icons.Default.TableChart, TileTone.Teal, beta = true),
            PdfToolCard("extract_images", R.string.tools_card_extract_images, Icons.Default.Collections, TileTone.Coral)
        )
    ),
    ToolSection(
        "secure", R.string.tools_section_secure, listOf(
            PdfToolCard("protect", R.string.tools_card_protect, Icons.Default.Lock, TileTone.Rose),
            PdfToolCard("unlock", R.string.tools_card_unlock, Icons.Default.LockOpen, TileTone.Rose),
            PdfToolCard("pdf_info", R.string.tools_card_pdf_info, Icons.Default.Info, TileTone.Slate, label = R.string.tools_tile_pdf_info)
        )
    ),
    ToolSection(
        "image", R.string.tools_section_image, listOf(
            PdfToolCard("crop_img", R.string.tools_card_crop_img, Icons.Default.Crop, TileTone.Coral, label = R.string.tools_tile_crop_img),
            PdfToolCard("compress_img", R.string.tools_card_compress_img, Icons.Default.Compress, TileTone.Amber, label = R.string.tools_tile_compress_img),
            PdfToolCard("convert_img", R.string.tools_card_convert_img, Icons.Default.SyncAlt, TileTone.Teal, label = R.string.tools_tile_convert_img),
            PdfToolCard("resize_img", R.string.tools_card_resize_img, Icons.Default.AspectRatio, TileTone.Sky, label = R.string.tools_tile_resize_img)
        )
    )
)

internal val VIEWER_TOOL = PdfToolCard("viewer", R.string.tools_card_viewer, Icons.Default.Visibility, TileTone.Slate)

/** Every tool, for the "Use with a tool" menu on a recent PDF. */
internal val PDF_TOOLS = TOOL_SECTIONS.filter { it.id != "image" }.flatMap { it.tools } + VIEWER_TOOL

/** Every tile, the image tools included: what a failed run or a finished file can be sent back into. */
internal val ALL_TOOLS = TOOL_SECTIONS.flatMap { it.tools } + VIEWER_TOOL
