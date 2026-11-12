package com.example.scanpro.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.FitText
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.core.designsystem.cappedFontScale
import com.example.scanpro.core.designsystem.rememberMotionEnabled
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

/** Windows at least this wide (tablets, unfolded foldables, split-screen on a large display) get a side rail. */
const val WIDE_WINDOW_DP = 600

/** Windows shorter than this (a phone on its side) get the compact side rail. */
private const val SHORT_WINDOW_DP = 480

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
    val motion = rememberMotionEnabled()
    val tint by animateColorAsState(if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant, label = "tint")
    val pill by animateColorAsState(if (selected) scheme.secondaryContainer else Color.Transparent, label = "pill")
    val pillWidth by animateDpAsState(if (selected) 56.dp else 32.dp, spring(stiffness = 600f), label = "pillWidth")
    // Subtle bounce on tab selection icon: graphicsLayer ensures draw-phase only execution (zero recompositions).
    val iconScale by animateFloatAsState(
        targetValue = if (selected && motion) 1.10f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f),
        label = "tabIconScale"
    )
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
            Icon(
                if (selected) tab.selectedIcon else tab.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
        }
        Spacer(Modifier.height(4.dp))
        // The bar has a fixed height and a quarter of the width: at a 150 % font size "Settings" lost its last letter
        // and the labels were cut at the bottom, so the label grows at most 1.3 times and ends in … if still too long.
        // FitText shrinks a label that is still too long in a long language (Tamil) instead of cutting it.
        FitText(
            label,
            style = MaterialTheme.typography.labelMedium.cappedFontScale(),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = tint
        )
    }
}

/** The raised scan button in the middle of the bottom bar, on the brand gradient. */
@Composable
fun ScanButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = ScanButtonSize) {
    val brand = ScanProTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val motion = rememberMotionEnabled()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && motion) 0.92f else 1.0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "scanButtonScale"
    )
    val scanLabel = stringResource(R.string.lib_scan)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
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
    // A phone on its side is wide enough for the rail but only about 360 dp tall: the Scan label and the gaps are
    // dropped there, and the tabs scroll, so the last tab (Settings) is never cut off at the bottom.
    val short = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } < SHORT_WINDOW_DP.dp
    // At a large font size on such a phone, four labelled tabs do not fit the height (the last label was cut off
    // at the bottom, R44): only the selected tab then shows its label, and the others keep it as their description.
    val compactLabels = short && LocalDensity.current.fontScale > 1.3f
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        header = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = if (short) 4.dp else 8.dp, bottom = if (short) 4.dp else 12.dp)
            ) {
                ScanButton(onClick = onScan, size = 56.dp)
                if (!short) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.lib_scan),
                        style = MaterialTheme.typography.labelMedium.cappedFontScale(),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    ) {
        if (!short) Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(if (short) 0.dp else 4.dp)
        ) {
            tabs.forEach { tab ->
                val selected = selectedRoute == tab.route
                NavigationRailItem(
                    selected = selected,
                    onClick = { onTab(tab.route) },
                    // In compact mode the label is omitted for all tabs so items maintain uniform height without jumping on selection; the icon supplies the description.
                    icon = {
                        Icon(
                            if (selected) tab.selectedIcon else tab.icon,
                            contentDescription = if (compactLabels) stringResource(tab.label) else null
                        )
                    },
                    alwaysShowLabel = !compactLabels,
                    label = if (compactLabels) null else {
                        {
                            FitText(
                                stringResource(tab.label),
                                style = MaterialTheme.typography.labelMedium.cappedFontScale()
                            )
                        }
                    }
                )
            }
        }
    }
}
