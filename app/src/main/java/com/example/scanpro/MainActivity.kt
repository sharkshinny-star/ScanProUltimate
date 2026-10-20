package com.example.scanpro

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.scanpro.ui.ScanProApp
import com.example.scanpro.ui.ScanProTheme
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dagger.hilt.android.AndroidEntryPoint

import android.net.Uri
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var sessionUnlocked by mutableStateOf(false)
    private var stoppedAt = 0L

    private val compatibilityScanner by lazy {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(100)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
    }

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        if (activityResult.resultCode != RESULT_OK) return@registerForActivityResult
        val scan = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
        val pdfUri = scan?.pdf?.uri
        val pageUris = scan?.pages?.map { it.imageUri }.orEmpty()
        if (pdfUri == null && pageUris.isEmpty()) {
            viewModel.showMessage("The compatibility scanner did not return any pages")
        } else {
            viewModel.importScan(pdfUri, pageUris)
        }
    }

    private val imageImportLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(100)
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importImages(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            ScanProTheme(themeMode = themeMode) {
                ScanProApp(
                    viewModel = viewModel,
                    sessionUnlocked = sessionUnlocked,
                    onFallbackScanner = ::startCompatibilityScanner,
                    onImportImages = {
                        imageImportLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onUnlock = { authenticate { sessionUnlocked = true } },
                    onAppLockChange = { enabled ->
                        // Turning the lock off needs authentication too, or anyone holding the phone could disable it.
                        authenticate {
                            viewModel.setAppLock(enabled)
                            sessionUnlocked = true
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val action = intent.action
        val type = intent.type.orEmpty()

        when {
            action == android.content.Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
                }
                if (uri != null) {
                    if (type == "application/pdf") {
                        viewModel.importPdf(uri)
                    } else if (type.startsWith("image/")) {
                        viewModel.importImages(listOf(uri))
                    }
                }
            }
            action == android.content.Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(android.content.Intent.EXTRA_STREAM).orEmpty()
                }
                if (uris.isNotEmpty()) {
                    viewModel.importImages(uris)
                }
            }
            action == android.content.Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null) {
                    if (type == "application/pdf" || uri.toString().endsWith(".pdf", ignoreCase = true)) {
                        val title = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "PDF Document"
                        val cacheFile = java.io.File(cacheDir, "ext_view_${System.currentTimeMillis()}.pdf")
                        val ok = runCatching {
                            contentResolver.openInputStream(uri)?.use { input ->
                                java.io.FileOutputStream(cacheFile).use { output -> input.copyTo(output) }
                            }
                            true
                        }.getOrDefault(false)
                        val finalUri = if (ok && cacheFile.exists()) Uri.fromFile(cacheFile) else uri
                        viewModel.openExternalPdf(finalUri, title)
                        viewModel.importPdf(uri)
                    } else if (type.startsWith("image/")) {
                        viewModel.importImages(listOf(uri))
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        // Re-lock after a real absence, but not for quick trips to the photo picker, camera or share sheet.
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > LOCK_TIMEOUT_MS) {
            sessionUnlocked = false
        }
    }

    private fun startCompatibilityScanner() {
        compatibilityScanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { error ->
                viewModel.showMessage(error.message ?: "Compatibility scanner is unavailable on this device")
            }
    }

    private fun authenticate(onSuccess: () -> Unit) {
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }

        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            viewModel.showMessage("Set up a device biometric before enabling app lock")
            return
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) viewModel.showMessage(errString.toString())
                }
            }
        )

        val infoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock ScanPro")
            .setSubtitle("Authenticate to access your private document library")
            .setAllowedAuthenticators(authenticators)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) infoBuilder.setNegativeButtonText("Cancel")
        prompt.authenticate(infoBuilder.build())
    }

    private companion object {
        const val LOCK_TIMEOUT_MS = 30_000L
    }
}
