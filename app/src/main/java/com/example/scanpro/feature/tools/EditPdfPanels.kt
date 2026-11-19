package com.example.scanpro.feature.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.Subscript
import androidx.compose.material.icons.filled.Superscript
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scanpro.R
import com.example.scanpro.imaging.PdfTextEditor
import java.util.Locale

// ---- what the controls offer ----------------------------------------------------------------------------------

/** The colours offered for text, 0xRRGGBB. */
internal val THEME_COLOURS = listOf(
    0x000000, 0x1E2A4A, 0x64748B, 0xFFFFFF, 0x3344D6, 0x1E6FD9, 0x12A594, 0x1A9E5C,
    0xE5A823, 0xE5484D, 0xE8703A, 0x9B4DCA, 0xE0458B, 0x5B7BE8, 0x8DB33A, 0x2AA5DC
)

/** The marker colours offered behind text, 0xRRGGBB. */
internal val HIGHLIGHT_COLOURS = listOf(0xFFF176, 0xB9F0D0, 0xC9D8FF, 0xFFC9D2, 0xE3CFF7)

/** The fonts offered, in the order shown: the PDF's own, the four the app carries, then the standard PDF fonts. */
private val FAMILIES = listOf(
    PdfTextEditor.Family.ORIGINAL, PdfTextEditor.Family.ROBOTO, PdfTextEditor.Family.INTER, PdfTextEditor.Family.GELASIO,
    PdfTextEditor.Family.MONTSERRAT, PdfTextEditor.Family.SANS, PdfTextEditor.Family.SERIF, PdfTextEditor.Family.MONO
)

/** A font's name as shown: font names are proper names and are not translated. */
@Composable
private fun familyName(family: PdfTextEditor.Family): String = when (family) {
    PdfTextEditor.Family.ORIGINAL -> stringResource(R.string.tools_edit_font_same)
    PdfTextEditor.Family.SANS -> stringResource(R.string.tools_edit_font_sans)
    PdfTextEditor.Family.SERIF -> stringResource(R.string.tools_edit_font_serif)
    PdfTextEditor.Family.MONO -> stringResource(R.string.tools_edit_font_mono)
    PdfTextEditor.Family.ROBOTO -> "Roboto"
    PdfTextEditor.Family.INTER -> "Inter"
    PdfTextEditor.Family.GELASIO -> "Gelasio"
    PdfTextEditor.Family.MONTSERRAT -> "Montserrat"
}

private fun carriedFamily(regular: Int, bold: Int, italic: Int, boldItalic: Int) = FontFamily(
    androidx.compose.ui.text.font.Font(regular),
    androidx.compose.ui.text.font.Font(bold, FontWeight.Bold),
    androidx.compose.ui.text.font.Font(italic, FontWeight.Normal, FontStyle.Italic),
    androidx.compose.ui.text.font.Font(boldItalic, FontWeight.Bold, FontStyle.Italic)
)

private val RobotoFamily = carriedFamily(R.font.roboto_regular, R.font.roboto_bold, R.font.roboto_italic, R.font.roboto_bold_italic)
private val InterFamily = carriedFamily(R.font.inter_regular, R.font.inter_bold, R.font.inter_italic, R.font.inter_bold_italic)
private val GelasioFamily = carriedFamily(R.font.gelasio_regular, R.font.gelasio_bold, R.font.gelasio_italic, R.font.gelasio_bold_italic)
private val MontserratFamily = carriedFamily(R.font.montserrat_regular, R.font.montserrat_bold, R.font.montserrat_italic, R.font.montserrat_bold_italic)

/** The screen's font for a sample of [family]: the very file that goes into the PDF for the fonts the app carries. */
private fun fontOf(family: PdfTextEditor.Family): FontFamily = when (family) {
    PdfTextEditor.Family.SERIF -> FontFamily.Serif
    PdfTextEditor.Family.MONO -> FontFamily.Monospace
    PdfTextEditor.Family.SANS -> FontFamily.SansSerif
    PdfTextEditor.Family.ORIGINAL -> FontFamily.Default
    PdfTextEditor.Family.ROBOTO -> RobotoFamily
    PdfTextEditor.Family.INTER -> InterFamily
    PdfTextEditor.Family.GELASIO -> GelasioFamily
    PdfTextEditor.Family.MONTSERRAT -> MontserratFamily
}

private fun number(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else String.format(Locale.getDefault(), "%.1f", value)

/** Sizes step by one point, or half a point for small print. */
private fun stepSize(size: Float, up: Boolean): Float {
    val step = if (size < 12f || (size == 12f && !up)) 0.5f else 1f
    return (size + if (up) step else -step).coerceIn(4f, 144f)
}

/** How a sample of [edit] looks, as near as the screen's fonts come to it. */
private fun sampleStyle(edit: PdfTextEditor.Edit, originalColour: Int, size: Float = 20f) = TextStyle(
    fontFamily = fontOf(edit.family),
    fontWeight = if (edit.bold) FontWeight.Bold else FontWeight.Normal,
    fontStyle = if (edit.italic) FontStyle.Italic else FontStyle.Normal,
    fontSize = size.sp,
    letterSpacing = edit.letterSpacing.sp,
    color = Color(0xFF000000.toInt() or (edit.colour ?: originalColour)),
    background = edit.highlight?.let { Color(0xFF000000.toInt() or it) } ?: Color.Unspecified,
    textDecoration = TextDecoration.combine(
        listOfNotNull(TextDecoration.Underline.takeIf { edit.underline }, TextDecoration.LineThrough.takeIf { edit.strike })
    )
)

/** [text] with a capital only at the start of each sentence. */
internal fun sentenceCase(text: String): String {
    val out = StringBuilder(text.lowercase())
    var start = true
    for (i in out.indices) {
        val c = out[i]
        if (start && c.isLetter()) { out[i] = c.uppercaseChar(); start = false }
        if (c == '.' || c == '!' || c == '?' || c == '\n') start = true
    }
    return out.toString()
}

// ---- the panel at the bottom ----------------------------------------------------------------------------------

/**
 * The formatting panel under the page: Home (font, size, bold to strike-through, colour, alignment, wrapping), Insert,
 * Layout and Review. Its controls change the selected line at once; with nothing selected they are dimmed.
 */
@Composable
internal fun FormatPanel(state: EditorState, calls: EditorCalls, initialTab: Int = 0) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    val edit = state.current
    Surface(
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 10.dp)) {
            Box(Modifier.padding(top = 8.dp, bottom = 6.dp).size(40.dp, 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant).align(Alignment.CenterHorizontally))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tools_edit_panel_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (edit != null) Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        "Aa " + stringResource(R.string.tools_edit_active), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                listOf(R.string.tools_edit_tab_home, R.string.tools_edit_tab_insert, R.string.tools_edit_tab_layout, R.string.tools_edit_tab_review).forEachIndexed { index, label ->
                    val chosen = tab == index
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Tab) { tab = index }.padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(label), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                            color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(2.dp).background(if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent))
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Column(Modifier.fillMaxWidth().heightIn(min = 148.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (tab) {
                    0 -> HomeTab(state, calls)
                    1 -> InsertTab(state, calls)
                    2 -> LayoutTab(state, calls)
                    else -> ReviewTab(state, calls)
                }
            }
        }
    }
}

@Composable
private fun HomeTab(state: EditorState, calls: EditorCalls) {
    val edit = state.current
    val on = edit != null
    val shown = edit ?: PdfTextEditor.Edit(0, 0, "", bold = false, italic = false, size = 12f)
    Column(Modifier.alpha(if (on) 1f else 0.42f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Outlined(Modifier.weight(1.5f).height(46.dp).clickable(enabled = on) { calls.onSheet(EditorSheet.FONT) }) {
                Text(
                    familyName(shown.family), fontFamily = fontOf(shown.family), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 14.dp)
                )
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Stepper(
                number(shown.size), enabled = on, modifier = Modifier.weight(1f).height(46.dp),
                minusLabel = stringResource(R.string.tools_edit_smaller), plusLabel = stringResource(R.string.tools_edit_larger),
                onMinus = { calls.onChange { it.copy(size = stepSize(it.size, up = false)) } },
                onPlus = { calls.onChange { it.copy(size = stepSize(it.size, up = true)) } }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            Square(Icons.Default.FormatBold, stringResource(R.string.tools_edit_bold), shown.bold, on) { calls.onChange { it.copy(bold = !it.bold) } }
            Square(Icons.Default.FormatItalic, stringResource(R.string.tools_edit_italic), shown.italic, on) { calls.onChange { it.copy(italic = !it.italic) } }
            Square(Icons.Default.FormatUnderlined, stringResource(R.string.tools_edit_underline), shown.underline, on) { calls.onChange { it.copy(underline = !it.underline) } }
            Square(Icons.Default.StrikethroughS, stringResource(R.string.tools_edit_strike), shown.strike, on) { calls.onChange { it.copy(strike = !it.strike) } }
            // The letter A over a bar of the text's colour.
            val colourLabel = stringResource(R.string.tools_edit_colour)
            Box(
                Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    .clickable(enabled = on, role = Role.Button) { calls.onSheet(EditorSheet.COLOUR) }
                    .semantics { contentDescription = colourLabel },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("A", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Box(Modifier.size(18.dp, 3.dp).clip(CircleShape).background(Color(0xFF000000.toInt() or (shown.colour ?: state.originalColour))))
                }
            }
            Box(Modifier.padding(horizontal = 2.dp).size(1.dp, 24.dp).background(MaterialTheme.colorScheme.outlineVariant))
            listOf(
                Triple(PdfTextEditor.Align.LEFT, Icons.AutoMirrored.Filled.FormatAlignLeft, R.string.tools_edit_align_left),
                Triple(PdfTextEditor.Align.CENTRE, Icons.Default.FormatAlignCenter, R.string.tools_edit_align_centre),
                Triple(PdfTextEditor.Align.RIGHT, Icons.AutoMirrored.Filled.FormatAlignRight, R.string.tools_edit_align_right),
                Triple(PdfTextEditor.Align.JUSTIFY, Icons.Default.FormatAlignJustify, R.string.tools_edit_align_justify)
            ).forEach { (align, icon, label) ->
                Square(icon, stringResource(label), shown.align == align, on, bordered = false) {
                    // Justify only shows on text that runs over several lines, so it turns wrapping on.
                    calls.onChange { it.copy(align = align, wrap = it.wrap || align == PdfTextEditor.Align.JUSTIFY) }
                }
            }
        }
    }
    if (!on) {
        Text(stringResource(R.string.tools_edit_select_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Segment(Icons.AutoMirrored.Filled.WrapText, stringResource(R.string.tools_edit_wrapping), Modifier.weight(1f)) { calls.onSheet(EditorSheet.PARAGRAPH) }
            Outlined(Modifier.padding(vertical = 6.dp).height(32.dp).clickable { calls.onChange { it.copy(wrap = !it.wrap) } }) {
                Text(
                    stringResource(if (shown.wrap) R.string.tools_edit_wrap_auto else R.string.common_off), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
            // Styles and colours, and the finer typography: icons, to leave the row to the wrapping control.
            IconButton(onClick = { calls.onSheet(EditorSheet.STYLES) }) { Icon(Icons.Default.Palette, contentDescription = stringResource(R.string.tools_edit_styles), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
            IconButton(onClick = { calls.onSheet(EditorSheet.TYPOGRAPHY) }) { Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.tools_edit_sheet_typo), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun InsertTab(state: EditorState, calls: EditorCalls) {
    val on = state.current != null
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile(stringResource(R.string.tools_edit_add_text), Icons.Default.TextFields, selected = state.adding, modifier = Modifier.weight(1f), onClick = calls.onAddText)
        Tile(stringResource(R.string.tools_edit_duplicate), Icons.Default.ContentCopy, selected = false, enabled = on, modifier = Modifier.weight(1f), onClick = calls.onDuplicate)
        Tile(stringResource(R.string.tools_edit_list_style), Icons.AutoMirrored.Filled.FormatListBulleted, selected = false, enabled = on, modifier = Modifier.weight(1f)) { calls.onSheet(EditorSheet.LISTS) }
    }
    Text(
        stringResource(if (state.adding) R.string.tools_edit_add_hint else R.string.tools_edit_select_hint),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun LayoutTab(state: EditorState, calls: EditorCalls) {
    val on = state.current != null
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile(stringResource(R.string.tools_edit_sheet_para), Icons.AutoMirrored.Filled.WrapText, selected = false, enabled = on, modifier = Modifier.weight(1f)) { calls.onSheet(EditorSheet.PARAGRAPH) }
        Tile(stringResource(R.string.tools_edit_sheet_lists), Icons.Default.FormatLineSpacing, selected = false, enabled = on, modifier = Modifier.weight(1f)) { calls.onSheet(EditorSheet.LISTS) }
        Tile(stringResource(R.string.tools_edit_sheet_position), Icons.Default.OpenWith, selected = false, enabled = on, modifier = Modifier.weight(1f)) { calls.onSheet(EditorSheet.POSITION) }
    }
    Row(Modifier.alpha(if (on) 1f else 0.42f), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.tools_edit_move), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        MoveArrows(enabled = on) { dx, dy -> calls.onChange { it.copy(dx = it.dx + dx, dy = it.dy + dy) } }
    }
}

@Composable
private fun ReviewTab(state: EditorState, calls: EditorCalls) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile(stringResource(R.string.tools_edit_find_replace), Icons.Default.FindReplace, selected = false, modifier = Modifier.weight(1f)) { calls.onSheet(EditorSheet.FIND) }
        Tile(
            pluralStringResource(R.plurals.tools_edit_changes, state.edits.size, state.edits.size), Icons.Default.History, selected = false,
            enabled = state.edits.isNotEmpty(), modifier = Modifier.weight(1f)
        ) { calls.onSheet(EditorSheet.CHANGES) }
        Tile(stringResource(R.string.tools_edit_save), Icons.Default.Save, selected = false, enabled = state.edits.isNotEmpty(), modifier = Modifier.weight(1f)) { calls.onSheet(EditorSheet.SAVE) }
    }
    Text(stringResource(R.string.tools_edit_save_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---- the sheets -----------------------------------------------------------------------------------------------

/** The sheet [state] has open, over a dimmed editor; a tap on the dimmed part closes it. */
@Composable
internal fun EditorSheets(state: EditorState, calls: EditorCalls) {
    val sheet = state.sheet ?: return
    val close = { calls.onSheet(null) }
    val edit = state.current
    // A sheet that changes the selection has nothing to show without one.
    if (edit == null && sheet !in setOf(EditorSheet.FIND, EditorSheet.CHANGES, EditorSheet.SAVE)) {
        LaunchedEffect(sheet) { close() }
        return
    }
    val apply: (PdfTextEditor.Edit) -> Unit = { draft -> calls.onChange { draft }; close() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close)
        )
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 24.dp,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max = maxHeight * 0.8f).imePadding()
                // Taps on the sheet itself must not fall through to the dimmed part and close it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
        ) {
            when (sheet) {
                EditorSheet.TEXT -> TextSheet(edit!!, close, calls.onSetText)
                EditorSheet.FONT -> FontSheet(edit!!, state.originalColour, close, apply)
                EditorSheet.PARAGRAPH -> ParagraphSheet(edit!!, close, apply)
                EditorSheet.COLOUR -> ColourSheet(edit!!, state.originalColour, close, apply)
                EditorSheet.TYPOGRAPHY -> TypographySheet(edit!!, state.originalColour, close, apply)
                EditorSheet.LISTS -> ListsSheet(edit!!, close, apply)
                EditorSheet.POSITION -> PositionSheet(edit!!, state.selectedBox, close, apply)
                EditorSheet.ACTIONS -> ActionsSheet(close, calls)
                EditorSheet.STYLES -> StylesSheet(edit!!, state.originalColour, close, apply)
                EditorSheet.FIND -> FindSheet(close, calls.onReplaceAll)
                EditorSheet.CHANGES -> ChangesSheet(state, close, calls)
                EditorSheet.SAVE -> SaveSheet(state, close, calls.onSave)
            }
        }
    }
}

/** A sheet's frame: handle, title with a line under it, a close button, the controls, and one wide button. */
@Composable
private fun SheetFrame(
    title: String, subtitle: String?, onClose: () -> Unit, action: String?, onAction: () -> Unit,
    actionEnabled: Boolean = true, content: @Composable () -> Unit
) {
    Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 14.dp)) {
        Box(Modifier.padding(top = 8.dp, bottom = 8.dp).size(40.dp, 4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant).align(Alignment.CenterHorizontally))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onClose, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close), modifier = Modifier.size(18.dp))
            }
        }
        Column(
            Modifier.weight(1f, fill = false).padding(top = 12.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) { content() }
        if (action != null) {
            Button(onClick = onAction, enabled = actionEnabled, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(52.dp)) {
                Text(action, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** The words of the selection (or of new text): typed in a field, with line breaks where Enter is pressed. */
@Composable
private fun TextSheet(edit: PdfTextEditor.Edit, onClose: () -> Unit, onSetText: (String) -> Unit) {
    var text by rememberSaveable(edit.page, edit.index, edit.id) { mutableStateOf(edit.text) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    SheetFrame(
        stringResource(if (edit.isNew && edit.text.isEmpty()) R.string.tools_edit_new_title else R.string.tools_edit_dialog_title), null, onClose,
        action = stringResource(R.string.common_apply), onAction = { onSetText(text) },
        actionEnabled = if (edit.isNew) text.isNotBlank() else text != edit.text || text.isNotEmpty()
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(2000) },
            label = { Text(stringResource(R.string.tools_edit_text_label)) },
            minLines = 2,
            maxLines = 6,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )
    }
}

/** Screen 02: the font family (each shown in its own face), the size and the style. */
@Composable
private fun FontSheet(current: PdfTextEditor.Edit, originalColour: Int, onClose: () -> Unit, onApply: (PdfTextEditor.Edit) -> Unit) {
    var draft by remember(current) { mutableStateOf(current) }
    SheetFrame(stringResource(R.string.tools_edit_sheet_font), stringResource(R.string.tools_edit_sheet_font_sub), onClose, stringResource(R.string.tools_edit_apply_font), { onApply(draft) }) {
        Label(stringResource(R.string.tools_edit_font))
        Outlined(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth()) {
                FAMILIES.forEach { family ->
                    val chosen = draft.family == family
                    Row(
                        Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { draft = draft.copy(family = family) }
                            .background(if (chosen) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f) else Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            familyName(family), fontFamily = fontOf(family), fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f)
                        )
                        Text("Aa " + stringResource(R.string.tools_edit_preview), fontFamily = fontOf(family), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent)
                    }
                }
            }
        }
        Label(stringResource(R.string.tools_edit_size))
        Stepper(
            stringResource(R.string.tools_edit_size_value, number(draft.size)), modifier = Modifier.fillMaxWidth().height(46.dp),
            minusLabel = stringResource(R.string.tools_edit_smaller), plusLabel = stringResource(R.string.tools_edit_larger),
            onMinus = { draft = draft.copy(size = stepSize(draft.size, up = false)) }, onPlus = { draft = draft.copy(size = stepSize(draft.size, up = true)) }
        )
        Label(stringResource(R.string.tools_edit_style))
        StyleTiles(draft) { draft = it }
        Sample(draft, originalColour)
    }
}

/** Screen 03: how the text is lined up and whether long lines wrap, with the room around its paragraphs. */
@Composable
private fun ParagraphSheet(current: PdfTextEditor.Edit, onClose: () -> Unit, onApply: (PdfTextEditor.Edit) -> Unit) {
    var draft by remember(current) { mutableStateOf(current) }
    SheetFrame(stringResource(R.string.tools_edit_sheet_para), stringResource(R.string.tools_edit_sheet_para_sub), onClose, stringResource(R.string.tools_edit_apply_layout), { onApply(draft) }) {
        Label(stringResource(R.string.tools_edit_alignment))
        AlignTiles(draft) { draft = it }
        Label(stringResource(R.string.tools_edit_wrapping))
        WrapSwitches(draft) { draft = it }
        Label(stringResource(R.string.tools_edit_para_spacing))
        SpacingSteppers(draft) { draft = it }
    }
}

/** Screen 04: the text's colour, from the theme's or typed as a hex code, with a sample. */
@Composable
private fun ColourSheet(current: PdfTextEditor.Edit, originalColour: Int, onClose: () -> Unit, onApply: (PdfTextEditor.Edit) -> Unit) {
    var draft by remember(current) { mutableStateOf(current) }
    SheetFrame(stringResource(R.string.tools_edit_sheet_colour), stringResource(R.string.tools_edit_sheet_colour_sub), onClose, stringResource(R.string.tools_edit_apply_colour), { onApply(draft) }) {
        Sample(draft, originalColour)
        Label(stringResource(R.string.tools_edit_theme_colours))
        ColourGrid(draft.colour, originalColour) { draft = draft.copy(colour = it) }
        Label(stringResource(R.string.tools_edit_custom_colour))
        HexField(draft.colour ?: originalColour) { draft = draft.copy(colour = it) }
    }
}

/** Screen 05: the finer points: style, room between letters, highlight, small raised or lowered text, capitals. */
@Composable
private fun TypographySheet(current: PdfTextEditor.Edit, originalColour: Int, onClose: () -> Unit, onApply: (PdfTextEditor.Edit) -> Unit) {
    var draft by remember(current) { mutableStateOf(current) }
    SheetFrame(stringResource(R.string.tools_edit_sheet_typo), stringResource(R.string.tools_edit_sheet_typo_sub), onClose, stringResource(R.string.tools_edit_apply_typo), { onApply(draft) }) {
        Sample(draft, originalColour)
        Label(stringResource(R.string.tools_edit_font) + " · " + stringResource(R.string.tools_edit_size))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Outlined(Modifier.weight(1f).height(46.dp).clickable {
                draft = draft.copy(family = FAMILIES[(FAMILIES.indexOf(draft.family) + 1) % FAMILIES.size])
            }) {
                Text(familyName(draft.family), fontFamily = fontOf(draft.family), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 14.dp))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Stepper(
                stringResource(R.string.tools_edit_size_value, number(draft.size)), modifier = Modifier.weight(1f).height(46.dp),
                minusLabel = stringResource(R.string.tools_edit_smaller), plusLabel = stringResource(R.string.tools_edit_larger),
                onMinus = { draft = draft.copy(size = stepSize(draft.size, up = false)) }, onPlus = { draft = draft.copy(size = stepSize(draft.size, up = true)) }
            )
        }
        Label(stringResource(R.string.tools_edit_style))
        StyleTiles(draft) { draft = it }
        Label(stringResource(R.string.tools_edit_char_spacing))
        Stepper(
            stringResource(R.string.tools_edit_size_value, number(draft.letterSpacing)), modifier = Modifier.fillMaxWidth().height(46.dp),
            onMinus = { draft = draft.copy(letterSpacing = (draft.letterSpacing - 0.5f).coerceAtLeast(-2f)) },
            onPlus = { draft = draft.copy(letterSpacing = (draft.letterSpacing + 0.5f).coerceAtMost(12f)) }
        )
        Label(stringResource(R.string.tools_edit_effects))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(stringResource(R.string.tools_edit_highlight), Icons.Default.Highlight, draft.highlight != null, Modifier.weight(1f)) {
                draft = draft.copy(highlight = if (draft.highlight == null) HIGHLIGHT_COLOURS.first() else null)
            }
            Tile(stringResource(R.string.tools_edit_subscript), Icons.Default.Subscript, draft.script == PdfTextEditor.Script.SUB, Modifier.weight(1f)) {
                draft = draft.copy(script = if (draft.script == PdfTextEditor.Script.SUB) PdfTextEditor.Script.NONE else PdfTextEditor.Script.SUB)
            }
            Tile(stringResource(R.string.tools_edit_superscript), Icons.Default.Superscript, draft.script == PdfTextEditor.Script.SUPER, Modifier.weight(1f)) {
                draft = draft.copy(script = if (draft.script == PdfTextEditor.Script.SUPER) PdfTextEditor.Script.NONE else PdfTextEditor.Script.SUPER)
            }
        }
        Label(stringResource(R.string.tools_edit_case))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(stringResource(R.string.tools_edit_case_sentence), null, false, Modifier.weight(1f), glyph = "Aa") { draft = draft.copy(text = sentenceCase(draft.text)) }
            Tile(stringResource(R.string.tools_edit_case_upper), null, false, Modifier.weight(1f), glyph = "AA") { draft = draft.copy(text = draft.text.uppercase()) }
            Tile(stringResource(R.string.tools_edit_case_lower), null, false, Modifier.weight(1f), glyph = "aa") { draft = draft.copy(text = draft.text.lowercase()) }
        }
    }
}

/** Screen 06: alignment, list markers, the room between lines, indents and the room between paragraphs. */
@Composable
private fun ListsSheet(current: PdfTextEditor.Edit, onClose: () -> Unit, onApply: (PdfTextEditor.Edit) -> Unit) {
    var draft by remember(current) { mutableStateOf(current) }
    SheetFrame(stringResource(R.string.tools_edit_sheet_lists), stringResource(R.string.tools_edit_sheet_lists_sub), onClose, stringResource(R.string.tools_edit_apply_para), { onApply(draft) }) {
        Label(stringResource(R.string.tools_edit_alignment))
        AlignTiles(draft) { draft = it }
        Label(stringResource(R.string.tools_edit_list_style))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(PdfTextEditor.ListStyle.BULLET, Icons.AutoMirrored.Filled.FormatListBulleted, R.string.tools_edit_bullets),
                Triple(PdfTextEditor.ListStyle.NUMBER, Icons.Default.FormatListNumbered, R.string.tools_edit_numbered),
                Triple(PdfTextEditor.ListStyle.CHECK, Icons.Default.Checklist, R.string.tools_edit_checklist)
            ).forEach { (style, icon, label) ->
                // A second tap on the chosen marker takes it off again.
                Tile(stringResource(label), icon, draft.list == style, Modifier.weight(1f)) {
                    draft = draft.copy(list = if (draft.list == style) PdfTextEditor.ListStyle.NONE else style)
                }
            }
        }
        Label(stringResource(R.string.tools_edit_line_spacing))
        Stepper(
            stringResource(R.string.tools_edit_times, number(draft.lineSpacing)), modifier = Modifier.fillMaxWidth().height(46.dp), lead = stringResource(R.string.tools_edit_line_height),
            onMinus = { draft = draft.copy(lineSpacing = (draft.lineSpacing - 0.1f).coerceAtLeast(0.8f).let { Math.round(it * 10f) / 10f }) },
            onPlus = { draft = draft.copy(lineSpacing = (draft.lineSpacing + 0.1f).coerceAtMost(3f).let { Math.round(it * 10f) / 10f }) }
        )
        Label(stringResource(R.string.tools_edit_indentation))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stepper(
                stringResource(R.string.tools_edit_size_value, number(draft.indentLeft)), modifier = Modifier.weight(1f).height(54.dp), lead = stringResource(R.string.tools_edit_indent_left), stacked = true,
                onMinus = { draft = draft.copy(indentLeft = (draft.indentLeft - 6f).coerceAtLeast(0f)) }, onPlus = { draft = draft.copy(indentLeft = (draft.indentLeft + 6f).coerceAtMost(144f)) }
            )
            Stepper(
                stringResource(R.string.tools_edit_size_value, number(draft.indentRight)), modifier = Modifier.weight(1f).height(54.dp), lead = stringResource(R.string.tools_edit_indent_right), stacked = true,
                onMinus = { draft = draft.copy(indentRight = (draft.indentRight - 6f).coerceAtLeast(0f)) }, onPlus = { draft = draft.copy(indentRight = (draft.indentRight + 6f).coerceAtMost(144f)) }
            )
        }
        Label(stringResource(R.string.tools_edit_para_spacing))
        SpacingSteppers(draft) { draft = it }
    }
}

/** Screen 07: how the text flows (wrapping, and whether the lines under it move) and exactly where it sits. */
@Composable
private fun PositionSheet(current: PdfTextEditor.Edit, box: PageBox?, onClose: () -> Unit, onApply: (PdfTextEditor.Edit) -> Unit) {
    var draft by remember(current) { mutableStateOf(current) }
    SheetFrame(stringResource(R.string.tools_edit_sheet_position), stringResource(R.string.tools_edit_sheet_position_sub), onClose, stringResource(R.string.tools_edit_apply_wrap), { onApply(draft) }) {
        Label(stringResource(R.string.tools_edit_wrapping))
        WrapSwitches(draft) { draft = it }
        Label(stringResource(R.string.tools_edit_precise))
        // Where the box's top-left corner is on the page, in points; the steppers move it a point at a time.
        val x = (box?.left ?: draft.x) + (draft.dx - current.dx)
        val y = (box?.top ?: draft.y) + (draft.dy - current.dy)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stepper(
                stringResource(R.string.tools_edit_size_value, number(Math.round(x * 10f) / 10f)), modifier = Modifier.weight(1f).height(54.dp), lead = stringResource(R.string.tools_edit_pos_x), stacked = true,
                minusLabel = stringResource(R.string.tools_edit_move_left), plusLabel = stringResource(R.string.tools_edit_move_right),
                onMinus = { draft = draft.copy(dx = draft.dx - 1f) }, onPlus = { draft = draft.copy(dx = draft.dx + 1f) }
            )
            Stepper(
                stringResource(R.string.tools_edit_size_value, number(Math.round(y * 10f) / 10f)), modifier = Modifier.weight(1f).height(54.dp), lead = stringResource(R.string.tools_edit_pos_y), stacked = true,
                minusLabel = stringResource(R.string.tools_edit_move_up), plusLabel = stringResource(R.string.tools_edit_move_down),
                onMinus = { draft = draft.copy(dy = draft.dy - 1f) }, onPlus = { draft = draft.copy(dy = draft.dy + 1f) }
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tools_edit_move), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            MoveArrows(enabled = true, step = 5f) { dx, dy -> draft = draft.copy(dx = draft.dx + dx, dy = draft.dy + dy) }
        }
    }
}

/** Screen 08: everything that can be done with the selection. */
@Composable
private fun ActionsSheet(onClose: () -> Unit, calls: EditorCalls) {
    SheetFrame(stringResource(R.string.tools_edit_sheet_actions), stringResource(R.string.tools_edit_sheet_actions_sub), onClose, stringResource(R.string.tools_edit_edit_selected), { calls.onSheet(EditorSheet.TEXT) }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(stringResource(R.string.tools_edit_dialog_title), Icons.Default.Edit, true, Modifier.weight(1f)) { calls.onSheet(EditorSheet.TEXT) }
            Tile(stringResource(R.string.tools_edit_format), Icons.Default.TextFormat, false, Modifier.weight(1f)) { calls.onSheet(EditorSheet.FONT) }
            Tile(stringResource(R.string.tools_edit_colour), Icons.Default.FormatColorText, false, Modifier.weight(1f)) { calls.onSheet(EditorSheet.COLOUR) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile(stringResource(R.string.tools_edit_duplicate), Icons.Default.ContentCopy, false, Modifier.weight(1f)) { calls.onDuplicate(); onClose() }
            Tile(stringResource(R.string.tools_edit_move), Icons.Default.OpenWith, false, Modifier.weight(1f)) { calls.onSheet(EditorSheet.POSITION) }
            Tile(stringResource(R.string.tools_edit_delete), Icons.Default.Delete, false, Modifier.weight(1f), tint = MaterialTheme.colorScheme.error) { calls.onDelete(); onClose() }
        }
    }
}

/** Screen 09: ready-made styles, colours for the words and for a marker behind them. */
@Composable
private fun StylesSheet(current: PdfTextEditor.Edit, originalColour: Int, onClose: () -> Unit, onApply: (PdfTextEditor.Edit) -> Unit) {
    var draft by remember(current) { mutableStateOf(current) }
    SheetFrame(stringResource(R.string.tools_edit_sheet_styles), stringResource(R.string.tools_edit_sheet_styles_sub), onClose, stringResource(R.string.tools_edit_apply_style), { onApply(draft) }) {
        Label(stringResource(R.string.tools_edit_quick_styles))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple("Aa", R.string.tools_edit_style_body, 11f), Triple("H1", R.string.tools_edit_style_h1, 20f), Triple("H2", R.string.tools_edit_style_h2, 15f)).forEach { (glyph, label, size) ->
                val heading = glyph != "Aa"
                Tile(stringResource(label), null, draft.size == size && draft.bold == heading, Modifier.weight(1f), glyph = glyph) {
                    draft = draft.copy(size = size, bold = heading, italic = false)
                }
            }
        }
        Label(stringResource(R.string.tools_edit_theme_colours))
        ColourGrid(draft.colour, originalColour) { draft = draft.copy(colour = it) }
        Label(stringResource(R.string.tools_edit_highlight_colour))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HIGHLIGHT_COLOURS.forEach { colour ->
                Swatch(Color(0xFF000000.toInt() or colour), draft.highlight == colour, Modifier.weight(1f).height(32.dp), RoundedCornerShape(10.dp)) { draft = draft.copy(highlight = colour) }
            }
            val none = stringResource(R.string.tools_edit_none)
            Box(
                Modifier.weight(1f).height(32.dp).clip(RoundedCornerShape(10.dp))
                    .border(if (draft.highlight == null) 2.dp else 1.dp, if (draft.highlight == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .clickable(role = Role.RadioButton) { draft = draft.copy(highlight = null) }.semantics { contentDescription = none },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Label(stringResource(R.string.tools_edit_custom_colour))
        HexField(draft.colour ?: originalColour) { draft = draft.copy(colour = it) }
        Sample(draft, originalColour)
    }
}

/** Find and replace: the words to look for, what to put in their place, everywhere in the PDF. */
@Composable
private fun FindSheet(onClose: () -> Unit, onReplaceAll: (String, String, Boolean) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var replacement by rememberSaveable { mutableStateOf("") }
    var matchCase by rememberSaveable { mutableStateOf(false) }
    SheetFrame(
        stringResource(R.string.tools_edit_find_replace), stringResource(R.string.tools_edit_find_hint), onClose,
        stringResource(R.string.tools_edit_replace_all), { onReplaceAll(query, replacement, matchCase) }, actionEnabled = query.isNotEmpty() && query != replacement
    ) {
        OutlinedTextField(query, { query = it.replace('\n', ' ').take(200) }, singleLine = true, label = { Text(stringResource(R.string.tools_edit_find_label)) }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(replacement, { replacement = it.replace('\n', ' ').take(200) }, singleLine = true, label = { Text(stringResource(R.string.tools_edit_replace_label)) }, modifier = Modifier.fillMaxWidth())
        FilterChip(selected = matchCase, onClick = { matchCase = !matchCase }, label = { Text(stringResource(R.string.tools_edit_match_case)) })
    }
}

/** The changes made so far, page by page: a tap goes to its page, the cross takes it back. */
@Composable
private fun ChangesSheet(state: EditorState, onClose: () -> Unit, calls: EditorCalls) {
    SheetFrame(
        stringResource(R.string.tools_edit_changes_title), pluralStringResource(R.plurals.tools_edit_changes, state.edits.size, state.edits.size), onClose,
        stringResource(R.string.common_done), onClose
    ) {
        state.edits.sortedWith(compareBy({ it.page }, { it.index }, { it.id })).forEach { edit ->
            val old = state.before[edit.target].orEmpty()
            val kind = when {
                edit.isNew -> stringResource(R.string.tools_edit_change_added)
                edit.text.isEmpty() -> stringResource(R.string.tools_edit_change_deleted)
                else -> null
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { calls.onOpenChange(edit) }
            ) {
                Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            listOfNotNull(stringResource(R.string.tools_edit_change_page, edit.page + 1), kind).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (old.isNotEmpty()) Text(
                            old, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (edit.text.isNotEmpty()) Text(edit.text, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { calls.onRemoveChange(edit) }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.tools_edit_change_remove)) }
                }
            }
        }
    }
}

/** Save: the new PDF's name, and a reminder that the original stays as it is. */
@Composable
private fun SaveSheet(state: EditorState, onClose: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(state.suggestedName) }
    SheetFrame(
        stringResource(R.string.tools_edit_save_title), pluralStringResource(R.plurals.tools_edit_changes, state.edits.size, state.edits.size), onClose,
        stringResource(R.string.tools_edit_save), { onSave(name.trim()) }, actionEnabled = name.isNotBlank() && state.edits.isNotEmpty()
    ) {
        OutlinedTextField(
            value = name, onValueChange = { name = it.replace('\n', ' ').take(80) }, singleLine = true,
            label = { Text(stringResource(R.string.tools_edit_save_name)) }, suffix = { Text(".pdf") }, modifier = Modifier.fillMaxWidth()
        )
        Text(stringResource(R.string.tools_edit_save_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- small parts ----------------------------------------------------------------------------------------------

/** A small heading over a group of controls. */
@Composable
private fun Label(text: String) {
    Text(
        text.uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
    )
}

/** A rounded outline round a row of content. */
@Composable
private fun Outlined(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)).then(modifier),
        verticalAlignment = Alignment.CenterVertically, content = content
    )
}

/** Minus, a value and plus in one outline; [lead] names the value, beside it or (when [stacked]) above it. */
@Composable
private fun Stepper(
    value: String, modifier: Modifier = Modifier, enabled: Boolean = true, lead: String? = null, stacked: Boolean = false,
    minusLabel: String = stringResource(R.string.tools_edit_smaller), plusLabel: String = stringResource(R.string.tools_edit_larger),
    onMinus: () -> Unit, onPlus: () -> Unit
) {
    Outlined(modifier) {
        if (lead != null && !stacked) Text(lead, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 14.dp))
        IconButton(onClick = onMinus, enabled = enabled, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Remove, contentDescription = minusLabel, modifier = Modifier.size(18.dp)) }
        Column(if (lead != null && !stacked) Modifier else Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            if (lead != null && stacked) Text(lead, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1,
                color = if (lead != null && !stacked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = onPlus, enabled = enabled, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Add, contentDescription = plusLabel, modifier = Modifier.size(18.dp), tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A square on/off button with an icon, as wide as its share of the row. */
@Composable
private fun RowScope.Square(icon: ImageVector, label: String, selected: Boolean, enabled: Boolean, bordered: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.weight(1f).height(40.dp).clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .then(if (bordered || selected) Modifier.border(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
}

/** One part of the bar under the Home controls: an icon and a word, opening a sheet. */
@Composable
private fun Segment(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A tile with an icon (or a few letters) over its name; tinted when chosen. */
@Composable
private fun Tile(
    label: String, icon: ImageVector?, selected: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true,
    glyph: String? = null, tint: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.alpha(if (enabled) 1f else 0.42f).clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp).padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (selected) MaterialTheme.colorScheme.primary else tint)
        if (glyph != null) Text(glyph, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(
            label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** Bold, italic, underline and strike-through as four tiles. */
@Composable
private fun StyleTiles(draft: PdfTextEditor.Edit, onDraft: (PdfTextEditor.Edit) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile(stringResource(R.string.tools_edit_bold), Icons.Default.FormatBold, draft.bold, Modifier.weight(1f)) { onDraft(draft.copy(bold = !draft.bold)) }
        Tile(stringResource(R.string.tools_edit_italic), Icons.Default.FormatItalic, draft.italic, Modifier.weight(1f)) { onDraft(draft.copy(italic = !draft.italic)) }
        Tile(stringResource(R.string.tools_edit_underline), Icons.Default.FormatUnderlined, draft.underline, Modifier.weight(1f)) { onDraft(draft.copy(underline = !draft.underline)) }
        Tile(stringResource(R.string.tools_edit_strike), Icons.Default.StrikethroughS, draft.strike, Modifier.weight(1f)) { onDraft(draft.copy(strike = !draft.strike)) }
    }
}

/** Left, centre, right and justify as four tiles. */
@Composable
private fun AlignTiles(draft: PdfTextEditor.Edit, onDraft: (PdfTextEditor.Edit) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(PdfTextEditor.Align.LEFT, Icons.AutoMirrored.Filled.FormatAlignLeft, R.string.tools_edit_align_left),
            Triple(PdfTextEditor.Align.CENTRE, Icons.Default.FormatAlignCenter, R.string.tools_edit_align_centre),
            Triple(PdfTextEditor.Align.RIGHT, Icons.AutoMirrored.Filled.FormatAlignRight, R.string.tools_edit_align_right),
            Triple(PdfTextEditor.Align.JUSTIFY, Icons.Default.FormatAlignJustify, R.string.tools_edit_align_justify)
        ).forEach { (align, icon, label) ->
            Tile(stringResource(label), icon, draft.align == align, Modifier.weight(1f)) {
                onDraft(draft.copy(align = align, wrap = draft.wrap || align == PdfTextEditor.Align.JUSTIFY))
            }
        }
    }
}

/** Wrap long lines, and whether the lines under this one move down for it. */
@Composable
private fun WrapSwitches(draft: PdfTextEditor.Edit, onDraft: (PdfTextEditor.Edit) -> Unit) {
    SwitchRow(stringResource(R.string.tools_edit_wrap_long), stringResource(R.string.tools_edit_wrap_long_sub), draft.wrap) { onDraft(draft.copy(wrap = it)) }
    // Added text sits where it was put and pushes nothing.
    if (!draft.isNew) SwitchRow(stringResource(R.string.tools_edit_push), stringResource(R.string.tools_edit_push_sub), draft.pushBelow) { onDraft(draft.copy(pushBelow = it)) }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(role = Role.Switch) { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** The room above the text and below each of its paragraphs. */
@Composable
private fun SpacingSteppers(draft: PdfTextEditor.Edit, onDraft: (PdfTextEditor.Edit) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Stepper(
            stringResource(R.string.tools_edit_size_value, number(draft.spaceBefore)), modifier = Modifier.weight(1f).height(54.dp), lead = stringResource(R.string.tools_edit_before), stacked = true,
            onMinus = { onDraft(draft.copy(spaceBefore = (draft.spaceBefore - 2f).coerceAtLeast(0f))) }, onPlus = { onDraft(draft.copy(spaceBefore = (draft.spaceBefore + 2f).coerceAtMost(72f))) }
        )
        Stepper(
            stringResource(R.string.tools_edit_size_value, number(draft.spaceAfter)), modifier = Modifier.weight(1f).height(54.dp), lead = stringResource(R.string.tools_edit_after), stacked = true,
            onMinus = { onDraft(draft.copy(spaceAfter = (draft.spaceAfter - 2f).coerceAtLeast(0f))) }, onPlus = { onDraft(draft.copy(spaceAfter = (draft.spaceAfter + 2f).coerceAtMost(72f))) }
        )
    }
}

/** Left, up, down and right: each press moves the text [step] points. */
@Composable
private fun MoveArrows(enabled: Boolean, step: Float = 1f, onMove: (Float, Float) -> Unit) {
    IconButton(onClick = { onMove(-step, 0f) }, enabled = enabled) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.tools_edit_move_left)) }
    IconButton(onClick = { onMove(0f, -step) }, enabled = enabled) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.tools_edit_move_up)) }
    IconButton(onClick = { onMove(0f, step) }, enabled = enabled) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.tools_edit_move_down)) }
    IconButton(onClick = { onMove(step, 0f) }, enabled = enabled) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.tools_edit_move_right)) }
}

/** The selection's own words (or a stock phrase) as they will look, on white like the page. */
@Composable
private fun Sample(edit: PdfTextEditor.Edit, originalColour: Int) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(stringResource(R.string.tools_edit_preview).uppercase(), style = MaterialTheme.typography.labelSmall, letterSpacing = 0.8.sp, color = Color(0xFF6B7280))
        Text(
            edit.text.lineSequence().firstOrNull { it.isNotBlank() } ?: stringResource(R.string.tools_edit_preview_text),
            style = sampleStyle(edit, originalColour), maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/** A round (or [shape]d) patch of [colour] to pick; ringed and ticked when chosen. */
@Composable
private fun Swatch(colour: Color, selected: Boolean, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape = CircleShape, mark: String? = null, onClick: () -> Unit) {
    val light = colour.red * 0.299f + colour.green * 0.587f + colour.blue * 0.114f > 0.6f
    Box(
        modifier.clip(shape).background(colour)
            .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = if (light) Color.Black else Color.White)
        else if (mark != null) Text(mark, color = if (light) Color.Black else Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/** The theme's colours in two rows of eight; the first patch of all is the colour the line has in the PDF. */
@Composable
private fun ColourGrid(chosen: Int?, originalColour: Int, onPick: (Int?) -> Unit) {
    val same = stringResource(R.string.tools_edit_colour_same)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val all: List<Int?> = listOf<Int?>(null) + THEME_COLOURS.drop(1)
        all.chunked(8).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { colour ->
                    Swatch(
                        Color(0xFF000000.toInt() or (colour ?: originalColour)), chosen == colour,
                        Modifier.weight(1f).height(32.dp).then(if (colour == null) Modifier.semantics { contentDescription = same } else Modifier),
                        mark = if (colour == null) "=" else null
                    ) { onPick(colour) }
                }
            }
        }
    }
}

/** A colour typed as six hex digits, with a patch showing it. */
@Composable
private fun HexField(colour: Int, onColour: (Int) -> Unit) {
    var text by remember(colour) { mutableStateOf(String.format(Locale.ROOT, "%06X", colour and 0xFFFFFF)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF000000.toInt() or colour)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)))
        OutlinedTextField(
            value = text,
            onValueChange = { typed ->
                text = typed.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
                if (text.length == 6) onColour(text.toInt(16))
            },
            singleLine = true,
            prefix = { Text("#") },
            modifier = Modifier.weight(1f)
        )
    }
}
