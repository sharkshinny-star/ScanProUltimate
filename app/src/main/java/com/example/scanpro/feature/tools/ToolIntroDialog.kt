package com.example.scanpro.feature.tools

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.HintInput
import com.example.scanpro.core.designsystem.IllustratedDialog
import com.example.scanpro.core.designsystem.ToolFlowIllustration

/** What a tool's first-time introduction says: what it does, what it starts from, and how that is picked. */
internal class ToolIntro(@StringRes val description: Int, val input: HintInput) {
    @get:StringRes
    val pickStep: Int
        get() = when (input) {
            HintInput.Pdf -> R.string.hint_tool_step_pick_pdf
            HintInput.Pdfs -> R.string.hint_tool_step_pick_pdfs
            HintInput.Image -> R.string.hint_tool_step_pick_image
            HintInput.Images -> R.string.hint_tool_step_pick_images
            HintInput.Office -> R.string.hint_tool_step_pick_office
        }
}

/** The introduction for each tile on the Tools screen, by tool id; null for anything without one (it opens at once). */
internal fun toolIntro(toolId: String): ToolIntro? = when (toolId) {
    "merge" -> ToolIntro(R.string.hint_tool_merge, HintInput.Pdfs)
    "split" -> ToolIntro(R.string.hint_tool_split, HintInput.Pdf)
    "organize" -> ToolIntro(R.string.hint_tool_organize, HintInput.Pdf)
    "rotate" -> ToolIntro(R.string.hint_tool_rotate, HintInput.Pdf)
    "extract" -> ToolIntro(R.string.hint_tool_extract, HintInput.Pdf)
    "compress" -> ToolIntro(R.string.hint_tool_compress, HintInput.Pdf)
    "repair" -> ToolIntro(R.string.hint_tool_repair, HintInput.Pdf)
    "page_numbers" -> ToolIntro(R.string.hint_tool_page_numbers, HintInput.Pdf)
    "watermark" -> ToolIntro(R.string.hint_tool_watermark, HintInput.Pdf)
    "sign" -> ToolIntro(R.string.hint_tool_sign, HintInput.Pdf)
    "img2pdf" -> ToolIntro(R.string.hint_tool_img2pdf, HintInput.Images)
    "office" -> ToolIntro(R.string.hint_tool_office, HintInput.Office)
    "ocr" -> ToolIntro(R.string.hint_tool_ocr, HintInput.Pdf)
    "fill" -> ToolIntro(R.string.hint_tool_fill, HintInput.Pdf)
    "pdf2img" -> ToolIntro(R.string.hint_tool_pdf2img, HintInput.Pdf)
    "pdf2text" -> ToolIntro(R.string.hint_tool_pdf2text, HintInput.Pdf)
    "pdf2excel" -> ToolIntro(R.string.hint_tool_pdf2excel, HintInput.Pdf)
    "pdf2ppt" -> ToolIntro(R.string.hint_tool_pdf2ppt, HintInput.Pdf)
    "protect" -> ToolIntro(R.string.hint_tool_protect, HintInput.Pdf)
    "unlock" -> ToolIntro(R.string.hint_tool_unlock, HintInput.Pdf)
    "crop_img" -> ToolIntro(R.string.hint_tool_crop_img, HintInput.Image)
    "compress_img" -> ToolIntro(R.string.hint_tool_compress_img, HintInput.Image)
    "convert_img" -> ToolIntro(R.string.hint_tool_convert_img, HintInput.Images)
    "resize_img" -> ToolIntro(R.string.hint_tool_resize_img, HintInput.Image)
    else -> null
}

/**
 * Shown the first time a tool is opened (F146), before its file picker: the tool at work in a little animation, what
 * it does and its three steps. Continue goes on to the picker; Cancel, Back or a tap outside close it.
 */
@Composable
internal fun ToolIntroDialog(tool: PdfToolCard, intro: ToolIntro, onContinue: () -> Unit, onCancel: () -> Unit) {
    IllustratedDialog(
        title = stringResource(tool.title),
        body = stringResource(intro.description),
        eyebrow = stringResource(R.string.hint_how_it_works),
        steps = listOf(
            stringResource(intro.pickStep),
            stringResource(R.string.hint_tool_step_options),
            stringResource(R.string.hint_tool_step_result, stringResource(R.string.tools_results_title))
        ),
        illustration = { ToolFlowIllustration(tool.icon, tool.tone, intro.input, Modifier.fillMaxSize()) },
        confirmText = stringResource(R.string.common_continue),
        onConfirm = onContinue,
        dismissText = stringResource(R.string.common_cancel),
        onDismiss = onCancel
    )
}
