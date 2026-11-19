package com.example.scanpro.feature.tools

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.scanpro.R
import com.example.scanpro.imaging.WatermarkConfig
import com.example.scanpro.imaging.WatermarkGeometry
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font

/** The first page as a picture, and its size in points, for the watermark to be drawn on. Null parts mean "could not read it". */
private class PagePreview(val bitmap: Bitmap?, val widthPt: Float, val heightPt: Float)

private val A4_WIDTH = 595f
private val A4_HEIGHT = 842f

private val ANGLES = listOf(45f, 0f, 90f, -45f)

private val SWATCHES = listOf(
    R.string.tools_watermark_color_red to 0xD32F2F,
    R.string.tools_watermark_color_grey to 0x757575,
    R.string.tools_watermark_color_blue to 0x1976D2,
    R.string.tools_watermark_color_black to 0x000000
)

private val RANGE_PATTERN = Regex("""^\s*\d+(\s*-\s*\d+)?(\s*,\s*\d+(\s*-\s*\d+)?)*\s*$""")

/**
 * The watermark screen: the real first page with the watermark drawn on it as you change it, instead of a form that is
 * judged only after the tool has run. Drag the text (or tap) to place it; the rest sits in a panel below the page.
 * The preview is drawn from the same geometry the PDF writer uses, so what is seen here is what is saved.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun WatermarkEditor(
    uri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (WatermarkConfig) -> Unit
) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("CONFIDENTIAL") }
    var colourIndex by rememberSaveable { mutableIntStateOf(1) }
    var opacity by rememberSaveable { mutableFloatStateOf(30f) }
    var sizePt by rememberSaveable { mutableFloatStateOf(54f) }
    var angle by rememberSaveable { mutableFloatStateOf(45f) }
    var tiled by rememberSaveable { mutableStateOf(false) }
    var cx by rememberSaveable { mutableFloatStateOf(0.5f) }
    var cy by rememberSaveable { mutableFloatStateOf(0.5f) }
    var allPages by rememberSaveable { mutableStateOf(true) }
    var range by rememberSaveable { mutableStateOf("") }

    // The picked file once (pages, size, first page): the preview, the page check and the size all come from it.
    val facts by rememberPdfFacts(uri)
    val pageCount = facts?.pageCount
    // Any script works: text Helvetica cannot write (Hindi, Tamil, emoji) is stamped as a picture (PdfTextImage, F139).
    // A range must also name pages that exist ("7-9" on a 5-page PDF is refused here, not after the run).
    val chosenPages: Int? = when {
        pageCount == null -> null
        allPages -> pageCount
        !RANGE_PATTERN.matches(range) -> null
        else -> com.example.scanpro.imaging.PageRanges.parse(range, pageCount).getOrNull()?.flatMap { it.toList() }?.toSet()?.size
    }
    val rangeOk = allPages || (RANGE_PATTERN.matches(range) && (pageCount == null || chosenPages != null))
    val valid = text.isNotBlank() && rangeOk

    val page = remember(facts) {
        facts?.let { f ->
            // A protected or unreadable PDF has no preview: a blank sheet still shows where the text will go.
            PagePreview(
                f.firstPage,
                if (f.pageWidthPt > 0) f.pageWidthPt.toFloat() else A4_WIDTH,
                if (f.pageHeightPt > 0) f.pageHeightPt.toFloat() else A4_HEIGHT
            )
        }
    }
    val summary: String? = facts?.let { f ->
        val total = f.pageCount ?: return@let null
        val on = chosenPages ?: return@let null
        // Text adds a few hundred bytes per page; a picture of the words (other scripts) is stored once and reused.
        val pictureText = !com.example.scanpro.imaging.PdfTextImage.canEncode(PDType1Font.HELVETICA_BOLD, text)
        val bytes = f.sizeBytes + on * (if (tiled) 2_000L else 250L) + if (pictureText) 25_000L else 0L
        pluralStringResource(R.plurals.edit_wm_pages, total, on, total) + " · " +
            stringResource(R.string.img_tool_about, com.example.scanpro.core.ui.formatBytes(context, bytes))
    }

    // Helvetica Bold, as the writer uses it, so the preview text is the width the saved text will be.
    // The width the PDF writer will use: Helvetica's for Latin text, the picture's for any other script.
    val helveticaWidth = remember(text, sizePt) {
        if (com.example.scanpro.imaging.PdfTextImage.canEncode(PDType1Font.HELVETICA_BOLD, text)) {
            PDType1Font.HELVETICA_BOLD.getStringWidth(text) / 1000f * sizePt
        } else {
            com.example.scanpro.imaging.PdfTextImage.widthPt(text, sizePt)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(R.string.tools_watermark_title), style = MaterialTheme.typography.titleLarge) },
                        navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close)) } },
                        actions = { ToolDemoHelpButton("watermark") }
                    )
                },
                bottomBar = {
                    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                      Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                        val support = if (!rangeOk) stringResource(R.string.tools_watermark_pages_invalid) else summary
                        if (support != null) {
                            Text(
                                support,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (rangeOk) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp)
                            )
                        }
                        Button(
                            onClick = {
                                onConfirm(
                                    WatermarkConfig(
                                        text = text.trim(),
                                        colorRgb = SWATCHES[colourIndex].second,
                                        opacityPercent = opacity.toInt(),
                                        fontSizePt = sizePt,
                                        pageRange = if (allPages) "" else range.trim(),
                                        angleDegrees = angle,
                                        centerX = cx,
                                        centerY = cy,
                                        tiled = tiled
                                    )
                                )
                            },
                            enabled = valid,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(52.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.tools_watermark_action), fontWeight = FontWeight.SemiBold)
                        }
                      }
                    }
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    // The page, as large as the space above the controls allows.
                    Box(Modifier.weight(1f).fillMaxWidth().heightIn(min = 180.dp).padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        val loaded = page
                        if (loaded == null) {
                            com.example.scanpro.core.designsystem.FileLoadingAnimation()
                        } else {
                            WatermarkPreviewPage(
                                page = loaded,
                                text = text,
                                sizePt = sizePt,
                                angle = angle,
                                opacity = opacity,
                                colour = SWATCHES[colourIndex].second,
                                tiled = tiled,
                                centerX = cx,
                                centerY = cy,
                                textWidthPt = helveticaWidth,
                                onPlace = { x, y -> cx = x.coerceIn(0f, 1f); cy = y.coerceIn(0f, 1f) }
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.tools_watermark_drag_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp)
                    )
                    val maxPanel = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * 0.36f).toDp() }
                    Surface(
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().heightIn(max = maxPanel)
                    ) {
                        Column(
                            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                label = { Text(stringResource(R.string.tools_watermark_text_label)) },
                                placeholder = { Text(stringResource(R.string.tools_watermark_text_hint)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    R.string.tools_watermark_quick_confidential,
                                    R.string.tools_watermark_quick_draft,
                                    R.string.tools_watermark_quick_copy,
                                    R.string.tools_watermark_quick_approved
                                ).forEach { quick ->
                                    val word = stringResource(quick)
                                    FilterChip(selected = text == word, onClick = { text = word }, label = { Text(word) })
                                }
                            }

                            LabelledRow(stringResource(R.string.tools_watermark_color_label)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SWATCHES.forEachIndexed { index, (label, rgb) ->
                                        val name = stringResource(label)
                                        Box(
                                            Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF000000 or rgb.toLong()))
                                                .then(if (index == colourIndex) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                                .clickable(role = Role.RadioButton) { colourIndex = index }
                                                .semantics { contentDescription = name }
                                        )
                                    }
                                }
                            }

                            Text("${stringResource(R.string.tools_watermark_size_label)}: ${sizePt.toInt()} pt", style = MaterialTheme.typography.bodyMedium)
                            Slider(value = sizePt, onValueChange = { sizePt = it }, valueRange = 16f..120f, modifier = Modifier.fillMaxWidth())
                            Text(stringResource(R.string.tools_watermark_opacity_label, opacity.toInt()), style = MaterialTheme.typography.bodyMedium)
                            Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 10f..90f, modifier = Modifier.fillMaxWidth())

                            LabelledRow(stringResource(R.string.tools_watermark_angle)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ANGLES.forEach { a ->
                                        FilterChip(selected = angle == a, onClick = { angle = a }, label = { Text("${a.toInt()}°") })
                                    }
                                }
                            }

                            LabelledRow(stringResource(R.string.tools_watermark_position)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                    PositionGrid(cx, cy, enabled = !tiled) { x, y -> cx = x; cy = y }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(checked = tiled, onCheckedChange = { tiled = it })
                                        Spacer(Modifier.width(10.dp))
                                        Text(stringResource(R.string.tools_watermark_tile), style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }

                            LabelledRow(stringResource(R.string.tools_watermark_pages)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = allPages, onClick = { allPages = true }, label = { Text(stringResource(R.string.tools_watermark_pages_all)) })
                                    FilterChip(selected = !allPages, onClick = { allPages = false }, label = { Text(stringResource(R.string.tools_watermark_pages_custom)) })
                                }
                            }
                            if (!allPages) {
                                OutlinedTextField(
                                    value = range,
                                    onValueChange = { range = it },
                                    placeholder = { Text(stringResource(R.string.tools_watermark_pages_hint)) },
                                    singleLine = true,
                                    isError = range.isNotBlank() && !rangeOk || (range.isBlank()),
                                    supportingText = if (rangeOk) null else { { Text(stringResource(R.string.tools_watermark_pages_invalid)) } },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelledRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

/** Nine spots on the page: one tap puts the text in a corner, an edge or the middle. */
@Composable
private fun PositionGrid(cx: Float, cy: Float, enabled: Boolean, onPick: (Float, Float) -> Unit) {
    val spots = listOf(0.2f, 0.5f, 0.8f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(0.12f, 0.5f, 0.88f).forEach { y ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                spots.forEach { x ->
                    val here = enabled && kotlin.math.abs(cx - x) < 0.06f && kotlin.math.abs(cy - y) < 0.06f
                    Box(
                        Modifier
                            .size(width = 44.dp, height = 44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (here) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                            .then(if (enabled) Modifier.clickable(role = Role.Button) { onPick(x, y) } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (here) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 1f else 0.4f)))
                    }
                }
            }
        }
    }
}

/** The page picture with the watermark painted over it; drag or tap anywhere on it to place the text. */
@Composable
private fun WatermarkPreviewPage(
    page: PagePreview,
    text: String,
    sizePt: Float,
    angle: Float,
    opacity: Float,
    colour: Int,
    tiled: Boolean,
    centerX: Float,
    centerY: Float,
    textWidthPt: Float,
    onPlace: (Float, Float) -> Unit
) {
    val aspect = page.widthPt / page.heightPt
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val fitWidth = if (maxWidth / maxHeight > aspect) maxHeight * aspect else maxWidth
        val pageWidth = fitWidth
        val pageHeight = fitWidth / aspect
        val density = LocalDensity.current
        val widthPx = with(density) { pageWidth.toPx() }
        val scale = widthPx / page.widthPt
        val config = remember(angle, centerX, centerY, tiled) {
            WatermarkConfig(text = text, angleDegrees = angle, centerX = centerX, centerY = centerY, tiled = tiled)
        }
        val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) } }
        Surface(
            shadowElevation = 4.dp,
            color = Color.White,
            modifier = Modifier
                .size(pageWidth, pageHeight)
                .pointerInput(Unit) { detectTapGestures { onPlace(it.x / size.width, it.y / size.height) } }
                .pointerInput(Unit) { detectDragGestures { change, _ -> change.consume(); onPlace(change.position.x / size.width, change.position.y / size.height) } }
        ) {
            val bitmap = page.bitmap
            if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                if (text.isBlank()) return@Canvas
                val textSizePx = sizePt * scale
                paint.textSize = textSizePx
                paint.color = android.graphics.Color.rgb(android.graphics.Color.red(colour), android.graphics.Color.green(colour), android.graphics.Color.blue(colour))
                paint.alpha = (opacity / 100f * 255).toInt().coerceIn(0, 255)
                val wantedWidth = textWidthPt * scale
                val measured = paint.measureText(text).takeIf { it > 0f } ?: wantedWidth
                paint.textScaleX = (wantedWidth / measured).coerceIn(0.6f, 1.6f)
                // The same stamps the PDF writer places: page coordinates run upwards, the screen's run downwards.
                val stamps = WatermarkGeometry.stamps(config, textWidthPt, sizePt, page.widthPt, page.heightPt, 0)
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    stamps.forEach { stamp ->
                        native.save()
                        native.translate(stamp.x * scale, (page.heightPt - stamp.y) * scale)
                        native.rotate(-Math.toDegrees(stamp.angleRad).toFloat())
                        native.drawText(text, -wantedWidth / 2f, sizePt * 0.36f * scale, paint)
                        native.restore()
                    }
                }
            }
        }
    }
}
