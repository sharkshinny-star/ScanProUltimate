package com.example.scanpro.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.example.scanpro.AppViewModel
import com.example.scanpro.core.ui.shareFile
import com.example.scanpro.feature.lock.LockScreen
import com.example.scanpro.navigation.ScanProDestinations
import com.example.scanpro.navigation.ScanProNavHost

/** App shell: app lock gate, app-wide snackbar and share effects, and the navigation graph. */
@Composable
fun ScanProApp(
    viewModel: AppViewModel,
    sessionUnlocked: Boolean,
    onFallbackScanner: () -> Unit,
    onImportImages: () -> Unit,
    onUnlock: () -> Unit,
    onAppLockChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val exportArtifact by viewModel.exportArtifact.collectAsStateWithLifecycle()
    val openDocumentRequest by viewModel.openDocumentRequest.collectAsStateWithLifecycle()
    val appLock by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val pdfImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importPdf(uri)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(exportArtifact) {
        exportArtifact?.let {
            shareFile(context, it.path, it.mimeType, it.title)
            viewModel.clearExportArtifact()
        }
    }
    // A finished camera scan opens its new document once the library has picked it up.
    LaunchedEffect(openDocumentRequest, documents) {
        val id = openDocumentRequest ?: return@LaunchedEffect
        if (documents.any { it.id == id }) {
            navController.navigate(ScanProDestinations.document(id)) { launchSingleTop = true }
            viewModel.clearOpenDocumentRequest()
        }
    }

    if (appLock && !sessionUnlocked) {
        LockScreen(onUnlock)
        return
    }

    ScanProNavHost(
        navController = navController,
        snackbarHostState = snackbarHostState,
        onImportImages = onImportImages,
        onImportPdf = { pdfImportLauncher.launch(arrayOf("application/pdf")) },
        onCameraScanComplete = viewModel::importCameraSession,
        onFallbackScanner = onFallbackScanner,
        onAppLockChange = onAppLockChange
    )
}
