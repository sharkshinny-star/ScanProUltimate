package com.example.scanpro.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.scanpro.data.AccentPalette

/**
 * The colour themes as a row of swatches, each drawn in its own brand gradient with the scan line across
 * it, so people see the colours themselves rather than names. [selected] is null when no theme applies
 * (wallpaper colours are on), and then no swatch is marked.
 */
@Composable
fun PalettePicker(
    selected: AccentPalette?,
    onSelect: (AccentPalette) -> Unit,
    modifier: Modifier = Modifier,
    swatchSize: Dp = 48.dp,
    /** Text under the swatches; for use on a coloured backdrop where the default would not read. */
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val dark = isDarkTheme()
    val ring = MaterialTheme.colorScheme.onSurface
    Row(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        AccentPalette.entries.forEach { palette ->
            val isSelected = palette == selected
            val brand = paletteBrand(palette, dark)
            val pop by animateFloatAsState(if (isSelected) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 500f), label = "swatch")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(palette) })
                    .padding(vertical = 6.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(swatchSize + 10.dp)
                        // The ring sits a little outside the swatch, so the gradient itself stays whole.
                        .border(2.5.dp, ring.copy(alpha = pop), CircleShape)
                        .padding(5.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(swatchSize)
                            .graphicsLayer { val s = 0.9f + 0.1f * pop; scaleX = s; scaleY = s }
                            .clip(CircleShape)
                            .drawBehind {
                                drawRect(Brush.linearGradient(listOf(brand.gradientStart, brand.gradientEnd), Offset.Zero, Offset(size.width, size.height)))
                                // A short scan line, the brand's motif, across the middle.
                                val y = size.height * 0.62f
                                drawLine(brand.scanLine, Offset(size.width * 0.22f, y), Offset(size.width * 0.78f, y), size.height * 0.07f, StrokeCap.Round)
                            }
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = brand.onGradient,
                                modifier = Modifier.size(swatchSize * 0.42f).graphicsLayer { alpha = pop; translationY = -size.height * 0.18f }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(palette.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else labelColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}
