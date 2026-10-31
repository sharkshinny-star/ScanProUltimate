package com.example.scanpro.feature.document

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.Eyebrow
import com.example.scanpro.data.PageAnnotation
import com.example.scanpro.data.WatermarkPosition
import com.example.scanpro.data.WatermarkSize
import com.example.scanpro.data.WatermarkStyle
import kotlin.math.roundToInt

/** Colour name resource and ARGB value. */
private val WatermarkColors = listOf(
    R.string.doc_watermark_color_grey to PageAnnotation.DEFAULT_TEXT_COLOR,
    R.string.doc_watermark_color_red to 0xFFD32F2F.toInt(),
    R.string.doc_watermark_color_blue to 0xFF1565C0.toInt(),
    R.string.doc_watermark_color_green to 0xFF2E7D32.toInt(),
    R.string.doc_watermark_color_orange to 0xFFEF6C00.toInt()
)

/** Text, size, position, colour and transparency for "Watermark all pages", with a sketch of the result. */
@Composable
fun WatermarkDialog(onDismiss: () -> Unit, onConfirm: (WatermarkStyle) -> Unit) {
    val defaultText = stringResource(R.string.doc_watermark_default_text)
    var text by rememberSaveable { mutableStateOf(defaultText) }
    var size by rememberSaveable { mutableStateOf(WatermarkSize.MEDIUM) }
    var position by rememberSaveable { mutableStateOf(WatermarkPosition.DIAGONAL) }
    var color by rememberSaveable { mutableIntStateOf(PageAnnotation.DEFAULT_TEXT_COLOR) }
    var opacity by rememberSaveable { mutableFloatStateOf(0.35f) }
    val canApply = text.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.doc_watermark_all_pages)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WatermarkPreview(text, size, position, Color(color).copy(alpha = opacity))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(80) },
                    label = { Text(stringResource(R.string.doc_watermark_text)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Eyebrow(stringResource(R.string.doc_size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WatermarkSize.entries.forEach { option ->
                        FilterChip(selected = size == option, onClick = { size = option }, label = { Text(stringResource(option.labelRes)) }, colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ), shape = androidx.compose.foundation.shape.CircleShape)
                    }
                }
                Eyebrow(stringResource(R.string.doc_position), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WatermarkPosition.entries.forEach { option ->
                        FilterChip(selected = position == option, onClick = { position = option }, label = { Text(stringResource(option.labelRes)) }, colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ), shape = androidx.compose.foundation.shape.CircleShape)
                    }
                }
                Eyebrow(stringResource(R.string.doc_colour), color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Each 36dp swatch sits in a 48dp touch target; the targets touch, which keeps the row inside the dialog
                // (12dp between swatches instead of 10dp).
                Row {
                    WatermarkColors.forEach { (nameRes, value) ->
                        val chosen = color == value
                        val name = stringResource(nameRes)
                        Box(
                            Modifier
                                .minimumInteractiveComponentSize()
                                .size(36.dp)
                                .background(Color(value), CircleShape)
                                .border(
                                    width = if (chosen) 3.dp else 1.dp,
                                    color = if (chosen) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                                .clickable(role = Role.RadioButton) { color = value }
                                .semantics {
                                    contentDescription = name
                                    selected = chosen
                                }
                        )
                    }
                }
                Text(stringResource(R.string.doc_transparency, 100 - (opacity * 100).roundToInt()), style = MaterialTheme.typography.labelLarge)
                Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.1f..1f)
            }
        },
        confirmButton = {
            TextButton(
                enabled = canApply,
                onClick = { onConfirm(WatermarkStyle(text.trim(), size, position, color, opacity)) }
            ) { Text(stringResource(R.string.common_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
private fun WatermarkPreview(text: String, size: WatermarkSize, position: WatermarkPosition, color: Color) {
    Box(
        Modifier
            .fillMaxWidth(0.5f)
            .heightIn(max = 180.dp)
            .aspectRatio(0.707f)
            .shadow(4.dp, RoundedCornerShape(6.dp))
            .background(Color.White, RoundedCornerShape(6.dp))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .padding(8.dp),
        contentAlignment = when (position) {
            WatermarkPosition.BOTTOM -> Alignment.BottomCenter
            else -> Alignment.Center
        }
    ) {
        Text(
            text = text.ifBlank { " " },
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = when (size) {
                WatermarkSize.SMALL -> 10.sp
                WatermarkSize.MEDIUM -> 15.sp
                WatermarkSize.LARGE -> 21.sp
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.rotate(if (position == WatermarkPosition.DIAGONAL) -28f else 0f)
        )
    }
}
