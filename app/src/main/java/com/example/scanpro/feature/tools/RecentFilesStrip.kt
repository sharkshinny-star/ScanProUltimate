package com.example.scanpro.feature.tools

import android.net.Uri
import com.example.scanpro.core.ui.formatBytes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.animateColorAsState
import android.text.format.DateUtils
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.data.RecentPdf
import com.example.scanpro.data.ToolResultFile

/**
 * What Home's one "Recent" section shows (F140). Home used to stack a "Recent files" strip (opened PDFs and tool
 * results, with its own chips) on top of the "Recent" scans grid: two recent sections, one under the other. Now there is
 * one heading and these chips pick the kind; only kinds that have something get a chip.
 */
enum class HomeRecentKind(@androidx.annotation.StringRes val label: Int) {
    SCANS(R.string.home_recent_scans),
    OPENED(R.string.home_recent_opened),
    RESULTS(R.string.tools_results_title)
}

@Composable
fun HomeRecentKindChips(
    kinds: List<HomeRecentKind>,
    selected: HomeRecentKind,
    onSelect: (HomeRecentKind) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        kinds.forEach { kind ->
            FilterChip(selected = kind == selected, onClick = { onSelect(kind) }, label = { Text(stringResource(kind.label)) })
        }
    }
}

/** One card of Home's Recent row (F140): a scan, an opened PDF or a tool result, all drawn the same way. */
class HomeRecentItem(
    val key: String,
    val title: String,
    val caption: String,
    val badge: String? = null,
    val selected: Boolean = false,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
    val thumbnail: @Composable (Modifier) -> Unit
)

/** Card size: portrait, like a page; two and a bit fit across a phone, so the row reads as one that scrolls. */
private val RecentCardWidth = 130.dp
internal val RecentThumbHeight = 175.dp

/**
 * Home's Recent row (F140): one sideways row of identical portrait cards whichever chip is chosen, the way iLovePDF's home
 * shows recent files. Scans used to be a two-column grid and files a smaller row, so the layout jumped when the chip
 * changed, and a single scan left half the grid empty.
 */
@Composable
fun HomeRecentRow(items: List<HomeRecentItem>, modifier: Modifier = Modifier) {
    LazyRow(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items, key = { it.key }) { HomeRecentCard(it) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeRecentCard(item: HomeRecentItem) {
    val shape = RoundedCornerShape(16.dp)
    val outline by animateColorAsState(
        if (item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        label = "recentOutline"
    )
    val longClickLabel = item.onLongClick?.let {
        stringResource(if (item.selected) R.string.lib_card_deselect else R.string.lib_card_select)
    }
    Column(
        Modifier
            .width(RecentCardWidth)
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.tools_recent_open),
                onClick = item.onClick,
                onLongClickLabel = longClickLabel,
                onLongClick = item.onLongClick
            )
            .semantics { selected = item.selected }
            .padding(bottom = 4.dp)
    ) {
        Box(
            Modifier
                .size(width = RecentCardWidth, height = RecentThumbHeight)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(if (item.selected) 2.5.dp else 1.dp, outline, shape)
        ) {
            item.thumbnail(Modifier.fillMaxSize())
            item.badge?.let { TypeBadge(it, Modifier.align(Alignment.TopStart).padding(8.dp)) }
            if (item.selected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    shadowElevation = 2.dp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.lib_cd_selected),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // The whole name on up to two lines: a middle ellipsis on a narrow card left only "ed.pdf" of every result.
        Text(
            item.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
        Text(
            item.caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 1.dp)
        )
    }
}

/** The file type on a card's corner, in the colour people know it by, so an Office or zip result isn't a blank page. */
@Composable
private fun TypeBadge(label: String, modifier: Modifier = Modifier) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
            .background(typeColour(label), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

private fun typeColour(label: String): Color = when (label) {
    "PDF" -> Color(0xFFC62828)
    "DOCX", "DOC" -> Color(0xFF2B579A)
    "XLSX", "XLS", "CSV" -> Color(0xFF1E7145)
    "PPTX", "PPT" -> Color(0xFFB7472A)
    "ZIP" -> Color(0xFF6D4C41)
    "JPG", "JPEG", "PNG", "WEBP", "HEIC" -> Color(0xFF00796B)
    else -> Color(0xFF455A64)
}

/** "2 min. ago", "3 hours ago", "Yesterday" or a date: when, the way the phone says it in its own language. */
internal fun recentTime(time: Long, now: Long): String =
    DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

/** Opened PDFs or tool results as Home cards: name without its extension, a caption, and the type on the corner. */
@Composable
fun fileRecentItems(
    kind: HomeRecentKind,
    recentPdfs: List<RecentPdf>,
    toolResults: List<ToolResultFile>,
    onOpenPdf: (Uri, String) -> Unit,
    onOpenResult: (ToolResultFile) -> Unit,
    now: Long
): List<HomeRecentItem> {
    val context = LocalContext.current
    val opened = stringResource(R.string.home_recent_opened)
    return if (kind == HomeRecentKind.RESULTS) {
        toolResults.take(STRIP_MAX).map { r ->
            HomeRecentItem(
                key = "r:" + r.file.path,
                title = r.file.nameWithoutExtension,
                caption = listOfNotNull(
                    r.sizeBytes.takeIf { it > 0 }?.let { formatBytes(context, it) },
                    recentTime(r.modifiedAt, now)
                ).joinToString(" · "),
                badge = r.file.extension.uppercase().ifBlank { null },
                onClick = { onOpenResult(r) },
                thumbnail = { m ->
                    FileThumbnail(r.file, r.file.extension, "${r.file.path}|${r.modifiedAt}|card", m, renderHeight = RecentThumbHeight)
                }
            )
        }
    } else {
        recentPdfs.take(STRIP_MAX).map { p ->
            HomeRecentItem(
                key = "p:" + p.uri,
                title = p.name.substringBeforeLast('.', p.name).ifBlank { p.name },
                caption = "$opened · ${recentTime(p.openedAt, now)}",
                badge = "PDF",
                onClick = { onOpenPdf(p.uri, p.name) },
                thumbnail = { m -> FileThumbnail(p.uri, "pdf", "${p.uri}|${p.openedAt}|card", m, renderHeight = RecentThumbHeight) }
            )
        }
    }
}

/** More than this belongs on the Tools screen's lists, not on Home. */
private const val STRIP_MAX = 12
