package com.example.scanpro.imaging

import android.content.Context
import android.net.Uri
import com.example.scanpro.data.OcrScript
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

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

    suspend fun recognize(file: File, script: OcrScript = OcrScript.LATIN): String {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        val recognizer = recognizerFor(script)
        return recognizer.process(image).await().text.trim()
    }
}
