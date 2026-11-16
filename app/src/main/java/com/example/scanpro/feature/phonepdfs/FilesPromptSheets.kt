package com.example.scanpro.feature.phonepdfs

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.GradientButton
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.core.designsystem.TileTone

/**
 * Whether ScanPro is what Android opens a PDF with (as the chosen default, or as the only app that can). Only
 * ScanPro's own answer is needed, so this works without seeing other apps.
 */
fun isDefaultPdfViewer(context: Context): Boolean = runCatching {
    val probe = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://com.example.probe/file.pdf"), "application/pdf")
    @Suppress("DEPRECATION")
    val info = context.packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)
    info?.activityInfo?.packageName == context.packageName
}.getOrDefault(false)

/**
 * Once, on Home, after the first scan or the first PDF opened in the viewer: an invitation to make ScanPro the app that
 * opens PDFs. "Set as default" opens the same system page as Settings > Shortcuts & default app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultViewerSheet(onSetDefault: () -> Unit, onNotNow: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onNotNow,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        SheetColumn {
            FolderWithPdfIllustration(Modifier.size(width = 168.dp, height = 128.dp))
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.default_viewer_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.default_viewer_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.default_viewer_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            GradientButton(
                text = stringResource(R.string.default_viewer_set),
                icon = Icons.Rounded.CheckCircle,
                onClick = onSetDefault,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = onNotNow, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.default_viewer_not_now)) }
        }
    }
}

/** Once, before the system file picker opens for a PDF import: how to pick one file or several there. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportTipSheet(onImport: () -> Unit, onCancel: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        SheetColumn {
            PickerIllustration(Modifier.size(width = 200.dp, height = 140.dp))
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.import_tip_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.import_tip_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text(stringResource(R.string.import_tip_cancel))
                }
                Spacer(Modifier.width(12.dp))
                GradientButton(
                    text = stringResource(R.string.import_tip_import),
                    icon = Icons.Rounded.FileOpen,
                    onClick = onImport,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SheetColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) { content() }
}

/** A folder with a PDF sheet rising out of it and a red "PDF" badge. Decorative, so screen readers skip it. */
@Composable
fun FolderWithPdfIllustration(modifier: Modifier = Modifier) {
    val folderBack = TileTone.Amber.content().copy(alpha = 0.55f)
    val folderFront = TileTone.Amber.container()
    val folderEdge = TileTone.Amber.content()
    val paper = MaterialTheme.colorScheme.surface
    val lines = MaterialTheme.colorScheme.outlineVariant
    val badge = PDF_RED
    val brand = ScanProTheme.colors.gradientEnd
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        // Back of the folder with its tab.
        val back = Path().apply {
            moveTo(w * 0.08f, h * 0.30f)
            lineTo(w * 0.08f, h * 0.20f)
            quadraticTo(w * 0.08f, h * 0.14f, w * 0.14f, h * 0.14f)
            lineTo(w * 0.36f, h * 0.14f)
            lineTo(w * 0.44f, h * 0.24f)
            lineTo(w * 0.86f, h * 0.24f)
            quadraticTo(w * 0.92f, h * 0.24f, w * 0.92f, h * 0.30f)
            lineTo(w * 0.92f, h * 0.90f)
            lineTo(w * 0.08f, h * 0.90f)
            close()
        }
        drawPath(back, folderBack)
        // The PDF page, tilted slightly, standing in the folder.
        rotate(-6f, Offset(w * 0.50f, h * 0.55f)) {
            val left = w * 0.26f
            val top = h * 0.04f
            val pw = w * 0.42f
            val ph = h * 0.70f
            drawRoundRect(paper, Offset(left, top), Size(pw, ph), CornerRadius(8f, 8f))
            drawRoundRect(lines, Offset(left, top), Size(pw, ph), CornerRadius(8f, 8f), style = Stroke(width = 2.5f))
            for (i in 0 until 4) {
                val y = top + ph * (0.22f + i * 0.13f)
                drawLine(lines, Offset(left + pw * 0.14f, y), Offset(left + pw * (if (i == 3) 0.6f else 0.86f), y), strokeWidth = 5f)
            }
        }
        // Front of the folder.
        val front = Path().apply {
            moveTo(w * 0.04f, h * 0.42f)
            lineTo(w * 0.96f, h * 0.42f)
            lineTo(w * 0.90f, h * 0.92f)
            lineTo(w * 0.10f, h * 0.92f)
            close()
        }
        drawPath(front, folderFront)
        drawPath(front, folderEdge, style = Stroke(width = 3f))
        // PDF badge on the folder's front.
        val bw = w * 0.30f
        val bh = h * 0.20f
        val bx = w * 0.56f
        val by = h * 0.60f
        drawRoundRect(badge, Offset(bx, by), Size(bw, bh), CornerRadius(bh / 3, bh / 3))
        drawPdfLetters(Offset(bx, by), Size(bw, bh))
        // A small spark in the brand colour.
        drawCircle(brand, radius = w * 0.03f, center = Offset(w * 0.86f, h * 0.10f))
        drawCircle(brand.copy(alpha = 0.5f), radius = w * 0.018f, center = Offset(w * 0.94f, h * 0.20f))
    }
}

/** A file picker: three files, two ticked by a long-press, and the Select button at the top right. Decorative. */
@Composable
private fun PickerIllustration(modifier: Modifier = Modifier) {
    val frame = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outlineVariant
    val selected = TileTone.Indigo.container()
    val accent = TileTone.Indigo.content()
    val text = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height
        drawRoundRect(frame, Offset.Zero, size, CornerRadius(18f, 18f))
        drawRoundRect(outline, Offset.Zero, size, CornerRadius(18f, 18f), style = Stroke(width = 3f))
        // Top bar with the "Select" pill at the top right.
        drawLine(text, Offset(w * 0.07f, h * 0.14f), Offset(w * 0.38f, h * 0.14f), strokeWidth = 7f)
        drawRoundRect(accent, Offset(w * 0.66f, h * 0.06f), Size(w * 0.27f, h * 0.16f), CornerRadius(h * 0.08f, h * 0.08f))
        drawLine(Color.White, Offset(w * 0.71f, h * 0.14f), Offset(w * 0.88f, h * 0.14f), strokeWidth = 6f)
        // Three file rows; the first two are selected.
        for (i in 0 until 3) {
            val top = h * (0.30f + i * 0.23f)
            val rowH = h * 0.19f
            val isSelected = i < 2
            if (isSelected) drawRoundRect(selected, Offset(w * 0.04f, top), Size(w * 0.92f, rowH), CornerRadius(12f, 12f))
            val iconSize = Size(rowH * 0.62f, rowH * 0.78f)
            val iconTop = Offset(w * 0.08f, top + (rowH - iconSize.height) / 2)
            drawRoundRect(PDF_RED, iconTop, iconSize, CornerRadius(5f, 5f))
            drawLine(text, Offset(w * 0.22f, top + rowH * 0.38f), Offset(w * (0.62f - i * 0.06f), top + rowH * 0.38f), strokeWidth = 6f)
            drawLine(text.copy(alpha = 0.25f), Offset(w * 0.22f, top + rowH * 0.68f), Offset(w * 0.46f, top + rowH * 0.68f), strokeWidth = 5f)
            val c = Offset(w * 0.87f, top + rowH / 2)
            if (isSelected) {
                drawCircle(accent, radius = rowH * 0.24f, center = c)
                val tick = Path().apply {
                    moveTo(c.x - rowH * 0.11f, c.y)
                    lineTo(c.x - rowH * 0.03f, c.y + rowH * 0.08f)
                    lineTo(c.x + rowH * 0.12f, c.y - rowH * 0.09f)
                }
                drawPath(tick, Color.White, style = Stroke(width = 4f))
            } else {
                drawCircle(outline, radius = rowH * 0.24f, center = c, style = Stroke(width = 3f))
            }
        }
        // A fingertip pressing on the second file.
        drawCircle(accent.copy(alpha = 0.18f), radius = h * 0.11f, center = Offset(w * 0.50f, h * 0.62f))
        drawCircle(accent.copy(alpha = 0.35f), radius = h * 0.06f, center = Offset(w * 0.50f, h * 0.62f))
    }
}

private val PDF_RED = Color(0xFFE5483B)

/** "PDF" in three blocky strokes, drawn rather than typeset so the illustration needs no font. */
private fun DrawScope.drawPdfLetters(origin: Offset, box: Size) {
    val stroke = box.height * 0.11f
    val letterW = box.width * 0.18f
    val gap = box.width * 0.08f
    val top = origin.y + box.height * 0.25f
    val bottom = origin.y + box.height * 0.75f
    val mid = (top + bottom) / 2
    var x = origin.x + (box.width - (letterW * 3 + gap * 2)) / 2
    val style = Stroke(width = stroke)
    // P
    drawPath(Path().apply {
        moveTo(x, bottom); lineTo(x, top); lineTo(x + letterW, top); lineTo(x + letterW, mid); lineTo(x, mid)
    }, Color.White, style = style)
    x += letterW + gap
    // D
    drawPath(Path().apply {
        moveTo(x, top); lineTo(x + letterW * 0.6f, top); quadraticTo(x + letterW, top, x + letterW, mid)
        quadraticTo(x + letterW, bottom, x + letterW * 0.6f, bottom); lineTo(x, bottom); close()
    }, Color.White, style = style)
    x += letterW + gap
    // F
    drawPath(Path().apply {
        moveTo(x, bottom); lineTo(x, top); lineTo(x + letterW, top); moveTo(x, mid); lineTo(x + letterW * 0.8f, mid)
    }, Color.White, style = style)
}
