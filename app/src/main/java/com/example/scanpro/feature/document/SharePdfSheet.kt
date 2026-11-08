package com.example.scanpro.feature.document

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.PageThumbnail
import com.example.scanpro.core.designsystem.THUMBNAIL_SMALL
import com.example.scanpro.data.PdfQuality
import com.example.scanpro.data.ScanPage
import com.example.scanpro.imaging.PdfShare

/**
 * The Share PDF card (R28), the way CamScanner, Genius Scan and Lens offer size or quality at share time: tick the
 * pages (all by default), see each quality with its estimated size, optionally keep the file under a size, share.
 * [estimates] is null until [onEstimate] has produced sizes for the current page choice.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SharePdfSheet(
    pages: List<ScanPage>,
    preferredQuality: PdfQuality,
    estimates: Map<PdfQuality, Long>?,
    busy: Boolean,
    onEstimate: (Set<String>) -> Unit,
    onShare: (pageIds: Set<String>, quality: PdfQuality, maxBytes: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selected by rememberSaveable { mutableStateOf(pages.map { it.id }.toSet()) }
    var quality by rememberSaveable { mutableStateOf(preferredQuality) }
    var maxBytes by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(selected) { if (selected.isNotEmpty()) onEstimate(selected) }
    // A size target picks the quality for you; the chips can still override it, and the share shrinks the file anyway.
    LaunchedEffect(maxBytes, estimates) {
        if (maxBytes != null && estimates != null) quality = PdfShare.pickQuality(estimates, maxBytes, quality)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text(stringResource(R.string.doc_share_pdf), style = MaterialTheme.typography.titleLarge)

            // Pages, all ticked to start with.
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (selected.size == pages.size) stringResource(R.string.doc_share_pdf_pages_all)
                    else stringResource(R.string.doc_share_pdf_pages_some, selected.size, pages.size),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                if (selected.size < pages.size) {
                    TextButton(onClick = { selected = pages.map { it.id }.toSet() }) { Text(stringResource(R.string.doc_share_pdf_select_all)) }
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                items(pages, key = { it.id }) { page ->
                    val on = page.id in selected
                    val label = stringResource(R.string.viewer_page_of, page.position + 1, pages.size)
                    Box(
                        Modifier
                            .width(64.dp)
                            .aspectRatio(210f / 297f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selected = if (on) selected - page.id else selected + page.id }
                            .semantics { contentDescription = label }
                    ) {
                        PageThumbnail(page.imagePath, Modifier.fillMaxWidth().aspectRatio(210f / 297f), THUMBNAIL_SMALL, version = page)
                        if (!on) Box(Modifier.fillMaxWidth().aspectRatio(210f / 297f).clip(RoundedCornerShape(8.dp)).padding(0.dp)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(210f / 297f).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)))
                        }
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (on) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Quality, each with its estimated size.
            Text(stringResource(R.string.doc_quality), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PdfQuality.entries.forEach { q ->
                    val on = q == quality
                    val size = estimates?.get(q)
                    OutlinedCard(
                        onClick = { quality = q },
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.outlinedCardColors(containerColor = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    ) {
                        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                            Text(stringResource(q.labelRes), style = MaterialTheme.typography.labelLarge, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium)
                            Text(
                                when {
                                    selected.isEmpty() -> "—"
                                    size == null -> stringResource(R.string.doc_share_pdf_estimating)
                                    else -> stringResource(R.string.doc_share_pdf_about, Formatter.formatShortFileSize(context, size))
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Size target, for upload forms with a limit.
            Text(stringResource(R.string.doc_share_pdf_keep_under), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = maxBytes == null, onClick = { maxBytes = null }, label = { Text(stringResource(R.string.doc_share_pdf_no_limit)) })
                PdfShare.SIZE_TARGETS.forEach { target ->
                    FilterChip(selected = maxBytes == target, onClick = { maxBytes = target }, label = { Text(Formatter.formatShortFileSize(context, target)) })
                }
            }
            val smallest = estimates?.get(PdfQuality.entries.last())
            if (maxBytes != null && smallest != null && smallest > maxBytes!!) {
                Text(
                    stringResource(R.string.doc_share_pdf_will_shrink, Formatter.formatShortFileSize(context, maxBytes!!)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onShare(selected, quality, maxBytes); onDismiss() },
                enabled = !busy && selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.doc_share_pdf))
            }
        }
    }
}
