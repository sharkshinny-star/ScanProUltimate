package com.example.scanpro.imaging

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

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
    suspend fun readFields(context: Context, inputUri: Uri): List<FormField> = withContext(Dispatchers.IO) {
        val input = PdfToolsEngine.copyUriToTemp(context, inputUri, "form_read_${UUID.randomUUID()}.pdf")
        try {
            PdfToolsEngine.loadPdf(input).use { document ->
                val form = document.documentCatalog.acroForm ?: return@use emptyList()
                val fields = form.fieldTree.mapNotNull(::toModel)
                if (fields.isEmpty() && form.xfa != null) {
                    throw IllegalArgumentException("This form is built with XFA, which isn't supported. Try opening it in Adobe Acrobat.")
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
    ): PdfToolResult = withContext(Dispatchers.IO) {
        val input = PdfToolsEngine.copyUriToTemp(context, inputUri, "form_fill_${UUID.randomUUID()}.pdf")
        try {
            PdfToolsEngine.loadPdf(input).use { document ->
                val form = document.documentCatalog.acroForm ?: throw IllegalArgumentException("This PDF has no fillable fields")
                var needAppearances = false
                values.forEach { (name, value) ->
                    val field = form.getField(name) ?: return@forEach
                    if (field.isReadOnly) return@forEach
                    if (!write(field, value)) needAppearances = true
                }
                if (flatten) {
                    // Flattening draws each field's appearance into the page, so every appearance must be current.
                    runCatching { form.refreshAppearances() }
                    try {
                        form.flatten()
                    } catch (error: Exception) {
                        throw IllegalStateException("The filled form couldn't be flattened. Save it without flattening instead.", error)
                    }
                } else if (needAppearances) {
                    // A value whose look couldn't be drawn here is drawn by the viewer that opens the file.
                    form.needAppearances = true
                }
                outputFile.parentFile?.mkdirs()
                document.save(outputFile)
                PdfToolResult(
                    outputFile = outputFile,
                    pageCount = document.numberOfPages,
                    originalSizeBytes = input.length(),
                    outputSizeBytes = outputFile.length(),
                    message = when {
                        values.isEmpty() -> "Form saved (no changes)"
                        flatten -> "Filled ${values.size} ${if (values.size == 1) "field" else "fields"} and flattened the form"
                        else -> "Filled ${values.size} ${if (values.size == 1) "field" else "fields"}"
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
                // Forms often store "Choice1" as the state and keep the readable text as the export value.
                val states = field.onValues.sorted()
                val exports = runCatching { field.exportValues }.getOrDefault(emptyList())
                val options = states.mapIndexed { i, state -> FormOption(state, exports.getOrNull(i)?.takeIf { it.isNotBlank() && it != state } ?: state.replace('_', ' ')) }
                FormField(name, label, FormFieldKind.RADIO, field.value.takeIf { it != "Off" }.orEmpty(), options, readOnly, required = required)
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
        if (field is PDTextField) field.cosObject.setString(COSName.V, value)
        false
    }
}
