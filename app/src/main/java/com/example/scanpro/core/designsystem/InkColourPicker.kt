package com.example.scanpro.core.designsystem

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.scanpro.R

/**
 * A row of preset ink swatches plus a rainbow "custom" swatch that opens [InkColourPickerDialog].
 * When the chosen colour is not one of the presets, the custom swatch shows it.
 */
@Composable
fun InkColourRow(
    presets: List<Pair<Int, Color>>,
    selected: Color,
    onSelect: (Color) -> Unit,
    modifier: Modifier = Modifier
) {
    var picking by remember { mutableStateOf(false) }
    val isCustom = presets.none { it.second == selected }
    val customDescription = stringResource(R.string.doc_ink_custom)
    // Swatches share the width evenly, so five 48dp touch targets fit a narrow dialog.
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { (nameRes, color) ->
            val chosen = selected == color
            val description = stringResource(R.string.doc_ink_description, stringResource(nameRes))
            Swatch(
                fill = SolidColor(color),
                chosen = chosen,
                description = description,
                onClick = { onSelect(color) }
            )
        }
        Swatch(
            fill = if (isCustom) SolidColor(selected) else RainbowBrush,
            chosen = isCustom,
            description = customDescription,
            onClick = { picking = true }
        ) {
            if (!isCustom) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
    if (picking) {
        InkColourPickerDialog(
            initial = selected,
            onDismiss = { picking = false },
            onPick = { onSelect(it); picking = false }
        )
    }
}

private val RainbowBrush = Brush.sweepGradient(
    listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
)

/** One round swatch: 28dp to look at, with a 48dp touch area around it. */
@Composable
private fun Swatch(
    fill: Brush,
    chosen: Boolean,
    description: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(28.dp)
            .clip(CircleShape)
            .background(fill, CircleShape)
            .border(
                if (chosen) 3.dp else 1.dp,
                if (chosen) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant,
                CircleShape
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = description; selected = chosen },
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Rings of hexagons around the centre: 6 rings gives 127 colours, 13 cells across the middle row. */
private const val HexRings = 6

private class HexCell(val x: Float, val y: Float, val colour: Color)

/**
 * The colours of the hexagon palette in unit cell space (pointy-top cells, size 1, centre at 0,0).
 * Hue follows the angle (clockwise from the top, which is blue). Going outwards a cell goes from white
 * to full colour, then darkens towards the rim.
 */
private val HexCells: List<HexCell> = buildList {
    val root3 = kotlin.math.sqrt(3f)
    for (r in -HexRings..HexRings) {
        for (q in -HexRings..HexRings) {
            val ring = maxOf(kotlin.math.abs(q), kotlin.math.abs(r), kotlin.math.abs(q + r))
            if (ring > HexRings) continue
            val x = root3 * (q + r / 2f)
            val y = 1.5f * r
            val colour = if (ring == 0) {
                Color.White
            } else {
                val d = ring / HexRings.toFloat()
                // Clockwise angle from the top, in degrees.
                val angle = Math.toDegrees(kotlin.math.atan2(x.toDouble(), -y.toDouble())).toFloat()
                val hue = (240f + angle + 360f) % 360f
                val sat = (d / 0.67f).coerceAtMost(1f)
                val value = if (d <= 0.67f) 1f else 1f - (d - 0.67f) / 0.33f * 0.65f
                colourOf(hue, sat, value)
            }
            add(HexCell(x, y, colour))
        }
    }
}

/** Pick any colour: tap or drag across the hexagon palette, or type a hex code. */
@Composable
fun InkColourPickerDialog(initial: Color, onDismiss: () -> Unit, onPick: (Color) -> Unit) {
    var current by remember { mutableStateOf(initial) }
    var hex by remember { mutableStateOf("%06X".format(initial.toArgb() and 0xFFFFFF)) }
    val paletteDescription = stringResource(R.string.doc_colour_palette)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.doc_colour_picker_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                HexPalette(
                    selected = current,
                    description = paletteDescription,
                    onPick = {
                        current = it
                        hex = "%06X".format(it.toArgb() and 0xFFFFFF)
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(current, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { typed ->
                            val clean = typed.removePrefix("#").filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }.take(6).uppercase()
                            hex = clean
                            if (clean.length == 6) current = Color(0xFF000000.toInt() or clean.toInt(16))
                        },
                        singleLine = true,
                        prefix = { Text("#") },
                        label = { Text(stringResource(R.string.doc_colour_hex)) },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(current) }) { Text(stringResource(R.string.doc_colour_use)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/** The hexagon grid itself. Fills the width; the height follows from the cell geometry. */
@Composable
private fun HexPalette(selected: Color, description: String, onPick: (Color) -> Unit) {
    val root3 = kotlin.math.sqrt(3f)
    var width by remember { mutableFloatStateOf(0f) }
    val ringColour = MaterialTheme.colorScheme.onSurface
    val haloColour = MaterialTheme.colorScheme.surface
    // cellSize is the cell's centre-to-corner distance; 13 cells across plus half a cell of margin each side.
    val cellSize = width / (root3 * (2 * HexRings + 1))
    val heightPx = cellSize * (3f * HexRings + 2f)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val centre = Offset(width / 2f, heightPx / 2f)

    fun nearest(p: Offset): HexCell {
        var best = HexCells[0]
        var bestDistance = Float.MAX_VALUE
        for (cell in HexCells) {
            val dx = centre.x + cell.x * cellSize - p.x
            val dy = centre.y + cell.y * cellSize - p.y
            val dist = dx * dx + dy * dy
            if (dist < bestDistance) { bestDistance = dist; best = cell }
        }
        return best
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(with(density) { heightPx.toDp() }.coerceAtLeast(1.dp))
            .onSizeChanged { width = it.width.toFloat() }
            .semantics { contentDescription = description }
            .pointerInput(cellSize) {
                detectTapGestures { p -> if (cellSize > 0f) onPick(nearest(p).colour) }
            }
            .pointerInput(cellSize) {
                detectDragGestures { change, _ ->
                    change.consume()
                    if (cellSize > 0f) onPick(nearest(change.position).colour)
                }
            }
            .drawBehind {
                if (cellSize <= 0f) return@drawBehind
                // A tiny gap between cells, so the hexagons read as separate tiles.
                val drawSize = cellSize * 0.96f
                val path = Path()
                fun hexPath(cx: Float, cy: Float, r: Float) {
                    path.reset()
                    for (i in 0 until 6) {
                        val a = Math.toRadians(60.0 * i - 30.0)
                        val x = cx + r * kotlin.math.cos(a).toFloat()
                        val y = cy + r * kotlin.math.sin(a).toFloat()
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    path.close()
                }
                var chosen: HexCell? = null
                for (cell in HexCells) {
                    hexPath(centre.x + cell.x * cellSize, centre.y + cell.y * cellSize, drawSize)
                    drawPath(path, cell.colour)
                    if (cell.colour == selected) chosen = cell
                }
                chosen?.let {
                    hexPath(centre.x + it.x * cellSize, centre.y + it.y * cellSize, drawSize)
                    drawPath(path, haloColour, style = Stroke(3.dp.toPx()))
                    drawPath(path, ringColour, style = Stroke(1.5.dp.toPx()))
                }
            }
    )
}

private fun colourOf(h: Float, s: Float, v: Float): Color =
    Color(AndroidColor.HSVToColor(floatArrayOf(h.coerceIn(0f, 359.99f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))
