package com.example.scanpro.ui

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.DocumentScanIllustration
import com.example.scanpro.core.designsystem.FilesIllustration
import com.example.scanpro.core.designsystem.GradientButton
import com.example.scanpro.core.designsystem.IllustratedDialogFrame
import com.example.scanpro.core.designsystem.IllustrationPanel
import com.example.scanpro.core.designsystem.ToolsGridIllustration
import kotlinx.coroutines.launch

/** One page of the tour: its title, its text (which names a tab, [tab]) and its picture. */
private enum class TourPage(@StringRes val title: Int, @StringRes val body: Int, @StringRes val tab: Int) {
    Scan(R.string.hint_tour_scan_title, R.string.hint_tour_scan_body, R.string.lib_scan),
    Files(R.string.hint_tour_files_title, R.string.hint_tour_files_body, R.string.nav_tab_files),
    Tools(R.string.hint_tour_tools_title, R.string.hint_tour_tools_body, R.string.nav_tab_tools)
}

/**
 * The short tour after the welcome (F146): three illustrated pages, swiped or stepped through with Next, on scanning,
 * Files and Tools. Each page's picture replays as it comes into view. Skip, Back or a tap outside end it early;
 * [onDone] runs on every way out.
 */
@Composable
internal fun HomeTourDialog(onDone: () -> Unit) {
    val pages = TourPage.entries
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == pages.lastIndex
    IllustratedDialogFrame(onDismiss = onDone) { _ ->
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { index ->
            val page = pages[index]
            val current = pagerState.currentPage == index
            Column {
                IllustrationPanel {
                    when (page) {
                        TourPage.Scan -> DocumentScanIllustration(Modifier.fillMaxSize())
                        TourPage.Files -> FilesIllustration(current, Modifier.fillMaxSize())
                        TourPage.Tools -> ToolsGridIllustration(current, Modifier.fillMaxSize())
                    }
                }
                Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 6.dp)) {
                    Text(
                        stringResource(page.title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                    Spacer(Modifier.height(8.dp))
                    // Room for three lines on every page, so the card doesn't change height as the pages are swiped.
                    Text(
                        stringResource(page.body, stringResource(page.tab)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        minLines = 3
                    )
                }
            }
        }
        val position = stringResource(R.string.hint_tour_position, pagerState.currentPage + 1, pages.size)
        PagerDots(
            count = pages.size,
            current = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 16.dp)
                .semantics { contentDescription = position }
        )
        // Skip's own padding lines its word up with the text above; without it, Got it starts where the text does.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = if (last) 24.dp else 12.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!last) {
                TextButton(onClick = onDone) { Text(stringResource(R.string.hint_skip)) }
                Spacer(Modifier.width(8.dp))
            }
            GradientButton(
                text = stringResource(if (last) R.string.hint_got_it else R.string.hint_next),
                onClick = { if (last) onDone() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Where the tour is: a dot per page, the current one stretched into a pill. */
@Composable
private fun PagerDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) 22.dp else 8.dp, label = "tourDot$index")
            val colour by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "tourDotColour$index"
            )
            Box(
                Modifier
                    .size(width, 8.dp)
                    .clip(CircleShape)
                    .background(colour)
            )
        }
    }
}
