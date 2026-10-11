package com.example.scanpro.imaging

object SmartTitle {
    fun fromOcr(text: String, fallback: String): String {
        val lines = text.lineSequence()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.length in 3..80 }
            .filterNot { it.matches(Regex("(?i)^page\\s+\\d+.*")) }
            .take(8)
            .toList()

        val first = lines.firstOrNull() ?: return fallback
        val lower = text.lowercase()
        val prefix = when {
            "invoice" in lower -> "Invoice"
            "receipt" in lower -> "Receipt"
            "resume" in lower || "curriculum vitae" in lower -> "Resume"
            "contract" in lower || "agreement" in lower -> "Contract"
            "statement" in lower -> "Statement"
            else -> null
        }

        val result = when {
            prefix == null -> first
            first.contains(prefix, ignoreCase = true) -> first
            else -> "$prefix - $first"
        }
        return result.take(72).trim().ifBlank { fallback }
    }
}
