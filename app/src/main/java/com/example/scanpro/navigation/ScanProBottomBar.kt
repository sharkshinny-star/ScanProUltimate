package com.example.scanpro.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.ScanProTheme
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity

/** Windows at least this wide (tablets, unfolded foldables, split-screen on a large display) get a side rail. */
const val WIDE_WINDOW_DP = 600

@Composable
fun isWideWindow(): Boolean {
    // The app's own window, not the whole screen: split-screen and freeform windows count by their real width.
    val widthPx = LocalWindowInfo.current.containerSize.width
    return with(LocalDensity.current) { widthPx.toDp() } >= WIDE_WINDOW_DP.dp
}

private class Tab(val route: String, @androidx.annotation.StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(ScanProDestinations.HOME, R.string.nav_tab_home, Icons.Outlined.Home, Icons.Rounded.Home),
    Tab(ScanProDestinations.FILES, R.string.nav_tab_files, Icons.Outlined.Folder, Icons.Rounded.Folder),
    Tab(ScanProDestinations.TOOLS, R.string.nav_tab_tools, Icons.Outlined.Handyman, Icons.Rounded.Handyman),
    Tab(ScanProDestinations.SETTINGS, R.string.common_settings, Icons.Outlined.Settings, Icons.Rounded.Settings)
)

/** Height of the bar itself, above the navigation-bar inset. */
private val BarHeight = 68.dp
private val ScanButtonSize = 60.dp

/**
 * Bottom navigation shared by the top-level tabs, with the scan button raised in the middle so a new
 * scan is one tap away from every tab. The bar's colour runs under the gesture area; the items sit above
 * it. On wide windows the side rail takes its place.
 */
@Composable
fun ScanProBottomBar(selectedRoute: String, onTab: (String) -> Unit, onScan: () -> Unit = {}) {
    if (isWideWindow()) return
    Box(Modifier.fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(BarHeight)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.take(2).forEach { tab -> BarItem(tab, selectedRoute == tab.route, Modifier.weight(1f)) { onTab(tab.route) } }
                    Spacer(Modifier.width(ScanButtonSize + 12.dp))
                    tabs.drop(2).forEach { tab -> BarItem(tab, selectedRoute == tab.route, Modifier.weight(1f)) { onTab(tab.route) } }
                }
                Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
        ScanButton(
            onClick = onScan,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-14).dp)
        )
    }
}

@Composable
private fun BarItem(tab: Tab, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val tint by animateColorAsState(if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant, label = "tint")
    val pill by animateColorAsState(if (selected) scheme.secondaryContainer else Color.Transparent, label = "pill")
    val pillWidth by animateDpAsState(if (selected) 56.dp else 32.dp, spring(stiffness = 600f), label = "pillWidth")
    val interaction = remember { MutableInteractionSource() }
    val label = stringResource(tab.label)
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interaction,
                indication = ripple(bounded = true, radius = 36.dp),
                onClick = onClick
            )
            // The label below is merged into this item, so no separate description: it would be read twice.
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .width(pillWidth)
                .height(32.dp)
                .background(pill, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = tint,
            maxLines = 1
        )
    }
}

/** The raised scan button in the middle of the bottom bar, on the brand gradient. */
@Composable
fun ScanButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = ScanButtonSize) {
    val brand = ScanProTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val scanLabel = stringResource(R.string.lib_scan)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier
            .size(size)
            .shadow(10.dp, CircleShape, ambientColor = brand.gradientStart.copy(alpha = 0.5f), spotColor = brand.gradientEnd.copy(alpha = 0.6f))
            .background(Brush.linearGradient(listOf(brand.gradientStart, brand.gradientEnd)), CircleShape)
            .clip(CircleShape)
            .selectable(
                selected = false,
                role = Role.Button,
                interactionSource = interaction,
                indication = ripple(bounded = true, color = Color.White),
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm)
                    onClick()
                }
            )
            .semantics { contentDescription = scanLabel },
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = brand.onGradient, modifier = Modifier.size(28.dp))
    }
}

/** Side navigation for wide windows; shows the same tabs as the bottom bar, with the scan button on top. */
@Composable
fun ScanProNavigationRail(selectedRoute: String, onTab: (String) -> Unit, onScan: () -> Unit = {}) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        header = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)) {
                ScanButton(onClick = onScan, size = 56.dp)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.lib_scan), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    ) {
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEach { tab ->
                val selected = selectedRoute == tab.route
                NavigationRailItem(
                    selected = selected,
                    onClick = { onTab(tab.route) },
                    // The rail always shows the label, so a described icon would be read twice.
                    icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                    label = { Text(stringResource(tab.label)) }
                )
            }
        }
    }
}
