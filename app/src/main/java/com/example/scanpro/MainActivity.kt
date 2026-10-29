package com.example.scanpro

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import com.example.scanpro.core.designsystem.ThemeTransition
import com.example.scanpro.data.ThemeMode
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import android.content.ContentResolver
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.scanpro.ui.ScanProApp
import com.example.scanpro.core.designsystem.ScanProTheme
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dagger.hilt.android.AndroidEntryPoint

import android.net.Uri
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()

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
            viewModel.showMessage(getString(R.string.main_compat_scanner_no_pages))
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
        // The splash library needs its own Theme.SplashScreen on Android 11 and older and crashes without it;
        // the activity uses the app theme, so there the first frame is held back instead (same effect).
        val splash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) installSplashScreen() else null
        super.onCreate(savedInstanceState)
        if (splash != null) showLaunchScreenUntilReady(splash) else holdFirstFrameUntilReady()
        // A recreated activity (rotation, theme change) still holds the original intent; only act on it once.
        if (savedInstanceState == null) handleIntent(intent)
        enableEdgeToEdge()
        hideNavigationBar()
        publishScanShortcut()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // With app lock on, keep documents out of screenshots and the recent-apps preview.
                viewModel.appLockEnabled.collect { locked ->
                    if (locked != false) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val sessionUnlocked by viewModel.sessionUnlocked.collectAsStateWithLifecycle()
            // Nothing is drawn until the saved theme has loaded (see holdLaunchScreenUntilReady).
            val mode = themeMode ?: return@setContent
            val targetDark = when (mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            ThemeTransition(targetDark) { dark ->
                // Status and navigation bar icons follow the app's theme, not the system's.
                DisposableEffect(dark) {
                    val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    onDispose { }
                }
                ScanProTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                    ScanProApp(
                        viewModel = viewModel,
                        sessionUnlocked = sessionUnlocked,
                        onFallbackScanner = ::startCompatibilityScanner,
                        onImportImages = {
                            imageImportLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onUnlock = {
                            authenticate(
                                // The screen lock was removed after app lock was turned on. Only the device
                                // owner can remove it, so let them in and switch app lock off rather than
                                // locking them out of their documents for good.
                                onNoScreenLock = {
                                    viewModel.setAppLock(false)
                                    viewModel.markUnlocked()
                                    viewModel.showMessage(getString(R.string.main_screen_lock_removed))
                                }
                            ) { viewModel.markUnlocked() }
                        },
                        onAppLockChange = { enabled ->
                            // Turning the lock off needs authentication too, or anyone holding the phone could disable it.
                            authenticate {
                                viewModel.setAppLock(enabled)
                                viewModel.markUnlocked()
                            }
                        }
                    )
                }
            }
        }
    }

    /**
     * Keeps the launch screen (always light) up until the saved theme and app-lock setting have
     * loaded, so the first frame is already in the right theme instead of flashing light, then dark.
     */
    /**
     * Keeps the launch screen up until the saved theme and lock setting have loaded (normally a few
     * milliseconds, never longer than [LAUNCH_SCREEN_MAX_HOLD_MS]), so nothing flashes in the wrong theme.
     * It then fades and grows away instead of cutting to the app.
     */
    private fun showLaunchScreenUntilReady(splash: androidx.core.splashscreen.SplashScreen) {
        val startedAt = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition {
            val ready = viewModel.themeMode.value != null && viewModel.appLockEnabled.value != null
            !ready && SystemClock.uptimeMillis() - startedAt < LAUNCH_SCREEN_MAX_HOLD_MS
        }
        splash.setOnExitAnimationListener { provider ->
            provider.view.animate()
                .alpha(0f)
                .scaleX(1.12f)
                .scaleY(1.12f)
                .setDuration(260L)
                .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction { provider.remove() }
                .start()
        }
    }

    /** Android 11 and older: don't draw until the saved theme and lock setting are known (or the hold times out). */
    private fun holdFirstFrameUntilReady() {
        val startedAt = SystemClock.uptimeMillis()
        val content = findViewById<android.view.View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val ready = (viewModel.themeMode.value != null && viewModel.appLockEnabled.value != null) ||
                    SystemClock.uptimeMillis() - startedAt >= LAUNCH_SCREEN_MAX_HOLD_MS
                if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                return ready
            }
        })
    }

    private fun hideNavigationBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavigationBar()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent == null) return
        // Queued imports outlive this Activity (they wait in the ViewModel until unlock), so capture
        // the ViewModel itself rather than `this`, which would leak a rotated-away Activity.
        val appViewModel = viewModel
        val action = intent.action
        val type = intent.type.orEmpty()

        when {
            action == ACTION_SCAN_NOW -> viewModel.requestScan()
            // "Don't remove our app" / "Bugs or issues?" launcher shortcuts: both lead to the Play listing.
            action == ACTION_SUPPORT -> com.example.scanpro.core.ui.openPlayStoreListing(this)
            action == android.content.Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM)
                }
                if (uri != null && isAllowedExternalUri(uri)) {
                    if (type == "application/pdf") {
                        appViewModel.submitExternalImport { appViewModel.importPdf(uri) }
                    } else if (type.startsWith("image/")) {
                        appViewModel.submitExternalImport { appViewModel.importImages(listOf(uri)) }
                    }
                }
            }
            action == android.content.Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(android.content.Intent.EXTRA_STREAM).orEmpty()
                }.filter(::isAllowedExternalUri)
                if (uris.isNotEmpty()) {
                    appViewModel.submitExternalImport { appViewModel.importImages(uris) }
                }
            }
            action == android.content.Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null && isAllowedExternalUri(uri)) {
                    if (type == "application/pdf" || uri.toString().endsWith(".pdf", ignoreCase = true)) {
                        appViewModel.submitExternalImport { appViewModel.importPdf(uri) }
                    } else if (type.startsWith("image/")) {
                        appViewModel.submitExternalImport { appViewModel.importImages(listOf(uri)) }
                    }
                }
            }
        }
    }

    /**
     * Only content:// URIs from other apps are imported. file:// URIs and URIs served by ScanPro's own
     * providers are refused, so another app can't point the importer at ScanPro's private files.
     */
    private fun isAllowedExternalUri(uri: Uri): Boolean =
        uri.scheme == ContentResolver.SCHEME_CONTENT && uri.authority?.startsWith(packageName) != true

    /**
     * Launcher long-press shortcuts. Published at runtime rather than from res/xml: a static shortcut can't name
     * the package per build type (debug adds ".debug"), and a manifest shortcut's id can't be replaced by code.
     */
    private fun publishScanShortcut() {
        runCatching {
            fun shortcut(id: String, short: Int, long: Int, action: String) = ShortcutInfoCompat.Builder(this, id)
                .setShortLabel(getString(short))
                .setLongLabel(getString(long))
                .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
                .setIntent(android.content.Intent(this, MainActivity::class.java).setAction(action))
                .build()
            ShortcutManagerCompat.setDynamicShortcuts(
                this,
                listOf(
                    shortcut("scan_now", R.string.shortcut_scan_short, R.string.shortcut_scan_long, ACTION_SCAN_NOW),
                    shortcut("bugs_or_issues", R.string.shortcut_bugs_short, R.string.shortcut_bugs_long, ACTION_SUPPORT),
                    shortcut("dont_remove_app", R.string.shortcut_dont_remove_short, R.string.shortcut_dont_remove_long, ACTION_SUPPORT)
                )
            )
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppStopped(SystemClock.elapsedRealtime())
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppStarted(SystemClock.elapsedRealtime(), LOCK_TIMEOUT_MS)
    }

    private fun startCompatibilityScanner() {
        compatibilityScanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { error ->
                viewModel.showMessage(error.message ?: getString(R.string.main_compat_scanner_unavailable))
            }
    }

    /**
     * Shows the system prompt. The device PIN, pattern or password is always allowed as a fallback, so
     * removing a fingerprint can't lock anyone out. [onNoScreenLock] runs instead when the device has no
     * screen lock at all.
     */
    private fun authenticate(onNoScreenLock: (() -> Unit)? = null, onSuccess: () -> Unit) {
        val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            // BIOMETRIC_WEAK | DEVICE_CREDENTIAL is the combination supported before Android 11.
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }

        when (BiometricManager.from(this).canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                if (onNoScreenLock != null) onNoScreenLock()
                else viewModel.showMessage(getString(R.string.main_set_up_screen_lock))
                return
            }
            else -> {
                viewModel.showMessage(getString(R.string.main_device_auth_unavailable))
                return
            }
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
            .setTitle(getString(R.string.main_unlock_title))
            .setSubtitle(getString(R.string.main_unlock_subtitle))
            .setAllowedAuthenticators(authenticators)

        prompt.authenticate(infoBuilder.build())
    }

    private companion object {
        const val LOCK_TIMEOUT_MS = 30_000L
        const val LAUNCH_SCREEN_MAX_HOLD_MS = 1_500L
        const val ACTION_SCAN_NOW = "com.example.scanpro.action.SCAN_NOW"
        const val ACTION_SUPPORT = "com.example.scanpro.action.SUPPORT"
    }
}
