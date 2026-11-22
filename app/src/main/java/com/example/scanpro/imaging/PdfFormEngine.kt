package com.example.scanpro.imaging

import com.example.scanpro.core.concurrent.BackgroundWork
import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDListBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import com.example.scanpro.R
import com.example.scanpro.core.text.userError
import com.example.scanpro.core.text.AppStrings

enum class FormFieldKind { TEXT, CHECKBOX, RADIO, CHOICE, MULTI_CHOICE }

data class FormOption(val value: String, val label: String)

/**
 * One fillable field. [value] is the text for a text field, the chosen option's value for a radio group or
 * single choice, the options joined by newlines for a multiple choice, and non-empty when a checkbox is ticked.
 */
data class FormField(
    val name: String,
    val label: String,
    val kind: FormFieldKind,
    val value: String,
    val options: List<FormOption> = emptyList(),
    val readOnly: Boolean = false,
    val multiline: Boolean = false,
    /** A combo box that also accepts typed text. */
    val editable: Boolean = false,
    val maxLength: Int = 0,
    /** The form marks this field as required; saving with it empty is refused. */
    val required: Boolean = false
)

/** The pure rules behind the form screen, kept free of PDFBox so they can be unit-tested. */
object FormFieldRules {
    const val CHECKED = "on"

    /** The tooltip name when the form has one, else the field's own name made readable. */
    fun label(alternateName: String?, partialName: String?, fullName: String): String {
        alternateName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val raw = partialName?.trim()?.takeIf { it.isNotEmpty() } ?: fullName.substringAfterLast('.')
        val words = raw
            .replace(Regex("\\[\\d+]$"), "")
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace('_', ' ')
            .replace('-', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        if (words.isEmpty()) return fullName
        return words.lowercase().replaceFirstChar { it.uppercase() }
    }

    fun isChecked(value: String) = value.isNotEmpty()

    /** Required fields that are still empty, in form order. */
    fun missingRequired(fields: List<FormField>, values: Map<String, String>): List<FormField> =
        fields.filter { it.required && !it.readOnly && (values[it.name] ?: it.value).isBlank() }

    fun multiValues(value: String): List<String> = value.split('\n').filter { it.isNotEmpty() }

    fun joinMulti(values: Collection<String>): String = values.joinToString("\n")

    /** Only fields the user changed are written back, so untouched fields keep their original appearance. */
    fun changes(original: List<FormField>, edited: Map<String, String>): Map<String, String> =
        original.asSequence()
            .filter { !it.readOnly }
            .mapNotNull { field -> edited[field.name]?.takeIf { it != field.value }?.let { field.name to clamp(field, it) } }
            .toMap()

    /** Keeps a value within what the field allows: its length limit, and only its own options for a choice. */
    fun clamp(field: FormField, value: String): String = when (field.kind) {
        FormFieldKind.TEXT -> if (field.maxLength > 0) value.take(field.maxLength) else value
        FormFieldKind.CHECKBOX -> if (isChecked(value)) CHECKED else ""
        FormFieldKind.RADIO -> value.takeIf { v -> v.isEmpty() || field.options.any { it.value == v } } ?: ""
        FormFieldKind.CHOICE -> if (field.editable || value.isEmpty() || field.options.any { it.value == value }) value else ""
        FormFieldKind.MULTI_CHOICE -> joinMulti(multiValues(value).filter { v -> field.options.any { it.value == v } })
    }
}

/** Reads and fills the interactive (AcroForm) fields of a PDF. */
object PdfFormEngine {

    /** Every fillable field, in the form's order. An empty list means the PDF has nothing to fill. */
    suspend fun readFields(context: Context, inputUri: Uri): List<FormField> = withContext(BackgroundWork.tools) {
        val input = PdfToolsEngine.copyUriToTemp(context, inputUri, "form_read_${UUID.randomUUID()}.pdf")
        try {
            PdfToolsEngine.loadPdf(input).use { document ->
                val form = document.documentCatalog.acroForm ?: return@use emptyList()
                // A malformed form can list two fields under one name; the screen keys its rows by name, so a second
                // row of the same name would crash it, and a value saved by name lands in the first one anyway.
                val fields = form.fieldTree.mapNotNull(::toModel).distinctBy { it.name }
                if (fields.isEmpty() && form.xfa != null) {
                    userError(R.string.err_form_xfa_unsupported)
                }
                fields
            }
        } finally {
            input.delete()
        }
    }

    /** Writes [values] (field name → value, as in [FormField.value]) into a copy of the PDF at [outputFile]. */
    suspend fun fill(
        context: Context,
        inputUri: Uri,
        values: Map<String, String>,
        flatten: Boolean,
        outputFile: File
    ): PdfToolResult = withContext(BackgroundWork.tools) {
        val input = PdfToolsEngine.copyUriToTemp(context, inputUri, "form_fill_${UUID.randomUUID()}.pdf")
        try {
            PdfToolsEngine.loadPdf(input).use { document ->
                val form = document.documentCatalog.acroForm ?: userError(R.string.err_form_no_fields)
                var needAppearances = false
                var written = 0
                values.forEach { (name, value) ->
                    val field = form.getField(name) ?: return@forEach
                    if (field.isReadOnly) return@forEach
                    if (!write(field, value)) needAppearances = true
                    written++
                }
                // A value the form's font cannot draw (Hindi typed into a Helvetica field) is kept for the viewer to
                // draw; flattening would bake in the old, empty look and lose it, so that is refused with the reason.
                if (flatten && needAppearances) userError(R.string.tools_form_err_flatten_font)
                if (flatten) {
                    // Flattening draws each field's appearance into the page, so every appearance must be current.
                    runCatching { form.refreshAppearances() }
                    try {
                        form.flatten()
                    } catch (error: Exception) {
                        userError(R.string.err_form_flatten_failed, cause = error)
                    }
                } else if (needAppearances) {
                    // A value whose look couldn't be drawn here is drawn by the viewer that opens the file.
                    form.needAppearances = true
                }
                outputFile.parentFile?.mkdirs()
                outputFile.deleteIfFails { document.save(outputFile) }
                PdfToolResult(
                    outputFile = outputFile,
                    pageCount = document.numberOfPages,
                    originalSizeBytes = input.length(),
                    outputSizeBytes = outputFile.length(),
                    message = when {
                        written == 0 && !flatten -> AppStrings.get(R.string.tools_form_saved_no_changes)
                        flatten -> AppStrings.plural(R.plurals.tools_form_filled_flattened, written, written)
                        else -> AppStrings.plural(R.plurals.tools_form_filled, written, written)
                    }
                )
            }
        } finally {
            input.delete()
        }
    }

    private fun toModel(field: PDField): FormField? {
        val name = field.fullyQualifiedName ?: return null
        val label = FormFieldRules.label(field.alternateFieldName, field.partialName, name)
        val readOnly = field.isReadOnly
        val required = field.isRequired
        return when (field) {
            is PDTextField -> FormField(
                name, label, FormFieldKind.TEXT, field.value.orEmpty(),
                readOnly = readOnly, multiline = field.isMultiline, maxLength = field.maxLen.coerceAtLeast(0), required = required
            )
            is PDCheckBox -> FormField(name, label, FormFieldKind.CHECKBOX, if (field.isChecked) FormFieldRules.CHECKED else "", readOnly = readOnly, required = required)
            is PDRadioButton -> {
                // With an /Opt array PDFBox already returns the readable export values here, in the form's own order;
                // otherwise the widgets' state names. Never sort them: the order is the form's, and a label must stay
                // with its own value (sorting once paired "Yes" with "No", so the other option was saved).
                val states = field.onValues.toList()
                val exports = runCatching { field.exportValues }.getOrDefault(emptyList())
                val options = states.map { state -> FormOption(state, state.replace('_', ' ')) }
                // With /Opt the stored state can be the option's index ("0", "1") rather than its export value.
                val raw = field.value.takeIf { it != "Off" }.orEmpty()
                val current = if (raw in states) raw else raw.toIntOrNull()?.let(exports::getOrNull)?.takeIf { it in states } ?: raw
                FormField(name, label, FormFieldKind.RADIO, current, options, readOnly, required = required)
            }
            is PDChoice -> {
                val exports = field.optionsExportValues
                val shown = field.optionsDisplayValues
                val options = exports.mapIndexed { i, value -> FormOption(value, shown.getOrNull(i)?.takeIf { it.isNotBlank() } ?: value) }
                val multi = field is PDListBox && field.isMultiSelect
                FormField(
                    name, label, if (multi) FormFieldKind.MULTI_CHOICE else FormFieldKind.CHOICE,
                    if (multi) FormFieldRules.joinMulti(field.value) else field.value.firstOrNull().orEmpty(),
                    options, readOnly, editable = field is PDComboBox && field.isEdit, required = required
                )
            }
            // Signature fields and push buttons can't be filled in with a value.
            else -> null
        }
    }

    /** Sets one value. Returns false when the value was stored but its look on the page couldn't be drawn. */
    private fun write(field: PDField, value: String): Boolean = when (field) {
        is PDCheckBox -> {
            if (FormFieldRules.isChecked(value)) field.check() else field.unCheck()
            true
        }
        is PDRadioButton -> {
            if (value.isEmpty()) {
                // Clears the group: no option is "on".
                field.cosObject.setName(COSName.V, "Off")
                field.widgets.forEach { it.setAppearanceState(COSName.Off.name) }
            } else {
                field.setValue(value)
            }
            true
        }
        is PDListBox -> withFallback(field, value) {
            if (field.isMultiSelect) field.setValue(FormFieldRules.multiValues(value)) else field.setValue(value)
        }
        is PDChoice -> withFallback(field, value) { field.setValue(value) }
        is PDTextField -> withFallback(field, value) { field.value = value }
        else -> true
    }

    /**
     * Drawing a value's look needs the form's font, and some forms name a font they don't include. PDFBox
     * stores the value before drawing it, so the value is kept (set again here for text) and the viewer that
     * opens the file draws it.
     */
    private inline fun withFallback(field: PDField, value: String, set: () -> Unit): Boolean = try {
        set()
        true
    } catch (error: java.io.IOException) {
        keepValue(field, value)
        false
    } catch (error: IllegalArgumentException) {
        // PDFBox reports a letter the field's font has no glyph for ("No glyph for U+0915") this way, not as an
        // IOException: the whole save used to fail for text in Hindi, Kannada, Malayalam, Tamil or Telugu (F211).
        keepValue(field, value)
        false
    }

    private fun keepValue(field: PDField, value: String) {
        when (field) {
            is PDTextField -> field.cosObject.setString(COSName.V, value)
            is PDComboBox -> if (value.isNotEmpty()) field.cosObject.setString(COSName.V, value)
        }
    }
}
