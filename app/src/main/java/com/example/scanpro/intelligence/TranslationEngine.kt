package com.example.scanpro.intelligence

/** Translates OCR text on the device. The implementation ships in the on-demand `translate` module. */
interface Translator {
    suspend fun translate(text: String, targetTag: String): String
}

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

    const val MAX_CHUNK = 30_000

    /**
     * Splits text into pieces of at most [max] characters at line, sentence or word boundaries, keeping
     * every separator so the pieces join back exactly. Cutting at fixed offsets split words and sentences
     * (and could split a surrogate pair), which garbled the translation at each boundary.
     */
    fun chunkAtBoundaries(text: String, max: Int): List<String> {
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
