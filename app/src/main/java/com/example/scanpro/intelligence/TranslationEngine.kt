package com.example.scanpro.intelligence

import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await

object TranslationEngine {
    data class Target(val label: String, val languageTag: String)

    val commonTargets = listOf(
        Target("English", "en"),
        Target("Hindi", "hi"),
        Target("Arabic", "ar"),
        Target("French", "fr"),
        Target("Spanish", "es"),
        Target("German", "de")
    )

    suspend fun translate(text: String, targetTag: String): String {
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
            for (chunk in text.chunked(30000)) {
                translatedChunks.add(translator.translate(chunk).await())
            }
            translatedChunks.joinToString("")
        } finally {
            translator.close()
        }
    }
}
