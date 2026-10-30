package com.example.scanpro.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.example.scanpro.R

/** Windows at least this wide (tablets, unfolded foldables, split-screen on a large display) get a side rail. */
const val WIDE_WINDOW_DP = 600

@Composable
fun isWideWindow(): Boolean = LocalConfiguration.current.screenWidthDp >= WIDE_WINDOW_DP

private val tabs = listOf(
    Triple(ScanProDestinations.HOME, R.string.nav_tab_home, Icons.Default.Home),
    Triple(ScanProDestinations.FILES, R.string.nav_tab_files, Icons.Default.Description),
    Triple(ScanProDestinations.TOOLS, R.string.nav_tab_tools, Icons.Default.PictureAsPdf),
    Triple(ScanProDestinations.SETTINGS, R.string.common_settings, Icons.Default.Settings)
)

/** Bottom navigation shared by the top-level tabs. On wide windows the side rail takes its place. */
@Composable
fun ScanProBottomBar(selectedRoute: String, onTab: (String) -> Unit) {
    if (isWideWindow()) return
    NavigationBar {
        tabs.forEach { (route, labelRes, icon) ->
            val label = stringResource(labelRes)
            NavigationBarItem(
                selected = selectedRoute == route,
                onClick = { onTab(route) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                alwaysShowLabel = false
            )
        }
    }
}

/** Side navigation for wide windows; shows the same tabs as the bottom bar. */
@Composable
fun ScanProNavigationRail(selectedRoute: String, onTab: (String) -> Unit) {
    NavigationRail {
        tabs.forEach { (route, labelRes, icon) ->
            val label = stringResource(labelRes)
            NavigationRailItem(
                selected = selectedRoute == route,
                onClick = { onTab(route) },
                // The rail always shows the label, so a described icon would be read twice.
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(label) }
            )
        }
    }
}
