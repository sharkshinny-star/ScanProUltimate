package com.example.scanpro.feature.tools

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.example.scanpro.core.designsystem.ProcessingAnimation
import com.example.scanpro.core.designsystem.ProcessingDots
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.scanpro.R

@Composable
internal fun ProcessingOverlay(
    current: Int,
    total: Int,
    title: String,
    subtitle: String?,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)
    ) {
        // Back and Cancel ask first: a stray Back press used to throw away a long compression or text recognition.
        var confirmStop by rememberSaveable { mutableStateOf(false) }
        BackHandler { confirmStop = true }
        if (confirmStop) {
            StopTaskDialog(onKeepGoing = { confirmStop = false }, onStop = { confirmStop = false; onCancel() })
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                // Capped, so on a tablet or in landscape the card stays a dialog rather than a wide banner.
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth(0.85f)
                    .padding(24.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    // Scrolls when a large font or landscape leaves too little height, so Cancel is never cut off.
                    // Grows smoothly when a tool starts reporting its count, instead of jumping.
                    modifier = Modifier.verticalScroll(rememberScrollState()).animateContentSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // The processing animation: a ring that fills with the real progress (circling while the tool
                    // cannot tell how long it will take) around a page the scan line keeps passing over.
                    val fraction = if (total > 0) (current.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
                    ProcessingAnimation(progress = fraction, size = 132.dp)
                    Spacer(modifier = Modifier.height(10.dp))
                    // The same slot holds the dots until a tool reports progress, then the percentage, so the
                    // title below stays put on every tool, reporting or not.
                    Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
                        if (fraction != null) {
                            Text(
                                // The real figure, so it always agrees with the "28 / 40" below; only the ring eases.
                                text = stringResource(R.string.tools_progress_percent, (fraction * 100).toInt()),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            ProcessingDots(MaterialTheme.colorScheme.primary)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

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
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }
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

                    Spacer(modifier = Modifier.height(20.dp))
                    OutlinedButton(onClick = { confirmStop = true }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                }
            }
        }
    }
}

/** Draws the overlay for the running tool's progress, collecting the flow here so only this part redraws on each tick. */
@Composable
internal fun ToolProgressHost(progress: kotlinx.coroutines.flow.StateFlow<ToolProgress?>, onCancel: () -> Unit) {
    val running by progress.collectAsStateWithLifecycle()
    running?.let {
        ProcessingOverlay(current = it.current, total = it.total, title = it.title, subtitle = it.subtitle, onCancel = onCancel)
    }
}
