package com.example.scanpro.feature.lock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.scanpro.R
import com.example.scanpro.core.designsystem.AuroraBackground
import com.example.scanpro.core.designsystem.BrandMark
import com.example.scanpro.core.designsystem.GradientButton
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * The gate in front of a locked library. The system prompt opens on its own shortly after the screen
 * appears; the button is there for when it was dismissed.
 */
@Composable
fun LockScreen(onUnlock: () -> Unit, snackbarHostState: SnackbarHostState) {
    // Once per lock, not again after a rotation while the prompt is already up.
    var prompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!prompted) {
            prompted = true
            delay(350.milliseconds)
            onUnlock()
        }
    }
    AuroraBackground(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 420.dp)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    BrandMark(size = 96.dp)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shadowElevation = 4.dp,
                        modifier = Modifier.offset(x = 6.dp, y = 6.dp).size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.lock_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
                GradientButton(
                    text = stringResource(R.string.lock_unlock),
                    icon = Icons.Rounded.Fingerprint,
                    onClick = onUnlock,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.lock_private_by_design),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Authentication problems are reported as snackbars; without a host here they'd never be seen.
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}
