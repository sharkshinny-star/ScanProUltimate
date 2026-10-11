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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OcrEngine @Inject constructor(@ApplicationContext private val context: Context) {
    private val recognizers: Map<OcrScript, TextRecognizer> by lazy {
        mapOf(
            OcrScript.LATIN to TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS),
            OcrScript.DEVANAGARI to TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()),
            OcrScript.CHINESE to TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build()),
            OcrScript.JAPANESE to TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build()),
            OcrScript.KOREAN to TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        )
    }

    suspend fun recognize(file: File, script: OcrScript = OcrScript.LATIN): String {
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        val recognizer = recognizers.getValue(script)
        return recognizer.process(image).await().text.trim()
    }
}
