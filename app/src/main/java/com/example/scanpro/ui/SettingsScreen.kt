package com.example.scanpro.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.scanpro.data.PdfQuality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    pdfQuality: PdfQuality,
    autoTitle: Boolean,
    appLock: Boolean,
    documentCount: Int,
    trashCount: Int,
    storageBytes: Long,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onPdfQualityChange: (PdfQuality) -> Unit,
    onAutoTitleChange: (Boolean) -> Unit,
    onAppLockChange: (Boolean) -> Unit,
    onImportBackup: (Uri, String) -> Unit,
    bottomBar: @Composable () -> Unit = {}
) {
    var pendingBackup by remember { mutableStateOf<Uri?>(null) }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingBackup = uri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                Text("Default PDF quality", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "High keeps more image detail. Small creates lighter PDFs for messaging and email.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PdfQuality.entries) { quality ->
                    FilterChip(
                        selected = quality == pdfQuality,
                        onClick = { onPdfQualityChange(quality) },
                        label = { Text(quality.label) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            ListItem(
                headlineContent = { Text("Smart OCR titles") },
                supportingContent = {
                    Text("Use recognized text to name new scans automatically.")
                },
                trailingContent = {
                    Switch(checked = autoTitle, onCheckedChange = onAutoTitleChange)
                }
            )

            ListItem(
                headlineContent = { Text("Biometric app lock") },
                supportingContent = {
                    Text("Require a device biometric when the library is locked.")
                },
                leadingContent = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingContent = {
                    Switch(checked = appLock, onCheckedChange = onAppLockChange)
                }
            )

            Spacer(Modifier.height(24.dp))
            Text("Storage", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "$documentCount active documents · $trashCount in recycle bin · ${formatBytes(storageBytes)} used in private app storage",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            Text("Secure backup", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Restore a password-encrypted .scanpro package. Backups include document images, OCR text and export settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedButton(onClick = { backupPicker.launch(arrayOf("application/octet-stream", "application/zip", "*/*")) }) {
                Text("Restore encrypted backup")
            }

            Spacer(Modifier.height(24.dp))
            Text("Privacy", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "ScanPro stores scans and OCR text in app-private storage by default. The primary scanner uses " +
                    "CameraX and OpenCV on-device; Google Play services is only a compatibility fallback. OCR runs " +
                    "on-device. Translation models may download from Google once and then run locally. No document " +
                    "is uploaded by the offline insights feature.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (pendingBackup != null) {
        PasswordInputDialog(
            title = "Restore encrypted backup",
            message = "Enter the password used when this ScanPro backup was created.",
            minimumLength = 6,
            confirmLabel = "Restore",
            onDismiss = { pendingBackup = null }
        ) { password ->
            val uri = pendingBackup ?: return@PasswordInputDialog
            onImportBackup(uri, password)
            pendingBackup = null
        }
    }

}
