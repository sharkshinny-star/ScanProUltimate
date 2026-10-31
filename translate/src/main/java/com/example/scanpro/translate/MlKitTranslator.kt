package com.example.scanpro.translate

import com.example.scanpro.intelligence.TranslationEngine
import com.example.scanpro.intelligence.Translator
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import com.example.scanpro.R
import com.example.scanpro.core.text.userError
import com.example.scanpro.core.text.userRequire

/**
 * The on-device ML Kit translator. Lives in the on-demand `translate` module and is created by name from
 * [com.example.scanpro.intelligence.TranslationFeature], so keep the class name and the no-argument
 * constructor (both are also pinned in the app's ProGuard rules).
 */
@Suppress("unused")
class MlKitTranslator : Translator {

    override suspend fun translate(text: String, targetTag: String): String {
        userRequire(text.isNotBlank(), R.string.err_translation_no_text)
        val detected = LanguageIdentification.getClient().use { identifier ->
            identifier.identifyLanguage(text.take(12000)).await()
        }
        userRequire(detected != "und", R.string.err_translation_unknown_language)

        val source = TranslateLanguage.fromLanguageTag(detected)
            ?: userError(R.string.err_translation_language_unsupported, detected)
        val target = TranslateLanguage.fromLanguageTag(targetTag)
            ?: userError(R.string.err_translation_target_unsupported)
        if (source == target) return text

        return Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(target)
                .build()
        ).use { translator ->
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            // Translate in chunks so long documents are not silently truncated.
            val translatedChunks = mutableListOf<String>()
            for (chunk in TranslationEngine.chunkAtBoundaries(text, TranslationEngine.MAX_CHUNK)) {
                translatedChunks.add(translator.translate(chunk).await())
            }
            translatedChunks.joinToString("")
        }
    }
}
