package com.example.scanpro.imaging

import android.content.Context
import android.net.Uri
import com.example.scanpro.data.OcrScript
import com.example.scanpro.data.OcrWord
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class OcrResult(val text: String, val words: List<OcrWord>)

@Singleton
class OcrEngine @Inject constructor(@ApplicationContext private val context: Context) {
    // Each recognizer loads its own model, so create one only when its script is first used
    // instead of loading all five on the first OCR call.
    private val recognizers = ConcurrentHashMap<OcrScript, TextRecognizer>()

    private fun recognizerFor(script: OcrScript): TextRecognizer = recognizers.computeIfAbsent(script) {
        when (it) {
            OcrScript.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            OcrScript.DEVANAGARI -> TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
            OcrScript.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            OcrScript.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            OcrScript.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        }
    }

    /** Scripts whose model Play services has confirmed is on the device; checked once per run. */
    private val readyScripts = ConcurrentHashMap.newKeySet<OcrScript>()

    /**
     * The recognition models live in Google Play services. Latin is fetched at install time; the other scripts
     * are requested here the first time they are needed, and recognition waits (up to a minute) for them.
     */
    private suspend fun ensureModel(script: OcrScript, recognizer: TextRecognizer) {
        if (script in readyScripts) return
        val modules = ModuleInstall.getClient(context)
        val available = runCatching { modules.areModulesAvailable(recognizer).await().areModulesAvailable() }.getOrDefault(true)
        if (!available) {
            runCatching { modules.installModules(ModuleInstallRequest.newBuilder().addApi(recognizer).build()).await() }
            val ready = withTimeoutOrNull(MODEL_WAIT_MS) {
                while (!runCatching { modules.areModulesAvailable(recognizer).await().areModulesAvailable() }.getOrDefault(false)) {
                    delay(1_000)
                }
                true
            } ?: false
            if (!ready) {
                throw IllegalStateException(
                    "The ${script.label} text model is still downloading. Connect to the internet, then run OCR again."
                )
            }
        }
        readyScripts += script
    }

    /** Makes sure [script]'s model is on the device, downloading it if needed; throws a readable error otherwise. */
    suspend fun prepare(script: OcrScript) = ensureModel(script, recognizerFor(script))

    suspend fun recognize(file: File, script: OcrScript = OcrScript.LATIN): String = recognizeDetailed(file, script).text

    /** Recognised text plus the position of every word, so a PDF text layer can sit under the printed words. */
    suspend fun recognizeDetailed(file: File, script: OcrScript = OcrScript.LATIN): OcrResult {
        // Rendered pages are already upright. Decoding at a bounded size keeps body text well above
        // ML Kit's minimum glyph height while avoiding a full 3400px decode per page.
        val bitmap = ImageProcessor.decodeScaled(file.absolutePath, OCR_MAX_DIMENSION)
        val image = bitmap?.let { InputImage.fromBitmap(it, 0) }
            ?: InputImage.fromFilePath(context, Uri.fromFile(file))
        val recognizer = recognizerFor(script)
        try {
            ensureModel(script, recognizer)
        } catch (error: Throwable) {
            bitmap?.recycle()
            throw error
        }
        val task = recognizer.process(image)
        // Recycle when ML Kit is done, not when this coroutine ends: a cancelled await would otherwise
        // free the pixels while recognition is still reading them.
        if (bitmap != null) task.addOnCompleteListener { bitmap.recycle() }
        val result = task.await()
        val width = (bitmap?.width ?: 0).toFloat()
        val height = (bitmap?.height ?: 0).toFloat()
        val words = if (width <= 0f || height <= 0f) emptyList() else buildList {
            result.textBlocks.forEach { block ->
                block.lines.forEach { line ->
                    line.elements.forEach { element ->
                        val box = element.boundingBox ?: return@forEach
                        if (element.text.isBlank()) return@forEach
                        add(
                            OcrWord(
                                element.text,
                                (box.left / width).coerceIn(0f, 1f),
                                (box.top / height).coerceIn(0f, 1f),
                                (box.right / width).coerceIn(0f, 1f),
                                (box.bottom / height).coerceIn(0f, 1f)
                            )
                        )
                    }
                }
            }
        }
        return OcrResult(result.text.trim(), words)
    }

    private companion object {
        const val OCR_MAX_DIMENSION = 2400
        const val MODEL_WAIT_MS = 60_000L
    }
}
