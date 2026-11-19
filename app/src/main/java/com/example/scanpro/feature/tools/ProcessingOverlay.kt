package com.example.scanpro.feature.tools

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.PROCESSING_DONE_MS
import com.example.scanpro.core.designsystem.ProcessingAnimation
import com.example.scanpro.core.designsystem.ProcessingDots
import com.example.scanpro.core.designsystem.rememberMotionEnabled
import kotlinx.coroutines.launch

/** How the progress card is arranged for the space it is given. */
internal data class ProgressCardLayout(val sideBySide: Boolean, val dial: Dp, val maxWidth: Dp)

/**
 * The arrangement for a window of [width] by [height]: the dial above the text on anything tall enough, and beside
 * it where the height is short (a phone on its side, a small split-screen window), so the card never needs scrolling
 * to reach Cancel there. The dial grows on a tablet and shrinks on a narrow phone.
 */
internal fun progressCardLayout(width: Dp, height: Dp): ProgressCardLayout {
    val sideBySide = height < 440.dp && width >= 480.dp
    val dial = when {
        sideBySide -> (height - 112.dp).coerceIn(96.dp, 132.dp)
        width >= 600.dp && height >= 600.dp -> 168.dp
        width < 340.dp -> 112.dp
        else -> 144.dp
    }
    return ProgressCardLayout(sideBySide, dial, maxWidth = if (sideBySide) 520.dp else if (dial > 144.dp) 420.dp else 360.dp)
}

/**
 * The card shown over the tools while one works. [finishMillis] is 0 while the tool runs; once the work is done the
 * view model keeps the card for that long, and the dial closes to 100% and draws its tick in that time.
 */
@Composable
internal fun ProcessingOverlay(
    current: Int,
    total: Int,
    title: String,
    subtitle: String?,
    onCancel: () -> Unit,
    finishMillis: Int = 0
) {
    val finishing = finishMillis > 0
    val motion = rememberMotionEnabled()
    // The card arrives instead of popping in: the scrim fades up and the card settles from slightly small.
    val arrive = remember { Animatable(if (motion) 0f else 1f) }
    val settle = remember { Animatable(if (motion) 0.9f else 1f) }
    LaunchedEffect(Unit) {
        if (!motion) return@LaunchedEffect
        launch { arrive.animateTo(1f, tween(220)) }
        settle.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow))
    }
    Surface(
        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = arrive.value },
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)
    ) {
        // Back and Cancel ask first: a stray Back press used to throw away a long compression or text recognition.
        // Once the work is done there is nothing to stop: Back is swallowed until the result shows.
        var confirmStop by rememberSaveable { mutableStateOf(false) }
        BackHandler { if (!finishing) confirmStop = true }
        if (confirmStop && !finishing) {
            StopTaskDialog(onKeepGoing = { confirmStop = false }, onStop = { confirmStop = false; onCancel() })
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            val layout = progressCardLayout(maxWidth, maxHeight)
            val reported = if (total > 0) (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
            Card(
                // Margins first, then the cap: the card fills a phone's width less its margins, and stays a dialog
                // rather than a wide banner on a tablet or in landscape.
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .widthIn(max = layout.maxWidth)
                    .fillMaxWidth()
                    .graphicsLayer { scaleX = settle.value; scaleY = settle.value },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                // Both arrangements fill the card's width and centre their content in it. (The column used to wrap
                // its content, which left the dial and the text against the card's left edge.) They scroll when a
                // large font leaves too little height, so Cancel is never cut off, and grow smoothly when a tool
                // starts reporting its count, instead of jumping.
                if (layout.sideBySide) {
                    Row(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProgressDial(reported, finishMillis, layout.dial)
                        Spacer(Modifier.width(20.dp))
                        Column(
                            modifier = Modifier.weight(1f).animateContentSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            ProgressText(title, subtitle, if (finishing) total else current, total)
                            Spacer(Modifier.height(14.dp))
                            CancelButton(visible = !finishing, onClick = { confirmStop = true })
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).animateContentSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ProgressDial(reported, finishMillis, layout.dial)
                        Spacer(Modifier.height(6.dp))
                        ProgressText(title, subtitle, if (finishing) total else current, total)
                        Spacer(Modifier.height(20.dp))
                        CancelButton(visible = !finishing, onClick = { confirmStop = true })
                    }
                }
            }
        }
    }
}

/**
 * The processing animation with its figure under it: a ring that fills with the real progress (circling while the
 * tool cannot tell how long it will take) around a page the scan line keeps passing over. While the card plays its
 * finish, the ring closes from wherever it was to 100% and the figure counts up with it, leaving
 * [PROCESSING_DONE_MS] at the end for the tick. Only this part redraws during that count.
 */
@Composable
private fun ProgressDial(reported: Float?, finishMillis: Int, size: Dp) {
    val finishing = finishMillis > 0
    val closing = remember { Animatable(0f) }
    LaunchedEffect(finishing) {
        if (!finishing) return@LaunchedEffect
        closing.snapTo(reported ?: 0f)
        closing.animateTo(1f, tween((finishMillis - PROCESSING_DONE_MS - 50).coerceAtLeast(0), easing = FastOutSlowInEasing))
    }
    // Never behind the real figure: the first finishing frame is drawn before the count above has started.
    val fraction = if (finishing) maxOf(closing.value, reported ?: 0f) else reported
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ProcessingAnimation(progress = fraction, size = size)
        Spacer(modifier = Modifier.height(10.dp))
        // The same slot holds the dots until a tool reports progress, then the percentage, so the title below
        // stays put on every tool, reporting or not.
        Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
            if (fraction != null) {
                Text(
                    // While the tool works this is the real figure, so it always agrees with the "28 / 40" below
                    // and only the ring eases; during the finish it counts up with the ring.
                    text = stringResource(R.string.tools_progress_percent, (fraction * 100).toInt()),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                ProcessingDots(MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ProgressText(title: String, subtitle: String?, current: Int, total: Int) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )

    val showSubtitle = !subtitle.isNullOrBlank() && subtitle != title
    if (total > 0 && showSubtitle) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = subtitle.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }

    if (total > 0) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.tools_progress_count, current, total),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Cancel while the tool works. During the finish there is nothing left to cancel: the button keeps its place, unseen,
 * so the card does not shrink, and "Done" stands where it was.
 */
@Composable
private fun CancelButton(visible: Boolean, onClick: () -> Unit) {
    Box(contentAlignment = Alignment.Center) {
        OutlinedButton(
            onClick = onClick,
            enabled = visible,
            modifier = if (visible) Modifier else Modifier.alpha(0f).clearAndSetSemantics { }
        ) {
            Text(stringResource(R.string.common_cancel))
        }
        if (!visible) {
            Text(
                text = stringResource(R.string.common_done),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Draws the overlay for the running tool's progress, collecting the flow here so only this part redraws on each tick. */
@Composable
internal fun ToolProgressHost(progress: kotlinx.coroutines.flow.StateFlow<ToolProgress?>, onCancel: () -> Unit) {
    val running by progress.collectAsStateWithLifecycle()
    running?.let {
        ProcessingOverlay(
            current = it.current,
            total = it.total,
            title = it.title,
            subtitle = it.subtitle,
            onCancel = onCancel,
            finishMillis = it.finishMillis
        )
    }
}
