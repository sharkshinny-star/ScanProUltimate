package com.example.scanpro.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.NoAccounts
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.example.scanpro.R
import kotlinx.coroutines.launch

private class WelcomePage(val icon: ImageVector, @StringRes val title: Int, @StringRes val body: Int, val points: List<Pair<ImageVector, Int>>)

private val pages = listOf(
    WelcomePage(
        Icons.Default.Lock,
        R.string.ui_welcome_privacy_title,
        R.string.ui_welcome_privacy_body,
        listOf(
            Icons.Default.NoAccounts to R.string.ui_welcome_privacy_point_account,
            Icons.Default.CloudOff to R.string.ui_welcome_privacy_point_offline,
            Icons.Default.MoneyOff to R.string.ui_welcome_privacy_point_ads
        )
    ),
    WelcomePage(
        Icons.Default.DocumentScanner,
        R.string.ui_welcome_scan_title,
        R.string.ui_welcome_scan_body,
        listOf(
            Icons.Default.CameraAlt to R.string.ui_welcome_scan_point_modes,
            Icons.Default.AutoFixHigh to R.string.ui_welcome_scan_point_cleanup,
            Icons.Default.QrCodeScanner to R.string.ui_welcome_scan_point_codes
        )
    ),
    WelcomePage(
        Icons.Default.PictureAsPdf,
        R.string.ui_welcome_more_title,
        R.string.ui_welcome_more_body,
        listOf(
            Icons.Default.Search to R.string.ui_welcome_more_point_search,
            Icons.Default.Description to R.string.ui_welcome_more_point_export,
            Icons.Default.Draw to R.string.ui_welcome_more_point_sign,
            Icons.Default.Badge to R.string.ui_welcome_more_point_contacts
        )
    )
)

/** Shown once on first launch: the privacy promise first, then what the app can do. */
@Composable
fun WelcomeScreen(onStartScanning: () -> Unit, onExplore: () -> Unit) {
    val pagerState = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == pages.lastIndex
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Taps on empty parts of the welcome must not reach the app drawn underneath it.
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(Modifier.fillMaxSize().widthIn(max = 560.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                if (!last) TextButton(onClick = onExplore) { Text(stringResource(R.string.ui_welcome_skip)) }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
                PageContent(pages[index])
            }
            val pageIndicatorDescription = stringResource(R.string.ui_welcome_page_of, pagerState.currentPage + 1, pages.size)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp).semantics { contentDescription = pageIndicatorDescription },
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(pages.size) { index ->
                    Box(
                        Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (index == pagerState.currentPage) 10.dp else 8.dp)
                            .background(
                                if (index == pagerState.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape
                            )
                    )
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                Button(
                    onClick = { if (last) onStartScanning() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text(if (last) stringResource(R.string.ui_welcome_scan_first_page) else stringResource(R.string.ui_welcome_next)) }
                if (last) {
                    TextButton(onClick = onExplore, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ui_welcome_look_around)) }
                }
            }
        }
    }
}

@Composable
private fun PageContent(page: WelcomePage) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(96.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(page.title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(stringResource(page.body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        page.points.forEach { (icon, text) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
