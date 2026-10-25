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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.scanpro.data.PageAnnotation
import com.example.scanpro.data.WatermarkPosition
import com.example.scanpro.data.WatermarkSize
import com.example.scanpro.data.WatermarkStyle
import kotlin.math.roundToInt

private val WatermarkColors = listOf(
    "Grey" to PageAnnotation.DEFAULT_TEXT_COLOR,
    "Red" to 0xFFD32F2F.toInt(),
    "Blue" to 0xFF1565C0.toInt(),
    "Green" to 0xFF2E7D32.toInt(),
    "Orange" to 0xFFEF6C00.toInt()
)

/** Text, size, position, colour and transparency for "Watermark all pages", with a sketch of the result. */
@Composable
fun WatermarkDialog(onDismiss: () -> Unit, onConfirm: (WatermarkStyle) -> Unit) {
    var text by rememberSaveable { mutableStateOf("CONFIDENTIAL") }
    var size by rememberSaveable { mutableStateOf(WatermarkSize.MEDIUM) }
    var position by rememberSaveable { mutableStateOf(WatermarkPosition.DIAGONAL) }
    var color by rememberSaveable { mutableIntStateOf(PageAnnotation.DEFAULT_TEXT_COLOR) }
    var opacity by rememberSaveable { mutableFloatStateOf(0.35f) }
    val canApply = text.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Watermark all pages") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WatermarkPreview(text, size, position, Color(color).copy(alpha = opacity))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(80) },
                    label = { Text("Watermark text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Size", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WatermarkSize.entries.forEach { option ->
                        FilterChip(selected = size == option, onClick = { size = option }, label = { Text(option.label) })
                    }
                }
                Text("Position", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WatermarkPosition.entries.forEach { option ->
                        FilterChip(selected = position == option, onClick = { position = option }, label = { Text(option.label) })
                    }
                }
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WatermarkColors.forEach { (name, value) ->
                        val chosen = color == value
                        Box(
                            Modifier
                                .size(36.dp)
                                .background(Color(value), CircleShape)
                                .border(
                                    width = if (chosen) 3.dp else 1.dp,
                                    color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = CircleShape
                                )
                                .clickable { color = value }
                                .semantics {
                                    contentDescription = name
                                    selected = chosen
                                }
                        )
                    }
                }
                Text("Transparency: ${100 - (opacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.1f..1f)
            }
        },
        confirmButton = {
            TextButton(
                enabled = canApply,
                onClick = { onConfirm(WatermarkStyle(text.trim(), size, position, color, opacity)) }
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WatermarkPreview(text: String, size: WatermarkSize, position: WatermarkPosition, color: Color) {
    Box(
        Modifier
            .fillMaxWidth(0.5f)
            .heightIn(max = 180.dp)
            .aspectRatio(0.707f)
            .background(Color.White, RoundedCornerShape(4.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
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
