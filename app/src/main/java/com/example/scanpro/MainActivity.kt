package com.example.scanpro

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.scanpro.core.crash.CrashReportPrompt
import com.example.scanpro.core.designsystem.ScanProTheme
import com.example.scanpro.core.designsystem.ThemeTransition
import com.example.scanpro.core.notifications.AppNotifications
import com.example.scanpro.feature.document.DocumentEditor
import com.example.scanpro.core.ui.SupportTopic
import com.example.scanpro.core.ui.contactSupport
import com.example.scanpro.core.ui.openPlayStoreListing
import com.example.scanpro.data.AccentPalette
import com.example.scanpro.data.ThemeMode
import com.example.scanpro.navigation.ScanProDestinations
import com.example.scanpro.scanner.HardwareShutter
import com.example.scanpro.ui.ScanProApp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels()
    @Inject lateinit var notifications: AppNotifications
    @Inject lateinit var documentEditor: DocumentEditor
    @Inject lateinit var folderLock: com.example.scanpro.core.security.FolderLock

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

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.example.scanpro.core.text.AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The splash library needs its own Theme.SplashScreen on Android 11 and older and crashes without it;
        // the activity uses the app theme, so there the first frame is held back instead (same effect).
        val splash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) installSplashScreen() else null
        super.onCreate(savedInstanceState)
        // A recreation (rotation, language or theme change) has no launch screen to finish: its state is already
        // loaded, so it must not be held back for the launch animation either.
        val freshLaunch = savedInstanceState == null
        if (splash != null) showLaunchScreenUntilReady(splash, freshLaunch) else holdFirstFrameUntilReady()
        // Edits that were still waiting when the app was last stopped run now, once per process.
        documentEditor.resumeInterrupted()
        // A recreated activity (rotation, theme change) still holds the original intent; only act on it once.
        // Reopening from Recents after backing out redelivers the intent the task started with (a shared PDF, a
        // shortcut); every action here is one-shot, so it is not replayed then.
        if (savedInstanceState == null && !launchedFromHistory(intent)) handleIntent(intent)
        enableEdgeToEdge()
        // The app draws behind both system bars; screens pad their own content with the insets. The
        // bars themselves stay available (hiding them took three-button navigation away from people).
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // No translucent scrim behind the gesture bar; the status bar never had one on these versions.
            window.isNavigationBarContrastEnforced = false
        }
        publishShortcuts()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // With app lock on, keep documents out of screenshots and the recent-apps preview.
                // Also while a locked folder is open this session, so its documents stay out of screenshots too.
                kotlinx.coroutines.flow.combine(viewModel.appLockEnabled, folderLock.locked, folderLock.hidden) { appLocked, locked, hidden ->
                    appLocked != false || locked.any { it !in hidden }
                }.collect { secure ->
                    if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        setContent {
          androidx.compose.runtime.CompositionLocalProvider(
            com.example.scanpro.core.security.LocalDeviceUnlock provides { onSuccess -> authenticate(onSuccess = onSuccess) }
          ) {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
            val savedPalette by viewModel.accentPalette.collectAsStateWithLifecycle()
            val sessionUnlocked by viewModel.sessionUnlocked.collectAsStateWithLifecycle()
            // Nothing is drawn until the saved theme has loaded (see holdLaunchScreenUntilReady).
            val mode = themeMode ?: return@CompositionLocalProvider
            val palette = savedPalette ?: return@CompositionLocalProvider
            val targetDark = when (mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            // A change of light/dark, colour theme or wallpaper colours crossfades rather than snapping.
            ThemeTransition(AppTheme(targetDark, palette, dynamicColor)) { theme ->
                val dark = theme.dark
                // Status and navigation bar icons follow the app's theme, not the system's.
                DisposableEffect(dark) {
                    // Explicit light/dark styles: with SystemBarStyle.auto, enableEdgeToEdge turns the navigation-bar
                    // contrast scrim back on, which draws a near-white band under the app's bottom bar on
                    // three-button navigation.
                    val transparent = android.graphics.Color.TRANSPARENT
                    val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                    enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
                    onDispose { }
                }
                ScanProTheme(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColor = theme.dynamicColor, palette = theme.palette) {
                    CrashReportPrompt()
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
        }

    /**
     * Keeps the launch screen up until the saved theme, lock and welcome settings have loaded (normally a few
     * milliseconds, never longer than [LAUNCH_SCREEN_MAX_HOLD_MS]), so nothing flashes in the wrong theme.
     * On a fresh launch the animated icon is shown in full first. It then fades and grows away instead of
     * cutting to the app.
     */
    private fun showLaunchScreenUntilReady(splash: androidx.core.splashscreen.SplashScreen, freshLaunch: Boolean) {
        val startedAt = SystemClock.uptimeMillis()
        // The animated icon (ic_launcher_splash) plays once in full, unless the user turned animations off.
        val animationsOn = android.provider.Settings.Global.getFloat(
            contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) > 0f
        val minimumMs = if (animationsOn && freshLaunch) LAUNCH_ANIMATION_MS else 0L
        splash.setKeepOnScreenCondition {
            val elapsed = SystemClock.uptimeMillis() - startedAt
            val ready = viewModel.launchStateReady
            (!ready && elapsed < LAUNCH_SCREEN_MAX_HOLD_MS) || elapsed < minimumMs
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

    /** Android 11 and older: don't draw until the saved theme, lock and welcome settings are known (or the hold times out). */
    private fun holdFirstFrameUntilReady() {
        val startedAt = SystemClock.uptimeMillis()
        val content = findViewById<android.view.View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val ready = viewModel.launchStateReady ||
                    SystemClock.uptimeMillis() - startedAt >= LAUNCH_SCREEN_MAX_HOLD_MS
                if (ready) content.viewTreeObserver.removeOnPreDrawListener(this)
                return ready
            }
        })
    }

    private fun launchedFromHistory(intent: Intent?): Boolean =
        intent != null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** Either volume key takes a photo while the scanner is up, for one-handed batch scanning. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val volume = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        return (volume && event?.repeatCount == 0 && HardwareShutter.onVolumeKey()) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val volume = keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        return (volume && HardwareShutter.active) || super.onKeyUp(keyCode, event)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        // Queued imports outlive this Activity (they wait in the ViewModel until unlock), so capture
        // the ViewModel itself rather than `this`, which would leak a rotated-away Activity.
        val appViewModel = viewModel
        val action = intent.action
        // Senders don't always set a type; the provider usually knows it.
        val type = intent.type ?: intent.data?.let { runCatching { contentResolver.getType(it) }.getOrNull() }.orEmpty()
        fun typeOf(uri: Uri): String = runCatching { contentResolver.getType(uri) }.getOrNull() ?: type

        when (action) {
            ACTION_OPEN_DOCUMENT -> intent.getStringExtra(EXTRA_DOCUMENT_ID)?.let(viewModel::requestOpenDocument)
            ACTION_OPEN_TRASH -> viewModel.requestDestination(ScanProDestinations.TRASH)
            ACTION_OPEN_SETTINGS -> viewModel.requestDestination(ScanProDestinations.SETTINGS)
            ACTION_OPEN_KEPT_SCANS -> viewModel.requestDestination(ScanProDestinations.KEPT_SCANS)
            ACTION_SCAN_NOW -> {
                // Launchers rank shortcuts by how often each is used.
                ShortcutManagerCompat.reportShortcutUsed(this, SHORTCUT_SCAN_NOW)
                viewModel.requestScan()
            }
            ACTION_REPORT_ISSUE -> {
                ShortcutManagerCompat.reportShortcutUsed(this, SHORTCUT_REPORT_ISSUE)
                contactSupport(this, SupportTopic.ISSUE)
            }
            ACTION_GIVE_FEEDBACK -> {
                ShortcutManagerCompat.reportShortcutUsed(this, SHORTCUT_GIVE_FEEDBACK)
                contactSupport(this, SupportTopic.FEEDBACK)
            }
            // Shortcuts pinned from an earlier build still carry the old single support action.
            ACTION_SUPPORT -> openPlayStoreListing(this)
            Intent.ACTION_SEND -> {
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (uri != null && isAllowedExternalUri(uri)) {
                    if (type == "application/pdf") {
                        appViewModel.submitExternalImport { appViewModel.importPdf(uri) }
                    } else if (type.startsWith("image/")) {
                        appViewModel.submitExternalImport { appViewModel.importImages(listOf(uri)) }
                    }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
                }.filter(::isAllowedExternalUri)
                // Several PDFs arrive as one document each; images become the pages of one document.
                val pdfs = uris.filter { typeOf(it) == "application/pdf" || it.toString().endsWith(".pdf", ignoreCase = true) }
                val images = uris.filter { it !in pdfs && typeOf(it).startsWith("image/") }
                pdfs.forEach { pdf -> appViewModel.submitExternalImport { appViewModel.importPdf(pdf) } }
                if (images.isNotEmpty()) {
                    appViewModel.submitExternalImport { appViewModel.importImages(images) }
                }
            }
            Intent.ACTION_VIEW -> {
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
     * Launcher shortcuts, published at runtime: a static shortcuts.xml cannot name the package, which differs
     * between the debug and release builds, and each intent here is one this activity really handles.
     */
    private fun publishShortcuts() {
        runCatching {
            fun shortcut(id: String, short: Int, long: Int, action: String) = ShortcutInfoCompat.Builder(this, id)
                .setShortLabel(getString(short))
                .setLongLabel(getString(long))
                .setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher))
                .setIntent(Intent(this, MainActivity::class.java).setAction(action))
                .build()
            ShortcutManagerCompat.setDynamicShortcuts(
                this,
                listOf(
                    shortcut(SHORTCUT_SCAN_NOW, R.string.shortcut_scan_short, R.string.shortcut_scan_long, ACTION_SCAN_NOW),
                    shortcut(SHORTCUT_REPORT_ISSUE, R.string.shortcut_bugs_short, R.string.shortcut_bugs_long, ACTION_REPORT_ISSUE),
                    shortcut(SHORTCUT_GIVE_FEEDBACK, R.string.shortcut_dont_remove_short, R.string.shortcut_dont_remove_long, ACTION_GIVE_FEEDBACK)
                )
            )
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppStopped(SystemClock.elapsedRealtime())
        notifications.appVisible = false
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppStarted(SystemClock.elapsedRealtime(), LOCK_TIMEOUT_MS)
        // Back in the app: finished and failed jobs speak through the snackbar again, and old notices are stale.
        notifications.appVisible = true
        notifications.clearTaskNotices()
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

    companion object {
        private const val LOCK_TIMEOUT_MS = 30_000L
        private const val LAUNCH_SCREEN_MAX_HOLD_MS = 1_500L
        /** Length of the animated launch icon; matches windowSplashScreenAnimationDuration in themes.xml. */
        private const val LAUNCH_ANIMATION_MS = 1000L
        private const val ACTION_SCAN_NOW = "com.example.scanpro.action.SCAN_NOW"
        /** From notifications: open a document ([EXTRA_DOCUMENT_ID]), the recycle bin or settings, after unlock. */
        const val ACTION_OPEN_DOCUMENT = "com.example.scanpro.action.OPEN_DOCUMENT"
        const val ACTION_OPEN_TRASH = "com.example.scanpro.action.OPEN_TRASH"
        const val ACTION_OPEN_SETTINGS = "com.example.scanpro.action.OPEN_SETTINGS"
        const val ACTION_OPEN_KEPT_SCANS = "com.example.scanpro.action.OPEN_KEPT_SCANS"
        const val EXTRA_DOCUMENT_ID = "documentId"
        // Shortcut ids are stored by launchers (pinned shortcuts); never rename them.
        private const val SHORTCUT_SCAN_NOW = "scan_now"
        private const val SHORTCUT_REPORT_ISSUE = "bugs_or_issues"
        private const val SHORTCUT_GIVE_FEEDBACK = "dont_remove_app"
        private const val ACTION_REPORT_ISSUE = "com.example.scanpro.action.REPORT_ISSUE"
        private const val ACTION_GIVE_FEEDBACK = "com.example.scanpro.action.GIVE_FEEDBACK"
        /** Superseded by the two actions above; still handled for shortcuts pinned before they existed. */
        private const val ACTION_SUPPORT = "com.example.scanpro.action.SUPPORT"
    }
}

/** Everything that decides the app's colours; a change to any of it crossfades the screen. */
private data class AppTheme(val dark: Boolean, val palette: AccentPalette, val dynamicColor: Boolean)
