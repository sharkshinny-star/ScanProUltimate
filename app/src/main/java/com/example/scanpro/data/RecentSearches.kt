package com.example.scanpro.data

/** The search terms kept for the Search screen (R35): newest first, no repeats, at most [LIMIT]. */
object RecentSearches {
    const val LIMIT = 8

    /** [list] with [term] added at the front (an earlier copy, ignoring case and spaces, removed), capped at [LIMIT]. */
    fun add(list: List<String>, term: String): List<String> {
        val clean = term.trim()
        if (clean.length < 2) return list
        return (listOf(clean) + list.filterNot { it.trim().equals(clean, ignoreCase = true) }).take(LIMIT)
    }
}
