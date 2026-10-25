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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private class WelcomePage(val icon: ImageVector, val title: String, val body: String, val points: List<Pair<ImageVector, String>>)

private val pages = listOf(
    WelcomePage(
        Icons.Default.Lock,
        "Your documents stay on your phone",
        "ScanPro works without an account. Nothing you scan is uploaded unless you choose to share it or save it somewhere.",
        listOf(
            Icons.Default.NoAccounts to "No account or sign-in",
            Icons.Default.CloudOff to "Nothing uploaded: scanning, OCR and PDF tools run on this phone",
            Icons.Default.MoneyOff to "No ads, and no watermark on your scans"
        )
    ),
    WelcomePage(
        Icons.Default.DocumentScanner,
        "Scan like a pro",
        "Point the camera at a page and hold steady. ScanPro finds the edges and captures it for you.",
        listOf(
            Icons.Default.CameraAlt to "Batch mode for many pages, ID cards front and back, books",
            Icons.Default.AutoFixHigh to "Clean up shadows and fingers, straighten curved pages",
            Icons.Default.QrCodeScanner to "Read QR codes and barcodes"
        )
    ),
    WelcomePage(
        Icons.Default.PictureAsPdf,
        "Do more with every page",
        "Every scan is a searchable PDF, ready to share.",
        listOf(
            Icons.Default.Search to "Find any word you scanned",
            Icons.Default.Description to "Export to PDF, Word, text or images",
            Icons.Default.Draw to "Sign, redact, watermark and password-protect",
            Icons.Default.Badge to "Business cards straight to your contacts"
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
                if (!last) TextButton(onClick = onExplore) { Text("Skip") }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
                PageContent(pages[index])
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp).semantics { contentDescription = "Page ${pagerState.currentPage + 1} of ${pages.size}" },
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
                ) { Text(if (last) "Scan your first page" else "Next") }
                if (last) {
                    TextButton(onClick = onExplore, modifier = Modifier.fillMaxWidth()) { Text("Look around first") }
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
        Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(page.body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        page.points.forEach { (icon, text) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(14.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
