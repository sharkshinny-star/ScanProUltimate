package com.example.scanpro.feature.tools

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.data.RecentPdf
import com.example.scanpro.data.ToolResultFile

/** One card of the Home strip: a PDF opened in ScanPro, or a file a tool made. */
private class StripFile(val key: String, val name: String, val source: Any, val extension: String, val cacheKey: String, val open: () -> Unit)

/**
 * "Recent files" on Home, the way iLovePDF's home screen shows them: a sideways row of first-page thumbnails, with
 * chips to switch between PDFs opened in ScanPro and files the tools made. Only kinds that have files get a chip.
 */
@Composable
fun RecentFilesStrip(
    recentPdfs: List<RecentPdf>,
    toolResults: List<ToolResultFile>,
    onOpenPdf: (Uri, String) -> Unit,
    onOpenResult: (ToolResultFile) -> Unit,
    modifier: Modifier = Modifier
) {
    if (recentPdfs.isEmpty() && toolResults.isEmpty()) return
    // Starts on whichever kind has the newest file.
    val resultsNewer = (toolResults.firstOrNull()?.modifiedAt ?: 0L) >= (recentPdfs.firstOrNull()?.openedAt ?: 0L)
    // Follows the newest file until the user picks a chip; a kind with no files left is never shown.
    var picked by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val showResults = when {
        toolResults.isEmpty() -> false
        recentPdfs.isEmpty() -> true
        else -> picked ?: resultsNewer
    }

    val files = if (showResults) {
        toolResults.take(STRIP_MAX).map { r ->
            StripFile("r:" + r.file.path, r.name, r.file, r.file.extension, "${r.file.path}|${r.modifiedAt}") { onOpenResult(r) }
        }
    } else {
        recentPdfs.take(STRIP_MAX).map { p ->
            StripFile("p:" + p.uri, p.name, p.uri, "pdf", "${p.uri}|${p.openedAt}") { onOpenPdf(p.uri, p.name) }
        }
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.home_recent_files),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (recentPdfs.isNotEmpty()) {
                FilterChip(selected = !showResults, onClick = { picked = false }, label = { Text(stringResource(R.string.home_recent_opened)) })
            }
            if (toolResults.isNotEmpty()) {
                FilterChip(selected = showResults, onClick = { picked = true }, label = { Text(stringResource(R.string.tools_results_title)) })
            }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(files, key = { it.key }) { file ->
                Column(
                    Modifier
                        .width(96.dp)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.tools_recent_open), onClick = file.open)
                ) {
                    FileThumbnail(
                        source = file.source,
                        extension = file.extension,
                        cacheKey = file.cacheKey,
                        modifier = Modifier.size(width = 96.dp, height = 124.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        file.name,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** More than this belongs on the Tools screen's lists, not on Home. */
private const val STRIP_MAX = 12
