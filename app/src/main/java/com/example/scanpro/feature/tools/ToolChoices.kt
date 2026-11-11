package com.example.scanpro.feature.tools

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.scanpro.R
import com.example.scanpro.data.OcrScript

/** Where the tools keep small choices between runs (the same file Compress PDF's last level lives in). */
internal const val TOOL_PREFS = "tools_ui"
private const val LAST_OCR_SCRIPT = "last_ocr_script"

/**
 * The text-recognition script to start with when the user has not picked one yet, from the app's language: Hindi
 * (and Marathi, Nepali) are written in Devanagari. ML Kit has no model for Tamil, Telugu, Kannada or Malayalam script,
 * so those, like English, start on Latin.
 */
internal fun defaultOcrScript(language: String): OcrScript = when (language.lowercase().substringBefore('-').substringBefore('_')) {
    "hi", "mr", "ne", "sa" -> OcrScript.DEVANAGARI
    "zh" -> OcrScript.CHINESE
    "ja" -> OcrScript.JAPANESE
    "ko" -> OcrScript.KOREAN
    else -> OcrScript.LATIN
}

/** The script chosen last time in Make searchable or PDF to text, or the app language's default. */
internal fun initialOcrScript(context: Context): OcrScript {
    val saved = context.getSharedPreferences(TOOL_PREFS, Context.MODE_PRIVATE).getString(LAST_OCR_SCRIPT, null)
    return OcrScript.entries.firstOrNull { it.name == saved }
        ?: defaultOcrScript(context.resources.configuration.locales[0]?.language.orEmpty())
}

internal fun rememberOcrScriptChoice(context: Context, script: OcrScript) {
    context.getSharedPreferences(TOOL_PREFS, Context.MODE_PRIVATE).edit().putString(LAST_OCR_SCRIPT, script.name).apply()
}

/** The script choice shared by Make searchable and PDF to text, as one card per script. */
@Composable
internal fun OcrScriptChoices(selected: OcrScript, onSelect: (OcrScript) -> Unit) {
    ChoiceGroup {
        OcrScript.entries.forEach { option ->
            ChoiceCard(
                selected = selected == option,
                title = if (option == OcrScript.LATIN) stringResource(R.string.tools_ocr_latin_label) else stringResource(option.labelRes),
                onClick = { onSelect(option) }
            )
        }
    }
}
