package com.example.scanpro.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/** Bottom navigation shared by the top-level tabs. */
@Composable
fun ScanProBottomBar(selectedRoute: String, onTab: (String) -> Unit) {
    NavigationBar {
        listOf(
            Triple(ScanProDestinations.HOME, "Home", Icons.Default.Home),
            Triple(ScanProDestinations.FILES, "Files", Icons.Default.Description),
            Triple(ScanProDestinations.TOOLS, "Tools", Icons.Default.PictureAsPdf),
            Triple(ScanProDestinations.SETTINGS, "Settings", Icons.Default.Settings)
        ).forEach { (route, label, icon) ->
            NavigationBarItem(
                selected = selectedRoute == route,
                onClick = { onTab(route) },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) }
            )
        }
    }
}
