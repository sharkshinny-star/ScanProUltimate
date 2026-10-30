package com.example.scanpro.intelligence

import android.content.Context
import com.google.android.play.core.splitinstall.SplitInstallException
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Gets the translator from the on-demand `translate` module, fetching the module from Google Play the
 * first time. ML Kit Translate is about 16 MB of native code per CPU type that most people never use,
 * so it is not part of the base install.
 */
object TranslationFeature {
    const val MODULE = "translate"
    private const val IMPLEMENTATION = "com.example.scanpro.translate.MlKitTranslator"

    sealed interface Progress {
        /** Asking Play for the module. */
        data object Preparing : Progress
        data class Downloading(val percent: Int) : Progress
        data object Installing : Progress
    }

    /**
     * The translator, after installing its module if needed. [onProgress] reports the download so the
     * screen can say what is happening; it is called on the main thread.
     */
    suspend fun translator(context: Context, onProgress: (Progress) -> Unit = {}): Translator {
        // Already present: sideload builds compile it in, and Play builds have it once the module is installed.
        load()?.let { return it }
        val manager = SplitInstallManagerFactory.create(context.applicationContext)
        install(manager, onProgress)
        return load() ?: throw IllegalStateException("Translation is installed but could not start. Please restart the app.")
    }

    private fun load(): Translator? = try {
        Class.forName(IMPLEMENTATION).getDeclaredConstructor().newInstance() as Translator
    } catch (_: ReflectiveOperationException) {
        null
    }

    private suspend fun install(manager: SplitInstallManager, onProgress: (Progress) -> Unit) {
        onProgress(Progress.Preparing)
        suspendCancellableCoroutine { continuation ->
            var sessionId = 0
            lateinit var listener: SplitInstallStateUpdatedListener
            fun finish(block: () -> Unit) {
                manager.unregisterListener(listener)
                if (continuation.isActive) block()
            }
            listener = SplitInstallStateUpdatedListener { state ->
                if (sessionId != 0 && state.sessionId() != sessionId) return@SplitInstallStateUpdatedListener
                when (state.status()) {
                    SplitInstallSessionStatus.DOWNLOADING -> {
                        val total = state.totalBytesToDownload()
                        val percent = if (total > 0) (state.bytesDownloaded() * 100 / total).toInt() else 0
                        onProgress(Progress.Downloading(percent.coerceIn(0, 100)))
                    }
                    SplitInstallSessionStatus.DOWNLOADED, SplitInstallSessionStatus.INSTALLING -> onProgress(Progress.Installing)
                    SplitInstallSessionStatus.INSTALLED -> finish { continuation.resume(Unit) }
                    SplitInstallSessionStatus.FAILED -> finish { continuation.resumeWithException(IllegalStateException(describe(state.errorCode()))) }
                    SplitInstallSessionStatus.CANCELED -> finish { continuation.resumeWithException(CancellationException("Translation download cancelled")) }
                    // Play asks for confirmation only for very large modules over mobile data; this one is small,
                    // but if it ever happens the user has to confirm from Play's own dialog, which needs an Activity.
                    SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION ->
                        finish { continuation.resumeWithException(IllegalStateException("Google Play needs your confirmation to download translation. Please try again on Wi-Fi.")) }
                    else -> Unit
                }
            }
            manager.registerListener(listener)
            manager.startInstall(SplitInstallRequest.newBuilder().addModule(MODULE).build())
                .addOnSuccessListener { id -> sessionId = id }
                .addOnFailureListener { error ->
                    val code = (error as? SplitInstallException)?.errorCode ?: SplitInstallErrorCode.INTERNAL_ERROR
                    finish { continuation.resumeWithException(IllegalStateException(describe(code), error)) }
                }
            continuation.invokeOnCancellation {
                manager.unregisterListener(listener)
                if (sessionId != 0) manager.cancelInstall(sessionId)
            }
        }
    }

    private fun describe(@SplitInstallErrorCode code: Int): String = when (code) {
        SplitInstallErrorCode.NETWORK_ERROR -> "Translation could not be downloaded. Check your connection and try again."
        SplitInstallErrorCode.INSUFFICIENT_STORAGE -> "Not enough storage to add translation."
        SplitInstallErrorCode.API_NOT_AVAILABLE, SplitInstallErrorCode.MODULE_UNAVAILABLE, SplitInstallErrorCode.PLAY_STORE_NOT_FOUND ->
            "Translation is added through Google Play, which is not available on this device or for this install."
        SplitInstallErrorCode.ACCESS_DENIED -> "Google Play did not allow the translation download."
        SplitInstallErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED, SplitInstallErrorCode.INCOMPATIBLE_WITH_EXISTING_SESSION ->
            "Another download is in progress. Please try again in a moment."
        else -> "Translation could not be added (Google Play error $code)."
    }
}
