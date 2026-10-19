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
            for (chunk in chunkAtBoundaries(text, MAX_CHUNK)) {
                translatedChunks.add(translator.translate(chunk).await())
            }
            translatedChunks.joinToString("")
        } finally {
            translator.close()
        }
    }

    private const val MAX_CHUNK = 30_000

    /**
     * Splits text into pieces of at most [max] characters at line, sentence or word boundaries, keeping
     * every separator so the pieces join back exactly. Cutting at fixed offsets split words and sentences
     * (and could split a surrogate pair), which garbled the translation at each boundary.
     */
    internal fun chunkAtBoundaries(text: String, max: Int): List<String> {
        if (text.length <= max) return listOf(text)
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = minOf(text.length, start + max)
            if (end < text.length) {
                val window = text.substring(start, end)
                val cut = listOf(window.lastIndexOf('\n'), window.lastIndexOfAny(charArrayOf('.', '!', '?')), window.lastIndexOf(' '))
                    .firstOrNull { it > max / 2 }
                end = if (cut != null) start + cut + 1 else end
                if (end < text.length && Character.isHighSurrogate(text[end - 1])) end--
            }
            chunks += text.substring(start, end)
            start = end
        }
        return chunks
    }
}
