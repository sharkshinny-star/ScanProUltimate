package com.example.scanpro.translate

import com.example.scanpro.intelligence.TranslationEngine
import com.example.scanpro.intelligence.Translator
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

/**
 * The on-device ML Kit translator. Lives in the on-demand `translate` module and is created by name from
 * [com.example.scanpro.intelligence.TranslationFeature], so keep the class name and the no-argument
 * constructor (both are also pinned in the app's ProGuard rules).
 */
@Suppress("unused")
class MlKitTranslator : Translator {

    override suspend fun translate(text: String, targetTag: String): String {
        require(text.isNotBlank()) { "No OCR text to translate" }
        val identifier = LanguageIdentification.getClient()
        val detected = try {
            identifier.identifyLanguage(text.take(12000)).await()
        } finally {
            identifier.close()
        }
        require(detected != "und") { "Could not identify the source language" }

        val source = TranslateLanguage.fromLanguageTag(detected)
            ?: error("Translation is not supported for detected language: $detected")
        val target = TranslateLanguage.fromLanguageTag(targetTag)
            ?: error("Unsupported target language")
        if (source == target) return text

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
        )
        return try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            // Translate in chunks so long documents are not silently truncated.
            val translatedChunks = mutableListOf<String>()
            for (chunk in TranslationEngine.chunkAtBoundaries(text, TranslationEngine.MAX_CHUNK)) {
                translatedChunks.add(translator.translate(chunk).await())
            }
            translatedChunks.joinToString("")
        } finally {
            translator.close()
        }
    }
}
