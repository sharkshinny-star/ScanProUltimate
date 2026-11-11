package com.example.scanpro.feature.tools

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.ripple
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.IconTile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * What the Tools landing remembers between visits: the tools the user pinned (in the order they pinned them) and the
 * last few they used. Kept in one small preferences file inside this package; nothing leaves the phone.
 */
@Stable
internal class ToolsMemory(private val context: Context, private val scope: kotlinx.coroutines.CoroutineScope) {
    var pinned by mutableStateOf<List<String>>(emptyList())
        private set
    var recent by mutableStateOf<List<String>>(emptyList())
        private set

    /** Reads the saved lists off the main thread (the first preferences read is a disk read). */
    suspend fun load() {
        val (p, r) = withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            fun read(key: String) = prefs.getString(key, "").orEmpty().split(',').filter { it in KNOWN_IDS }.distinct()
            read(KEY_PINNED) to read(KEY_RECENT)
        }
        pinned = p
        recent = r
    }

    fun isPinned(id: String) = id in pinned

    fun togglePin(id: String) {
        pinned = if (id in pinned) pinned - id else pinned + id
        save()
    }

    /** Called when a tool is opened: it moves to the front of the recent list, which keeps the last four. */
    fun used(id: String) {
        recent = (listOf(id) + recent.filter { it != id }).take(MAX_RECENT)
        save()
    }

    private fun save() {
        val p = pinned.joinToString(",")
        val r = recent.joinToString(",")
        scope.launch(Dispatchers.IO) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PINNED, p).putString(KEY_RECENT, r).apply()
        }
    }

    private companion object {
        const val PREFS = "tools_ui"
        const val KEY_PINNED = "pinned"
        const val KEY_RECENT = "recent"
        const val MAX_RECENT = 4
        val KNOWN_IDS: Set<String> by lazy { (TOOL_SECTIONS.flatMap { it.tools } + VIEWER_TOOL).map { it.id }.toSet() }
    }
}

@Composable
internal fun rememberToolsMemory(): ToolsMemory {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val memory = remember { ToolsMemory(context, scope) }
    LaunchedEffect(memory) { memory.load() }
    return memory
}

/**
 * Words people actually type for a job, besides the tool's own name ("shrink" finds Compress, "password" finds Protect).
 * English only; the tool names in the user's own language are searched as well.
 */
private val TOOL_KEYWORDS = mapOf(
    "merge" to "join combine put together concatenate",
    "split" to "separate divide cut apart",
    "organize" to "reorder rearrange delete remove pages sort",
    "rotate" to "turn flip sideways upside down orientation",
    "extract" to "pull out select pages",
    "compress" to "shrink reduce smaller size email optimise optimize",
    "page_numbers" to "number footer header count paginate numbering",
    "sign" to "signature e-sign autograph initials",
    "watermark" to "stamp draft confidential text overlay copyright brand",
    "img2pdf" to "photos pictures jpg png create make pdf",
    "office" to "word excel powerpoint docx xlsx pptx slides csv document spreadsheet",
    "ocr" to "scan recognise read text searchable",
    "fill" to "form fields sign complete type",
    "pdf2img" to "export pictures jpg png save as images",
    "pdf2text" to "txt copy words plain text word docx",
    "pdf2excel" to "excel xlsx spreadsheet table sheet cells csv",
    "pdf2ppt" to "powerpoint pptx slides presentation deck",
    "protect" to "password encrypt lock secure",
    "unlock" to "remove password decrypt open",
    "repair" to "fix broken damaged corrupt will not open recover",
    "crop_img" to "trim cut photo",
    "compress_img" to "shrink reduce photo kb mb size",
    "convert_img" to "jpg jpeg png webp format change",
    "resize_img" to "dimensions width height pixels scale passport",
    "viewer" to "read open view"
)

private class SearchHit(val tool: PdfToolCard, val sectionTitle: Int)

/** Every tool whose name, category or keywords contain every word typed; best (name-first) matches first. */
@Composable
private fun rememberSearchHits(query: String): List<SearchHit> {
    val resources = androidx.compose.ui.platform.LocalResources.current
    return remember(query, resources) {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return@remember emptyList()
        val all = TOOL_SECTIONS.flatMap { section -> section.tools.map { SearchHit(it, section.title) } } +
            SearchHit(VIEWER_TOOL, R.string.tools_section_convert)
        all.mapNotNull { hit ->
            val name = resources.getString(hit.tool.title).lowercase() + " " + resources.getString(hit.tool.label).lowercase()
            val rest = resources.getString(hit.sectionTitle).lowercase() + " " + TOOL_KEYWORDS[hit.tool.id].orEmpty()
            when {
                words.all { it in name } -> 0 to hit
                words.all { it in name || it in rest } -> 1 to hit
                else -> null
            }
        }.sortedBy { it.first }.map { it.second }
    }
}

/** The search box that heads the landing: filled, pill-shaped, and quiet until it is used. */
@Composable
internal fun ToolSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        placeholder = { Text(stringResource(R.string.tools_search_placeholder)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.tools_search_clear))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
        )
    )
}

/** All / Edit / Convert / Secure / Image Tools: one tap narrows the landing to a single short list. */
@Composable
internal fun ToolCategoryChips(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(selected = selected == ALL_CATEGORIES, onClick = { onSelect(ALL_CATEGORIES) }, label = { Text(stringResource(R.string.tools_category_all)) })
        TOOL_SECTIONS.forEach { section ->
            FilterChip(selected = selected == section.id, onClick = { onSelect(section.id) }, label = { Text(stringResource(section.title)) })
        }
    }
}

internal const val ALL_CATEGORIES = "all"

/** A list on the Tools tab that Home's "See all" can open it at (F140). */
enum class ToolsFocus(internal val key: String) { RESULTS("results-header"), RECENT_PDFS("recent-header") }

/** One-shot: set by Home just before switching to the Tools tab, taken by the Tools list once it can show it. */
object ToolsFocusRequest {
    val pending = kotlinx.coroutines.flow.MutableStateFlow<ToolsFocus?>(null)
}

/**
 * Scrolls so the row with [key] is at the top. The list lays its rows out lazily, so the key's position is only known once
 * its row has been laid out: start at the end (where Your results and Recent PDFs are) and step back until it shows.
 */
internal suspend fun androidx.compose.foundation.lazy.LazyListState.scrollToKey(key: Any) {
    androidx.compose.runtime.withFrameNanos { }
    scrollToItem((layoutInfo.totalItemsCount - 1).coerceAtLeast(0))
    repeat(40) {
        androidx.compose.runtime.withFrameNanos { }
        layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.let { found ->
            scrollToItem(found.index)
            return
        }
        val first = firstVisibleItemIndex
        if (first == 0) return
        scrollToItem((first - 4).coerceAtLeast(0))
    }
}

/** A heading on the Tools page, with how many tools it holds (F140). */
@Composable
private fun SectionTitle(text: String, count: Int? = null, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 18.dp, bottom = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 1.dp)
            )
        }
    }
}

/**
 * One line under each tool's name (F140): what it does, in a few words. The tiles used to be an icon and a name only, so
 * 25 tools read as a wall of names and the less obvious ones (OCR, Repair, Fill form) said nothing about themselves.
 */
internal val TOOL_DESCRIPTIONS: Map<String, Int> = mapOf(
    "merge" to R.string.tools_desc_merge,
    "split" to R.string.tools_desc_split,
    "organize" to R.string.tools_desc_organize,
    "rotate" to R.string.tools_desc_rotate,
    "extract" to R.string.tools_desc_extract,
    "compress" to R.string.tools_desc_compress,
    "repair" to R.string.tools_desc_repair,
    "page_numbers" to R.string.tools_desc_page_numbers,
    "watermark" to R.string.tools_desc_watermark,
    "sign" to R.string.tools_desc_sign,
    "img2pdf" to R.string.tools_desc_img2pdf,
    "office" to R.string.tools_desc_office,
    "ocr" to R.string.tools_desc_ocr,
    "fill" to R.string.tools_desc_fill,
    "pdf2img" to R.string.tools_desc_pdf2img,
    "pdf2text" to R.string.tools_desc_pdf2text,
    "pdf2excel" to R.string.tools_desc_pdf2excel,
    "pdf2ppt" to R.string.tools_desc_pdf2ppt,
    "protect" to R.string.tools_desc_protect,
    "unlock" to R.string.tools_desc_unlock,
    "crop_img" to R.string.tools_desc_crop_img,
    "compress_img" to R.string.tools_desc_compress_img,
    "convert_img" to R.string.tools_desc_convert_img,
    "resize_img" to R.string.tools_desc_resize_img,
    "viewer" to R.string.tools_desc_viewer
)

/**
 * A tool as a card (F140): its icon, its name and one line on what it does. Pressing it shrinks it a touch, so a tap
 * feels answered at once; a long press pins or unpins it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolCard(
    tool: PdfToolCard,
    pinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = stringResource(tool.title)
    val description = TOOL_DESCRIPTIONS[tool.id]?.let { stringResource(it) }
    val pinLabel = stringResource(if (pinned) R.string.tools_unpin_action else R.string.tools_pin_action)
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "toolPress")
    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClickLabel = name,
                onClick = onClick,
                onLongClickLabel = pinLabel,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onTogglePin() }
            )
            .semantics {
                // Read once, name then what it does (the texts below are covered by this).
                contentDescription = if (description != null) "$name. $description" else name
                customActions = listOf(CustomAccessibilityAction(pinLabel) { onTogglePin(); true })
            }
            .padding(start = 14.dp, end = 12.dp, top = 14.dp, bottom = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            IconTile(tool.icon, tool.tone, size = 42.dp, corner = 13.dp, iconSize = 22.dp)
            Spacer(Modifier.weight(1f))
            if (pinned) {
                Icon(Icons.Default.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (description != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** One row of cards, all as tall as the tallest, so a name that wraps doesn't leave its neighbour short. */
@Composable
private fun ToolCardRow(
    tools: List<PdfToolCard>,
    perRow: Int,
    memory: ToolsMemory,
    onLaunch: (PdfToolCard) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        tools.forEach { tool ->
            ToolCard(
                tool = tool,
                pinned = memory.isPinned(tool.id),
                onClick = { memory.used(tool.id); onLaunch(tool) },
                onTogglePin = { memory.togglePin(tool.id) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        // A short last row keeps its cards the same width as the rows above.
        repeat(perRow - tools.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** A pill for the quick-access strip: one tap runs the tool again; a long press pins or unpins it. */
@Composable
private fun QuickToolChip(tool: PdfToolCard, pinned: Boolean, onClick: () -> Unit, onTogglePin: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val pinLabel = stringResource(if (pinned) R.string.tools_unpin_action else R.string.tools_pin_action)
    val name = stringResource(tool.title)
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = name,
                onClick = onClick,
                onLongClickLabel = pinLabel,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onTogglePin() }
            )
            .semantics { customActions = listOf(CustomAccessibilityAction(pinLabel) { onTogglePin(); true }) }
            .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(tool.icon, tool.tone, size = 32.dp, corner = 16.dp, iconSize = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        if (pinned) {
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun SearchResultRow(hit: SearchHit, onClick: () -> Unit) {
    val name = stringResource(hit.tool.title)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(role = Role.Button, onClickLabel = name, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(hit.tool.icon, hit.tool.tone, size = 40.dp, corner = 12.dp, iconSize = 22.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                TOOL_DESCRIPTIONS[hit.tool.id]?.let { stringResource(it) } ?: stringResource(hit.sectionTitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The body of the Tools landing, as list items: search results while a query is typed; otherwise pinned tools, the
 * last few used, and the categories (all of them, or the one chosen). The caller owns the list and the other items.
 */
internal fun androidx.compose.foundation.lazy.LazyListScope.toolsLanding(
    query: String,
    category: String,
    memory: ToolsMemory,
    perRow: Int = 2,
    onLaunch: (PdfToolCard) -> Unit
) {
    if (query.isNotBlank()) {
        item(key = "search-results") {
            val hits = rememberSearchHits(query)
            Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.height(4.dp))
                if (hits.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.tools_no_match, query.trim()), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.tools_no_match_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                } else {
                    hits.forEach { hit ->
                        SearchResultRow(hit) { memory.used(hit.tool.id); onLaunch(hit.tool) }
                    }
                }
            }
        }
        return
    }

    val everyTool = TOOL_SECTIONS.flatMap { it.tools } + VIEWER_TOOL
    val pinnedTools = memory.pinned.mapNotNull { id -> everyTool.firstOrNull { it.id == id } }
    val recentTools = memory.recent.mapNotNull { id -> everyTool.firstOrNull { it.id == id } }.filter { it !in pinnedTools }

    if (category == ALL_CATEGORIES && (pinnedTools.isNotEmpty() || recentTools.isNotEmpty())) {
        item(key = "quick-access") {
            Column(Modifier.animateContentSize()) {
                SectionTitle(
                    stringResource(
                        when {
                            recentTools.isEmpty() -> R.string.tools_pinned
                            pinnedTools.isEmpty() -> R.string.tools_recent_tools
                            else -> R.string.tools_quick_access
                        }
                    )
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (pinnedTools + recentTools).forEach { tool ->
                        QuickToolChip(
                            tool = tool,
                            pinned = memory.isPinned(tool.id),
                            onClick = { memory.used(tool.id); onLaunch(tool) },
                            onTogglePin = { memory.togglePin(tool.id) }
                        )
                    }
                }
            }
        }
    }

    // F140: a heading and then one list item per row of cards, so only the rows on screen are composed when the tab opens
    // (the whole section used to be one item).
    TOOL_SECTIONS.filter { category == ALL_CATEGORIES || it.id == category }.forEach { section ->
        item(key = "section-${section.id}", contentType = "tools-header") {
            SectionTitle(stringResource(section.title), count = section.tools.size)
        }
        section.tools.chunked(perRow).forEachIndexed { index, row ->
            item(key = "section-${section.id}-$index", contentType = "tools-row") {
                ToolCardRow(row, perRow, memory, onLaunch)
            }
        }
    }

    if (category == ALL_CATEGORIES) {
        item(key = "viewer-header", contentType = "tools-header") {
            SectionTitle(stringResource(R.string.tools_section_read))
        }
        item(key = "viewer", contentType = "tools-row") {
            Column {
                ToolCardRow(listOf(VIEWER_TOOL), perRow, memory, onLaunch)
                if (memory.pinned.isEmpty()) {
                    Text(
                        stringResource(R.string.tools_pin_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }
            }
        }
    }
}
