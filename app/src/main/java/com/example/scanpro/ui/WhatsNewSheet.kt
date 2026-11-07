package com.example.scanpro.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.GradientButton
import com.example.scanpro.core.designsystem.IconTile
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.core.designsystem.TileTone

private class NewThing(val icon: ImageVector, val tone: TileTone, @StringRes val title: Int, @StringRes val body: Int)

private val newThings = listOf(
    NewThing(Icons.Rounded.ColorLens, TileTone.Rose, R.string.whats_new_themes_title, R.string.whats_new_themes_body),
    NewThing(Icons.Rounded.TipsAndUpdates, TileTone.Amber, R.string.whats_new_tips_title, R.string.whats_new_tips_body)
)

/**
 * Shown once to people updating from an earlier release (see WHATS_NEW_VERSION in SettingsStore): what this
 * release adds, with a shortcut to the colour themes. New installs get the welcome instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(onPickColours: () -> Unit, onDismiss: () -> Unit) {
    val brand = ScanProTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .background(Brush.linearGradient(listOf(brand.gradientStart, brand.gradientEnd)), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = brand.onGradient, modifier = Modifier.size(32.dp)) }
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.whats_new_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                newThings.forEach { thing ->
                    Row(verticalAlignment = Alignment.Top) {
                        IconTile(thing.icon, thing.tone, size = 44.dp, corner = 14.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(thing.title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(thing.body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            GradientButton(
                text = stringResource(R.string.whats_new_pick_colours),
                icon = Icons.Rounded.ColorLens,
                onClick = onPickColours,
                modifier = Modifier.fillMaxWidth()
            )
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.whats_new_got_it)) }
        }
    }
}
