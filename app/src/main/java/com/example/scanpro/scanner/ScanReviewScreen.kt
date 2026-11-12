package com.example.scanpro.scanner

import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.filled.Folder
import com.example.scanpro.feature.library.FolderPickerDialog
import androidx.compose.material.icons.filled.Edit
import com.example.scanpro.core.designsystem.TextInputDialog
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import com.example.scanpro.core.designsystem.FileLoadingAnimation
import com.example.scanpro.core.designsystem.ProcessingDots
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.exifinterface.media.ExifInterface
import com.example.scanpro.R
import com.example.scanpro.core.ui.FilterStrip
import com.example.scanpro.data.CropQuad
import com.example.scanpro.data.NormalizedPoint
import com.example.scanpro.data.PageAdjustments
import com.example.scanpro.data.PageFilter
import com.example.scanpro.imaging.ImageProcessor
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Curve handles keep their warm colour so they stand apart from the corner handles. */
private val Amber = Color(0xFFFFB95C)

/** The "desk" the page lies on, in the light and dark themes. */
private val DeskLight = Color(0xFF8A7D6B)
private val DeskDark = Color(0xFF4A4338)

/** Rotation is a physical direction, so these icons must not flip in right-to-left layouts (the AutoMirrored ones would); the 90° icon never flips and isn't deprecated. */
private val RotateRightIcon: ImageVector = Icons.Filled.Rotate90DegreesCw

/** What a page's crop, filter and sliders were when the review opened for it; Undo returns to this. */
private data class ReviewSnapshot(val quad: CropQuad, val filter: PageFilter?, val adjustments: PageAdjustments?)

/**
 * "Adjust page": the post-capture review. The page lies on a desk with draggable corner handles; under it a
 * filter strip, brightness/contrast/saturation sliders (per page, in [adjustments]) and the Auto detect, Rotate
 * and Save page actions. Save page moves on to the next page, and finishes with [onDone] on the last one.
 * Rotation is written into the photo's EXIF orientation so crop and rotation never disagree.
 */
@Composable
fun ScanReviewScreen(
    pages: SnapshotStateList<Uri>,
    quads: SnapshotStateMap<String, CropQuad>,
    filters: SnapshotStateMap<String, PageFilter>,
    /** Brightness, contrast and saturation per page, keyed like [filters]; pages without an entry are untouched. */
    adjustments: MutableMap<String, PageAdjustments> = remember { mutableStateMapOf() },
    /** What to point out per page (blurred, glare, too dark), keyed like [filters]; filled in as each check finishes. */
    quality: Map<String, Set<PageIssue>> = emptyMap(),
    defaultFilter: PageFilter,
    onDefaultFilterChange: (PageFilter) -> Unit,
    index: Int,
    processing: Boolean,
    onIndexChange: (Int) -> Unit,
    /** Returns to the camera for another page; offered in the overflow menu, the scanner's own counter is the main way. */
    onAdd: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
    /** Returns to the camera to shoot this page again; the next capture replaces it in place. */
    onRetake: (() -> Unit)? = null,
    /** The name typed for the new document; blank leaves it to the automatic title. */
    name: String = "",
    /** Shows the Name row when set; null where a name means nothing (a page shot again for a saved document). */
    onNameChange: ((String) -> Unit)? = null,
    /** The folder the new document goes into; blank for none. */
    folder: String = "",
    /** Existing folders to offer, locked ones left out. */
    folders: List<String> = emptyList(),
    /** Shows the Folder choice when set; null for a retake, like [onNameChange]. */
    onFolderChange: ((String) -> Unit)? = null
) {
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val current = index.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
    val uri = pages[current]
    val key = uri.toString()
    var working by remember { mutableStateOf(false) }
    // Curve mode shows a handle in the middle of each edge that bends it, for book pages and curled paper.
    var curveMode by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var naming by rememberSaveable { mutableStateOf(false) }
    var choosingFolder by rememberSaveable { mutableStateOf(false) }
    var panel by rememberSaveable { mutableIntStateOf(PANEL_FILTERS) }
    // Bumped after a rotation so the page is decoded again from the same file.
    var rotationTick by remember { mutableIntStateOf(0) }
    val blocked = working || processing
    // Turned sideways there is not room for the page and every control in one column: the controls move beside it.
    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val primary = MaterialTheme.colorScheme.primary
    val desk = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) DeskDark else DeskLight

    // While a rotation or the save is running, Back is swallowed: leaving then would strand a half-written file.
    BackHandler(enabled = blocked) { }
    BackHandler(enabled = !blocked) { onBack() }

    val bitmap by produceState<Bitmap?>(initialValue = null, key, rotationTick) {
        value = null
        value = withContext(Dispatchers.IO) { ImageProcessor.decodeOrientedScaled(uri.path.orEmpty(), 1800) }
    }

    val pageFilter = filters[key] ?: defaultFilter
    val pageAdjustments = adjustments[key] ?: PageAdjustments.DEFAULT
    val sliders = reviewSliders(pageAdjustments)
    fun setSliders(bri: Int, con: Int, sat: Int) {
        val next = reviewAdjustments(bri, con, sat)
        if (next == PageAdjustments.DEFAULT) adjustments.remove(key) else adjustments[key] = next
    }

    // Filter previews follow the outline and sliders, a moment after the last change so dragging stays smooth.
    // The previous previews stay up meanwhile.
    var previews by remember(key, rotationTick) { mutableStateOf<Map<PageFilter, Bitmap>>(emptyMap()) }
    val previewQuad = quads[key]
    LaunchedEffect(bitmap, previewQuad, pageAdjustments) {
        val image = bitmap ?: return@LaunchedEffect
        val quad = previewQuad ?: return@LaunchedEffect
        delay(350.milliseconds)
        withContext(Dispatchers.Default) { runCatching { ImageProcessor.filterPreviews(image, quad, adjustments = pageAdjustments) }.getOrNull() }
            ?.let { previews = it }
    }
    // The large view shows the chosen filter and sliders too (on the whole photo, so the outline still lines up).
    // Until the first render is ready, and if it fails, the plain photo stays up; later renders replace the last one.
    var filteredView by remember(bitmap) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(bitmap, pageFilter, pageAdjustments) {
        val image = bitmap ?: return@LaunchedEffect
        if (pageAdjustments != PageAdjustments.DEFAULT) delay(150.milliseconds)
        withContext(Dispatchers.Default) { runCatching { ImageProcessor.filterUncropped(image, pageFilter, pageAdjustments) }.getOrNull() }
            ?.let { filteredView = it }
    }
    // The Result view: the page cut out along its outline, straightened and filtered, the way it will be saved
    // (Adobe Scan, Lens and Genius Scan show the result, not the raw photo). Rendered a moment after the last
    // change so corner drags and slider moves stay smooth; the previous render stays up meanwhile.
    var resultPreview by remember(key, rotationTick) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(bitmap, previewQuad, pageFilter, pageAdjustments, rotationTick) {
        if (bitmap == null) return@LaunchedEffect
        val quad = previewQuad ?: return@LaunchedEffect
        delay(150.milliseconds)
        withContext(Dispatchers.IO) {
            runCatching {
                ImageProcessor.renderPreview(File(uri.path.orEmpty()), pageFilter, 0, quad, pageAdjustments, maxDimension = 1400)
            }.getOrNull()
        }?.let { resultPreview = it }
    }

    // Pages that have not been outlined yet (imports, or captures not seen before) are detected on first view.
    LaunchedEffect(bitmap) {
        val image = bitmap ?: return@LaunchedEffect
        if (quads[key] == null) {
            val detected = withContext(Dispatchers.Default) { detectQuad(image) }
            // Checked again after detecting: the capture's own detection, or a corner the user has already
            // dragged, must not be replaced.
            if (quads[key] == null) quads[key] = detected
        }
    }

    // Undo returns to the page as it was when it came up here (taken once its outline is known).
    var snapshot by remember(key) { mutableStateOf<ReviewSnapshot?>(null) }
    LaunchedEffect(key, previewQuad != null) {
        val quad = quads[key] ?: return@LaunchedEffect
        if (snapshot == null) snapshot = ReviewSnapshot(quad, filters[key], adjustments[key])
    }
    val canUndo = snapshot?.let { (it.quad != quads[key]) || (it.filter != filters[key]) || (it.adjustments != adjustments[key]) } == true
    fun undo() {
        val start = snapshot ?: return
        quads[key] = start.quad
        if (start.filter == null) filters.remove(key) else filters[key] = start.filter
        if (start.adjustments == null) adjustments.remove(key) else adjustments[key] = start.adjustments
        if (!start.quad.isCurved) curveMode = false
    }

    fun rotate(clockwise: Boolean) {
        if (blocked) return
        working = true
        scope.launch {
            // The turn is written into the photo's EXIF orientation: no pixels are re-encoded, nothing is
            // downscaled, and every later step reads the tag. The outline turns with it.
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val exif = ExifInterface(uri.path.orEmpty())
                    exif.rotate(if (clockwise) 90 else -90)
                    exif.saveAttributes()
                }
            }
            result.onSuccess {
                quads[key]?.let { quads[key] = rotateQuad(it, clockwise) }
                // The starting outline turns too, so Undo after a rotation still fits the page.
                snapshot = snapshot?.let { it.copy(quad = rotateQuad(it.quad, clockwise)) }
                rotationTick++
            }.onFailure { message = resources.getString(R.string.scan_review_rotate_failed) }
            working = false
        }
    }

    /** Turns every page a quarter clockwise (CamScanner's batch rotate), the same way [rotate] turns one. */
    fun rotateAll() {
        if (blocked) return
        working = true
        scope.launch {
            val all = pages.toList()
            val turned = withContext(Dispatchers.IO) {
                all.filter { page ->
                    runCatching { ExifInterface(page.path.orEmpty()).apply { rotate(90) }.saveAttributes() }.isSuccess
                }
            }
            turned.forEach { page -> page.toString().let { k -> quads[k]?.let { quads[k] = rotateQuad(it, true) } } }
            if (uri in turned) snapshot = snapshot?.let { it.copy(quad = rotateQuad(it.quad, true)) }
            if (turned.size < all.size) message = resources.getString(R.string.scan_review_rotate_failed)
            rotationTick++
            working = false
        }
    }

    // The page being adjusted stays selected while others are dragged around it.
    val selectedUri by rememberUpdatedState(uri)
    fun movePage(from: Int, to: Int) {
        if (blocked || from !in pages.indices || to !in pages.indices || from == to) return
        val keep = selectedUri
        pages.add(to, pages.removeAt(from))
        onIndexChange(pages.indexOf(keep).coerceAtLeast(0))
    }

    fun move(delta: Int) {
        val target = current + delta
        if (target !in pages.indices) return
        val moved = pages[current]
        pages[current] = pages[target]
        pages[target] = moved
        onIndexChange(target)
    }

    if (naming && onNameChange != null) {
        TextInputDialog(
            title = stringResource(R.string.scan_review_name_dialog_title),
            initial = name,
            label = stringResource(R.string.scan_review_name_label),
            confirmLabel = stringResource(R.string.common_done),
            // Clearing the name hands it back to the automatic title.
            allowBlank = true,
            onDismiss = { naming = false },
            onConfirm = { typed -> onNameChange(typed.trim()); naming = false }
        )
    }

    if (choosingFolder && onFolderChange != null) {
        FolderPickerDialog(
            title = stringResource(R.string.scan_review_folder_dialog_title),
            confirmLabel = stringResource(R.string.common_done),
            folders = folders,
            initial = folder,
            onDismiss = { choosingFolder = false },
            onConfirm = { chosen -> onFolderChange(chosen.trim()); choosingFolder = false }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.scan_review_delete_page)) },
            text = { Text(stringResource(R.string.scan_review_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    quads.remove(key)
                    filters.remove(key)
                    adjustments.remove(key)
                    Thread { File(uri.path.orEmpty()).delete() }.start()
                    pages.remove(uri)
                    if (pages.isEmpty()) onBack() else onIndexChange(current.coerceAtMost(pages.lastIndex))
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        // Top bar: Back · title · Undo · Retake · Delete · Save PDF · overflow (Adobe Scan style).
        var menu by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = !blocked, onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.scan_review_back_to_camera))
            }
            // Seven controls share this bar, so a visible title only ever showed as "Adj". The strip below already marks
            // the page; the title stays for screen readers as a heading.
            val screenTitle = if (pages.size > 1) stringResource(R.string.scan_review_adjust_page, current + 1) else stringResource(R.string.scan_review_adjust_page_single)
            Box(Modifier.weight(1f).semantics { heading(); contentDescription = screenTitle })
            IconButton(enabled = !blocked && canUndo, onClick = ::undo) {
                Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.scan_review_undo))
            }
            if (onRetake != null) {
                IconButton(enabled = !blocked, onClick = onRetake) {
                    Icon(Icons.Default.Replay, contentDescription = stringResource(R.string.scan_review_retake_page))
                }
            }
            IconButton(enabled = !blocked, onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.scan_review_delete_page))
            }
            Button(
                enabled = !blocked,
                onClick = onDone,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                if (processing) {
                    ProcessingDots(MaterialTheme.colorScheme.onPrimary, dotSize = 5.dp)
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.common_save), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            Box {
                IconButton(enabled = !blocked, onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.scan_review_more))
                }
                val curveOn = stringResource(R.string.scan_review_state_on)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.scan_review_no_crop)) },
                        leadingIcon = { Icon(Icons.Default.Fullscreen, contentDescription = null) },
                        enabled = bitmap != null,
                        onClick = { menu = false; quads[key] = CropQuad.DEFAULT }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.scan_review_curve)) },
                        leadingIcon = { Icon(Icons.Default.Gesture, contentDescription = null) },
                        trailingIcon = { if (curveMode) Icon(Icons.Default.Check, contentDescription = curveOn) },
                        enabled = bitmap != null && quads[key] != null,
                        modifier = Modifier.semantics { if (curveMode) stateDescription = curveOn },
                        onClick = {
                            menu = false
                            val image = bitmap ?: return@DropdownMenuItem
                            val start = quads[key] ?: return@DropdownMenuItem
                            if (curveMode) {
                                curveMode = false
                                quads[key] = start.straightened()
                            } else {
                                curveMode = true
                                // The bend handles live on the Crop view, so it comes up with them.
                                panel = PANEL_CROP
                                // Start from the page's own edges; the orange handles then fine-tune each bend.
                                scope.launch {
                                    val estimated = withContext(Dispatchers.Default) {
                                        runCatching { ImageProcessor.estimateCurves(image, start) }.getOrDefault(start)
                                    }
                                    if (curveMode && quads[key] == start) quads[key] = estimated
                                }
                            }
                        }
                    )
                    if (pages.size > 1 && pages.any { (filters[it.toString()] ?: defaultFilter) != pageFilter }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.scan_review_use_on_all, stringResource(pageFilter.labelRes))) },
                            onClick = { menu = false; pages.forEach { filters[it.toString()] = pageFilter }; onDefaultFilterChange(pageFilter) }
                        )
                    }
                    if (pages.size > 1) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.scan_review_move_earlier)) },
                            enabled = current > 0,
                            onClick = { menu = false; move(-1) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.scan_review_move_later)) },
                            enabled = current < pages.lastIndex,
                            onClick = { menu = false; move(1) }
                        )
                    }
                    if (pages.size > 1) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.tools_organizer_rotate_all)) },
                            leadingIcon = { Icon(RotateRightIcon, contentDescription = null) },
                            enabled = !blocked,
                            onClick = { menu = false; rotateAll() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.scan_review_add_another_page)) },
                        leadingIcon = { Icon(Icons.Default.AddAPhoto, contentDescription = null) },
                        onClick = { menu = false; onAdd() }
                    )
                }
            }
        }

        // Stage: the page on its desk. Swiping sideways away from a corner changes page.
        var swipe by remember { mutableFloatStateOf(0f) }
        // Pages follow the reading direction: swiping towards the end of the row (left in LTR, right in RTL) goes forward.
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val stage: @Composable (Modifier) -> Unit = { stageModifier -> Column(stageModifier) {
        // A page that came out blurred, glary or dark says so, with a one-tap retake (Genius Scan, Scanbot). Above the
        // page rather than over it, so it never covers a corner handle.
        val issues = quality[key].orEmpty()
        if (issues.isNotEmpty() && bitmap != null) {
            QualityNotice(
                issues = issues,
                onRetake = onRetake?.takeIf { !blocked },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 6.dp)
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(desk)
                .pointerInput(current, blocked, isRtl) {
                    // Only drags that start away from a corner reach here; corner drags are consumed by the outline.
                    detectHorizontalDragGestures(
                        onDragStart = { swipe = 0f },
                        onHorizontalDrag = { change, amount -> change.consume(); swipe += amount },
                        onDragEnd = {
                            if (!blocked) {
                                val travel = if (isRtl) -swipe else swipe
                                if (travel < -120f && current < pages.lastIndex) onIndexChange(current + 1)
                                else if (travel > 120f && current > 0) onIndexChange(current - 1)
                            }
                            swipe = 0f
                        },
                        onDragCancel = { swipe = 0f }
                    )
                }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            val image = bitmap
            val quad = quads[key]
            if (image == null || quad == null) {
                FileLoadingAnimation()
            } else if (panel == PANEL_CROP) {
                CropCanvas(
                    image = image,
                    display = filteredView ?: image,
                    crop = quad,
                    curveMode = curveMode,
                    enabled = !blocked,
                    accent = primary,
                    onCropChange = { quads[key] = it }
                )
            } else {
                // The finished page (R27); the raw photo with its corner handles is one tap away on the Crop tool.
                val result = resultPreview
                if (result != null) {
                    Image(
                        bitmap = result.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    FileLoadingAnimation()
                }
            }
        } } }

        // Thumbnails, filters and sliders; below the page, or beside it in landscape.
        val lowerControls: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        // Every page as a thumbnail: tap one to adjust it, "+" takes another page.
        ReviewFilmstrip(
            pages = pages,
            quality = quality,
            current = current,
            refresh = rotationTick,
            enabled = !blocked,
            onSelect = onIndexChange,
            onAdd = onAdd,
            onMove = ::movePage
        )

        // Crop, Filters or Adjust, one panel at a time, as Adobe Scan, CamScanner, OKEN and Lens do: the sliders and
        // crop actions no longer take room from the page while nobody is using them. A dot on Adjust says this page's sliders have moved.
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = panel == PANEL_CROP,
                onClick = { panel = PANEL_CROP },
                leadingIcon = { Icon(Icons.Default.Crop, contentDescription = null, Modifier.size(18.dp)) },
                label = { Text(stringResource(R.string.doc_crop)) }
            )
            FilterChip(
                selected = panel == PANEL_FILTERS,
                onClick = { panel = PANEL_FILTERS },
                leadingIcon = { Icon(Icons.Default.ColorLens, contentDescription = null, Modifier.size(18.dp)) },
                label = { Text(stringResource(R.string.doc_filters)) }
            )
            FilterChip(
                selected = panel == PANEL_ADJUST,
                onClick = { panel = PANEL_ADJUST },
                leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null, Modifier.size(18.dp)) },
                label = { Text(stringResource(R.string.scan_review_adjust) + if (adjustments[key] != null) " •" else "") }
            )
            if (pages.size > 1) {
                FilterChip(
                    selected = panel == PANEL_REORDER,
                    onClick = { panel = PANEL_REORDER },
                    leadingIcon = { Icon(Icons.Default.SwapHoriz, contentDescription = null, Modifier.size(18.dp)) },
                    label = { Text(stringResource(R.string.tools_organizer_organize_title).substringBefore(" ")) }
                )
            }
        }
        if (panel == PANEL_CROP) {
            // Dedicated Crop controls directly under the canvas (Adobe Scan style: Auto-detect, No crop, Curve/Straighten).
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    enabled = !blocked && bitmap != null,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    onClick = {
                        val image = bitmap ?: return@OutlinedButton
                        scope.launch { quads[key] = withContext(Dispatchers.Default) { detectQuad(image) } }
                    }
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.scan_review_auto_detect),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize)
                    )
                }
                OutlinedButton(
                    enabled = !blocked && bitmap != null,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    onClick = { quads[key] = CropQuad.DEFAULT }
                ) {
                    Icon(Icons.Default.Fullscreen, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.scan_review_no_crop),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize)
                    )
                }
                val curveOn = stringResource(R.string.scan_review_state_on)
                OutlinedButton(
                    enabled = !blocked && bitmap != null && quads[key] != null,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { if (curveMode) stateDescription = curveOn },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    colors = if (curveMode) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) else ButtonDefaults.outlinedButtonColors(),
                    onClick = {
                        val image = bitmap ?: return@OutlinedButton
                        val start = quads[key] ?: return@OutlinedButton
                        if (curveMode) {
                            curveMode = false
                            quads[key] = start.straightened()
                        } else {
                            curveMode = true
                            scope.launch {
                                val estimated = withContext(Dispatchers.Default) {
                                    runCatching { ImageProcessor.estimateCurves(image, start) }.getOrDefault(start)
                                }
                                if (curveMode && quads[key] == start) quads[key] = estimated
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Gesture, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        stringResource(R.string.scan_review_curve) + if (curveMode) " ✓" else "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.labelMedium.fontSize)
                    )
                }
            }
        } else if (panel == PANEL_FILTERS) {
            FilterStrip(
                previews = previews,
                selected = pageFilter,
                enabled = !blocked,
                // One rule: a filter applies to this page only. "Use on all pages" below applies it to the whole scan.
                onSelect = { filter -> filters[key] = filter },
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp)
            )
            if (pages.size > 1 && pages.any { (filters[it.toString()] ?: defaultFilter) != pageFilter }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        enabled = !blocked,
                        onClick = {
                            pages.forEach { filters[it.toString()] = pageFilter }
                            onDefaultFilterChange(pageFilter)
                        },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.scan_review_use_on_all, stringResource(pageFilter.labelRes)))
                    }
                }
            }
        } else if (panel == PANEL_ADJUST) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                AdjustmentRow(stringResource(R.string.doc_brightness), sliders.first, !blocked) { setSliders(it, sliders.second, sliders.third) }
                AdjustmentRow(stringResource(R.string.doc_contrast), sliders.second, !blocked) { setSliders(sliders.first, it, sliders.third) }
                AdjustmentRow(stringResource(R.string.doc_saturation), sliders.third, !blocked) { setSliders(sliders.first, sliders.second, it) }
            }
            val slidersMoved = adjustments[key] != null
            val othersDiffer = pages.size > 1 && pages.any { adjustments[it.toString()] != adjustments[key] }
            if (slidersMoved || othersDiffer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                    if (slidersMoved) {
                        TextButton(enabled = !blocked, onClick = { adjustments.remove(key) }) { Text(stringResource(R.string.common_reset)) }
                    }
                    if (othersDiffer) {
                        TextButton(
                            enabled = !blocked,
                            onClick = {
                                val chosen = adjustments[key]
                                pages.forEach { page -> if (chosen == null) adjustments.remove(page.toString()) else adjustments[page.toString()] = chosen }
                            }
                        ) { Text(stringResource(R.string.scan_review_adjust_use_on_all)) }
                    }
                }
            }
        } else if (panel == PANEL_REORDER) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(pages, key = { _, uri -> uri.toString() }) { index, uri ->
                    val isCurrent = index == current
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable(enabled = !blocked) { onIndexChange(index) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(6.dp))
                            IconButton(
                                enabled = !blocked && index > 0,
                                onClick = { movePage(index, index - 1) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = stringResource(R.string.scan_review_move_earlier), modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                enabled = !blocked && index < pages.lastIndex,
                                onClick = { movePage(index, index + 1) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = stringResource(R.string.scan_review_move_later), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        }
    }

        // Name the document before saving, as Adobe Scan and CamScanner allow; left alone, it is named automatically. It sits
        // with the Save button: under the controls in portrait, pinned beside it in landscape.
        val nameRow: @Composable () -> Unit = {
            if (onNameChange != null || onFolderChange != null) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onNameChange != null) {
                        SaveOptionCell(
                            label = stringResource(R.string.scan_review_name_label),
                            value = name.ifBlank { stringResource(R.string.scan_review_name_auto) },
                            chosen = name.isNotBlank(),
                            icon = Icons.Default.Edit,
                            enabled = !blocked,
                            onClickLabel = stringResource(R.string.scan_review_name_dialog_title),
                            onClick = { naming = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Where it is saved, as Genius Scan (scan into a folder), Adobe Scan and Lens (save location) allow.
                    if (onFolderChange != null) {
                        SaveOptionCell(
                            label = stringResource(R.string.scan_review_folder_label),
                            value = folder.ifBlank { stringResource(R.string.lib_no_folder) },
                            chosen = folder.isNotBlank(),
                            icon = Icons.Default.Folder,
                            enabled = !blocked,
                            onClickLabel = stringResource(R.string.scan_review_folder_dialog_title),
                            onClick = { choosingFolder = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Bottom buttons: Rotate · Save (saves every page). In Crop mode, Auto detect, No crop and Curve
        // are located directly in the dedicated crop tool panel above.
        val rotateButton: @Composable () -> Unit = {
            // Tap turns the page clockwise; a long press turns it the other way.
            val rotateEnabled = !blocked
            Box(
                Modifier
                    .size(width = 52.dp, height = 48.dp)
                    .clip(RoundedCornerShape(50))
                    .border(BorderStroke(1.dp, if (rotateEnabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)), RoundedCornerShape(50))
                    .combinedClickable(
                        enabled = rotateEnabled,
                        role = Role.Button,
                        onLongClickLabel = stringResource(R.string.scan_review_rotate_left),
                        onLongClick = { rotate(clockwise = false) },
                        onClick = { rotate(clockwise = true) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    RotateRightIcon,
                    contentDescription = stringResource(R.string.scan_review_rotate),
                    tint = if (rotateEnabled) primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
        val saveButton: @Composable (Modifier) -> Unit = { buttonModifier ->
            Button(
                enabled = !blocked,
                modifier = buttonModifier,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                // Always saves the whole scan, from any page; the thumbnails and swiping move between pages.
                onClick = onDone
            ) {
                if (processing) {
                    ProcessingDots(MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (pages.size > 1) stringResource(R.string.scan_review_save_all, pages.size) else stringResource(R.string.scan_review_save_page),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize)
                    )
                }
            }
        }

        if (landscape) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                stage(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 12.dp))
                Column(Modifier.width(320.dp).fillMaxHeight()) {
                    // Rotate scrolls with the other controls here; only the name and Save stay fixed.
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        lowerControls()
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            rotateButton()
                        }
                    }
                    nameRow()
                    saveButton(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp))
                }
            }
        } else {
            stage(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp))
            lowerControls()
            nameRow()
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rotateButton()
                saveButton(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Thumbnails of every page: tap one to adjust it, long-press and drag one to put the pages in order, "+" at the end
 * takes another page. Screen readers get "Move earlier" and "Move later" actions on each thumbnail instead of the drag.
 */
@Composable
private fun ReviewFilmstrip(
    pages: List<Uri>,
    quality: Map<String, Set<PageIssue>>,
    current: Int,
    refresh: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit
) {
    val cache = remember { HashMap<String, Bitmap>() }
    val listState = rememberLazyListState()
    // Not while dragging: scrolling to the selected page would pull the strip out from under the finger.
    var draggingKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(current, pages.size) {
        if (draggingKey == null && current in pages.indices) listState.animateScrollToItem(current)
    }
    val addLabel = stringResource(R.string.scan_review_add_another_page)
    val moveEarlierLabel = stringResource(R.string.scan_review_move_earlier)
    val moveLaterLabel = stringResource(R.string.scan_review_move_later)
    val haptic = LocalHapticFeedback.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // One thumbnail plus the gap: dragging this far past a neighbour swaps the two.
    val step = with(LocalDensity.current) { (48.dp + 8.dp).toPx() }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val latestOnMove by rememberUpdatedState(onMove)
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) {
        itemsIndexed(pages, key = { _, uri -> uri.toString() }, contentType = { _, _ -> "review_thumb" }) { index, uri ->
            // A turned page is decoded again: the refresh counter is part of the cache key.
            val cacheKey = "$uri#$refresh"
            val thumbnail by produceState<Bitmap?>(initialValue = cache[cacheKey], cacheKey) {
                if (value == null) {
                    value = withContext(Dispatchers.IO) { runCatching { ImageProcessor.decodeOrientedScaled(uri.path.orEmpty(), 160) }.getOrNull() }
                    value?.let { cache[cacheKey] = it }
                }
            }
            val isCurrent = index == current
            val key = uri.toString()
            val flagged = quality[key].orEmpty().isNotEmpty()
            val label = stringResource(if (flagged) R.string.scan_quality_page_flagged else R.string.scan_review_page_n, index + 1)
            val dragging = draggingKey == key
            Box(
                Modifier
                    .then(if (dragging) Modifier.zIndex(1f) else Modifier.animateItem())
                    // Outside the lift layer below: inside it, finger movement would be scaled down by the 1.08x lift.
                    .pointerInput(enabled) {
                        if (!enabled) return@pointerInput
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggingKey = key
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.x
                                // Found by key at every step, so another change to the pages mid-drag can't move the wrong one.
                                val from = pages.indexOfFirst { it.toString() == key }
                                if (from < 0) return@detectDragGesturesAfterLongPress
                                // Towards the end of the row is right in left-to-right layouts and left in right-to-left ones.
                                val forward = if (isRtl) -dragOffset else dragOffset
                                val to = when {
                                    forward > step && from < pages.lastIndex -> from + 1
                                    forward < -step && from > 0 -> from - 1
                                    else -> return@detectDragGesturesAfterLongPress
                                }
                                // The strip keeps its scroll on the first visible item's key; when that item is the one
                                // moving, pin the position first, or the strip would jump along with it.
                                val first = listState.firstVisibleItemIndex
                                if (first == from || first == to) listState.requestScrollToItem(first, listState.firstVisibleItemScrollOffset)
                                latestOnMove(from, to)
                                dragOffset += if ((to > from) != isRtl) -step else step
                            },
                            onDragEnd = { draggingKey = null; dragOffset = 0f },
                            onDragCancel = { draggingKey = null; dragOffset = 0f }
                        )
                    }
                    .graphicsLayer {
                        if (dragging) {
                            translationX = dragOffset
                            scaleX = 1.08f
                            scaleY = 1.08f
                            shadowElevation = 8.dp.toPx()
                        }
                    }
                    .size(48.dp, 64.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        if (isCurrent) 2.dp else 1.dp,
                        if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(6.dp)
                    )
                    .clickable(enabled = enabled, role = Role.Tab, onClick = { onSelect(index) })
                    .semantics {
                        contentDescription = label
                        selected = isCurrent
                        customActions = buildList {
                            if (index > 0) add(CustomAccessibilityAction(moveEarlierLabel) { latestOnMove(index, index - 1); true })
                            if (index < pages.lastIndex) add(CustomAccessibilityAction(moveLaterLabel) { latestOnMove(index, index + 1); true })
                        }
                    }
            ) {
                thumbnail?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                if (flagged) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = QualityAmber,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(3.dp)
                            .size(16.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .padding(2.dp)
                    )
                }
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(topStart = 6.dp))
                        .padding(horizontal = 4.dp)
                )
            }
        }
        item(key = "add-page") {
            Box(
                Modifier
                    .size(48.dp, 64.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    .clickable(enabled = enabled, role = Role.Button, onClick = onAdd)
                    .semantics { contentDescription = addLabel },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

/** Amber for "worth a look": readable on the dark thumbnail badge and as the notice's icon in both themes. */
internal val QualityAmber = Color(0xFFFFB13D)

/**
 * Over the top of the page: what looks wrong with it (all issues, most serious first) and, where the page can be shot
 * again, a Retake button. Polite live region, so TalkBack reads it when it appears for the page on screen.
 */
@Composable
private fun QualityNotice(issues: Set<PageIssue>, onRetake: (() -> Unit)?, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 4.dp,
        modifier = modifier.widthIn(max = 420.dp).semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = QualityAmber, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                PageIssue.entries.filter { it in issues }.map { stringResource(it.messageRes) }.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f, fill = false).padding(vertical = 6.dp)
            )
            if (onRetake != null) {
                TextButton(
                    onClick = onRetake,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inversePrimary)
                ) { Text(stringResource(R.string.scan_quality_retake)) }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

/** A labelled, tappable value above the Save button: the new document's name or folder. */
@Composable
private fun SaveOptionCell(
    label: String,
    value: String,
    chosen: Boolean,
    icon: ImageVector,
    enabled: Boolean,
    onClickLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClickLabel = onClickLabel, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                color = if (chosen) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private const val PANEL_FILTERS = 0
private const val PANEL_ADJUST = 1
private const val PANEL_CROP = 2
private const val PANEL_REORDER = 3

/** One slider row: label · slider · value, with the value as a whole number from −50 to 50. */
@Composable
private fun AdjustmentRow(label: String, value: Int, enabled: Boolean, onValueChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, modifier = Modifier.width(88.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(REVIEW_SLIDER_MIN, REVIEW_SLIDER_MAX)) },
            valueRange = REVIEW_SLIDER_MIN.toFloat()..REVIEW_SLIDER_MAX.toFloat(),
            enabled = enabled,
            modifier = Modifier.weight(1f).semantics { contentDescription = label }
        )
        Text(
            value.toString(),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(36.dp)
        )
    }
}

/**
 * The page with a draggable four-corner outline. Dragging moves whichever corner is nearest the touch; in
 * [curveMode] the handle in the middle of each edge bends that edge.
 */
@Composable
private fun CropCanvas(
    image: Bitmap,
    display: Bitmap = image,
    crop: CropQuad,
    curveMode: Boolean,
    enabled: Boolean,
    /** The theme's primary colour: the outline fill and the handle rings. */
    accent: Color,
    onCropChange: (CropQuad) -> Unit
) {
    var active by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    val latest by rememberUpdatedState(crop)
    val latestEnabled by rememberUpdatedState(enabled)
    val latestCurveMode by rememberUpdatedState(curveMode)
    val pagePreview = stringResource(R.string.a11y_page_preview)
    // The page is fitted to both the width and the height with a safe gutter round it, so a handle on the page's
    // edge is drawn whole and can be grabbed from every side, not just from its inner half.
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val imageRatio = image.width.toFloat() / image.height.toFloat()
        val handleInset = minOf(18.dp, maxWidth / 10f, maxHeight / 10f)
        val contentWidthDp = minOf((maxWidth - handleInset * 2).coerceAtLeast(1.dp), (maxHeight - handleInset * 2).coerceAtLeast(1.dp) * imageRatio)
        val contentHeightDp = contentWidthDp / imageRatio
        val insetPx = with(LocalDensity.current) { handleInset.toPx() }
        Canvas(
            Modifier
                .width(contentWidthDp + handleInset * 2)
                .height(contentHeightDp + handleInset * 2)
                .semantics { contentDescription = pagePreview }
                .pointerInput(image, insetPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val contentWidth = (size.width - insetPx * 2f).coerceAtLeast(1f)
                        val contentHeight = (size.height - insetPx * 2f).coerceAtLeast(1f)
                        val points = handles(latest, latestCurveMode).map { Offset(insetPx + it.x * contentWidth, insetPx + it.y * contentHeight) }
                        val nearest = points.indices.minByOrNull { hypot(points[it].x - down.position.x, points[it].y - down.position.y) } ?: 0
                        val reach = 44.dp.toPx()
                        val distance = hypot(points[nearest].x - down.position.x, points[nearest].y - down.position.y)
                        // Away from every corner the touch is left alone, so the page can be swiped.
                        if (distance > reach || !latestEnabled) return@awaitEachGesture
                        active = nearest
                        dragging = true
                        drag(down.id) { change ->
                            change.consume()
                            val delta = change.position - change.previousPosition
                            if (active >= 4) {
                                onCropChange(bend(latest, active - 4, delta.x / contentWidth, delta.y / contentHeight))
                                return@drag
                            }
                            val list = corners(latest).toMutableList()
                            val p = list[active]
                            list[active] = NormalizedPoint(
                                (p.x + delta.x / contentWidth).coerceIn(0f, 1f),
                                (p.y + delta.y / contentHeight).coerceIn(0f, 1f)
                            )
                            // copy() keeps any bends: they are stored relative to the corners, so they follow them.
                            onCropChange(latest.copy(topLeft = list[0], topRight = list[1], bottomRight = list[2], bottomLeft = list[3]))
                        }
                        dragging = false
                    }
                }
        ) {
            // The filtered view under the outline; the corner loupe below keeps the full-detail photo.
            val contentWidth = (size.width - insetPx * 2f).coerceAtLeast(1f)
            val contentHeight = (size.height - insetPx * 2f).coerceAtLeast(1f)
            drawImage(
                display.asImageBitmap(),
                dstOffset = IntOffset(insetPx.toInt(), insetPx.toInt()),
                dstSize = IntSize(contentWidth.toInt(), contentHeight.toInt())
            )
            val all = handles(crop, curveMode = true).map { Offset(insetPx + it.x * contentWidth, insetPx + it.y * contentHeight) }
            val points = all.take(4)
            // Each edge as the curve it will be flattened along (a straight edge's midpoint gives a straight line).
            fun control(a: Offset, b: Offset, mid: Offset) = Offset(2f * mid.x - (a.x + b.x) / 2f, 2f * mid.y - (a.y + b.y) / 2f)
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (edge in 0 until 4) {
                    val a = points[edge]
                    val b = points[(edge + 1) % 4]
                    val c = control(a, b, all[4 + edge])
                    quadraticTo(c.x, c.y, b.x, b.y)
                }
                close()
            }
            drawPath(path, accent.copy(alpha = 0.18f))
            drawPath(path, Color.White, style = Stroke(width = 2.dp.toPx()))
            // Corner handles: 30 dp circles with a translucent primary fill, a white ring and a thin primary outer ring.
            points.forEach { centre ->
                drawCircle(accent.copy(alpha = 0.25f), radius = 12.dp.toPx(), center = centre)
                drawCircle(Color.White, radius = 13.dp.toPx(), center = centre, style = Stroke(width = 2.5.dp.toPx()))
                drawCircle(accent, radius = 15.dp.toPx(), center = centre, style = Stroke(width = 1.5.dp.toPx()))
            }
            if (curveMode) all.drop(4).forEach { drawCircle(Amber, radius = 9.dp.toPx(), center = it) }
            if (dragging && active < 4) {
                val corner = corners(crop)[active]
                val sample = 96
                val sx = (corner.x * image.width).toInt().coerceIn(0, image.width - 1)
                val sy = (corner.y * image.height).toInt().coerceIn(0, image.height - 1)
                val left = (sx - sample / 2).coerceIn(0, (image.width - sample).coerceAtLeast(0))
                val top = (sy - sample / 2).coerceIn(0, (image.height - sample).coerceAtLeast(0))
                val loupe = 120.dp.toPx()
                val dstLeft = if (corner.x < 0.5f) size.width - loupe - 8.dp.toPx() else 8.dp.toPx()
                val dstTop = 8.dp.toPx()
                drawRect(Color.Black.copy(alpha = 0.8f), Offset(dstLeft - 4.dp.toPx(), dstTop - 4.dp.toPx()), Size(loupe + 8.dp.toPx(), loupe + 8.dp.toPx()))
                drawImage(
                    image.asImageBitmap(),
                    srcOffset = IntOffset(left, top),
                    srcSize = IntSize(sample.coerceAtMost(image.width - left), sample.coerceAtMost(image.height - top)),
                    dstOffset = IntOffset(dstLeft.toInt(), dstTop.toInt()),
                    dstSize = IntSize(loupe.toInt(), loupe.toInt())
                )
                val c = Offset(dstLeft + loupe / 2f, dstTop + loupe / 2f)
                drawLine(Color.White, Offset(c.x - 14.dp.toPx(), c.y), Offset(c.x + 14.dp.toPx(), c.y), strokeWidth = 2f)
                drawLine(Color.White, Offset(c.x, c.y - 14.dp.toPx()), Offset(c.x, c.y + 14.dp.toPx()), strokeWidth = 2f)
            }
        }
    }
}

private fun corners(crop: CropQuad) = listOf(crop.topLeft, crop.topRight, crop.bottomRight, crop.bottomLeft)

/** The corners, then (in curve mode) the middle of the top, right, bottom and left edges, bends included. */
private fun handles(crop: CropQuad, curveMode: Boolean): List<NormalizedPoint> {
    val c = corners(crop)
    if (!curveMode) return c
    fun mid(a: NormalizedPoint, b: NormalizedPoint, bow: NormalizedPoint?) =
        NormalizedPoint((a.x + b.x) / 2f + (bow?.x ?: 0f), (a.y + b.y) / 2f + (bow?.y ?: 0f))
    return c + listOf(
        mid(c[0], c[1], crop.topBow), mid(c[1], c[2], crop.rightBow),
        mid(c[2], c[3], crop.bottomBow), mid(c[3], c[0], crop.leftBow)
    )
}

/** Bends edge [edge] (0 top, 1 right, 2 bottom, 3 left) by a drag of (dx, dy), within sensible limits. */
private fun bend(crop: CropQuad, edge: Int, dx: Float, dy: Float): CropQuad {
    fun moved(bow: NormalizedPoint?) = NormalizedPoint(((bow?.x ?: 0f) + dx).coerceIn(-0.3f, 0.3f), ((bow?.y ?: 0f) + dy).coerceIn(-0.3f, 0.3f))
    return when (edge) {
        0 -> crop.copy(topBow = moved(crop.topBow))
        1 -> crop.copy(rightBow = moved(crop.rightBow))
        2 -> crop.copy(bottomBow = moved(crop.bottomBow))
        else -> crop.copy(leftBow = moved(crop.leftBow))
    }
}

/** Detected page outline, or the whole frame when nothing convincing is found. */
private fun detectQuad(image: Bitmap): CropQuad {
    val detection = runCatching { DocumentEdgeDetector.detect(image) }.getOrNull()
    val quad = detection?.quad
    return if (quad != null && detection.confidence >= 0.34f) quad else CropQuad.DEFAULT
}

