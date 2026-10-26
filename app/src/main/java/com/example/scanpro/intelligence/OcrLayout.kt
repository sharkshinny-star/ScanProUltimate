package com.example.scanpro.intelligence

import com.example.scanpro.data.OcrWord
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A line of recognised words, left to right, with the box around them (fractions of the page). */
data class OcrLine(val words: List<OcrWord>) {
    val left: Float get() = words.minOf { it.left }
    val top: Float get() = words.minOf { it.top }
    val right: Float get() = words.maxOf { it.right }
    val bottom: Float get() = words.maxOf { it.bottom }
    val text: String get() = words.joinToString(" ") { it.text }
}

/** Rebuilds lines and tables from where OCR found each word on the page. */
object OcrLayout {
    /** Groups words into lines: a word joins a line when the two overlap for most of the shorter one's height. */
    fun lines(words: List<OcrWord>): List<OcrLine> {
        val groups = mutableListOf<MutableList<OcrWord>>()
        words.filter { it.text.isNotBlank() && it.bottom > it.top }
            .sortedBy { (it.top + it.bottom) / 2f }
            .forEach { word ->
                val line = groups.lastOrNull { group ->
                    val top = group.minOf { it.top }
                    val bottom = group.maxOf { it.bottom }
                    val overlap = min(bottom, word.bottom) - max(top, word.top)
                    overlap >= 0.5f * min(bottom - top, word.bottom - word.top)
                }
                if (line != null) line += word else groups += mutableListOf(word)
            }
        return groups.map { group -> OcrLine(group.sortedBy { it.left }) }.sortedBy { it.top }
    }

    /**
     * Rebuilds a table: each line is cut into cells where the gap between words is much wider than a space, and
     * the cells are lined up into columns from where cells sit across the whole page. Text that isn't a table
     * comes out as one column, a row per line.
     */
    fun table(words: List<OcrWord>): List<List<String>> {
        val lines = lines(words)
        if (lines.isEmpty()) return emptyList()
        val charWidth = words.filter { it.text.isNotEmpty() }
            .map { (it.right - it.left) / it.text.length }
            .sorted()
            .let { it[it.size / 2] }
            .coerceAtLeast(0.002f)
        val gap = charWidth * 2.2f
        val rows = lines.map { line ->
            val cells = mutableListOf(mutableListOf(line.words.first()))
            line.words.zipWithNext { a, b -> if (b.left - a.right > gap) cells += mutableListOf(b) else cells.last() += b }
            cells.map { OcrLine(it) }
        }

        // Columns: the spans covered by cells of rows that have more than one cell, overlapping spans merged.
        val spans = rows.filter { it.size > 1 }.flatten().map { it.left to it.right }.sortedBy { it.first }
        if (spans.isEmpty()) return lines.map { listOf(it.text) }
        val columns = mutableListOf<Pair<Float, Float>>()
        spans.forEach { (left, right) ->
            val last = columns.lastOrNull()
            if (last != null && left <= last.second + charWidth * 0.5f) columns[columns.lastIndex] = last.first to max(last.second, right)
            else columns += left to right
        }

        return rows.map { cells ->
            val row = MutableList(columns.size) { "" }
            cells.forEach { cell ->
                val column = columns.indices.maxByOrNull { index ->
                    val (left, right) = columns[index]
                    val overlap = min(right, cell.right) - max(left, cell.left)
                    // No overlap anywhere: the nearest column wins.
                    if (overlap > 0f) overlap else -abs((left + right) / 2f - (cell.left + cell.right) / 2f) - 1f
                } ?: 0
                row[column] = if (row[column].isEmpty()) cell.text else row[column] + " " + cell.text
            }
            row
        }
    }
}
