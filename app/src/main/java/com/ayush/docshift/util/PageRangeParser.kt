package com.ayush.docshift.util

/**
 * Parses a user-entered, one-based inclusive page range such as "1-4".
 * Returns null for malformed, overflowing, zero/negative, or reversed ranges.
 */
object PageRangeParser {
    private val RANGE_PATTERN = Regex("""^\s*(\d+)\s*-\s*(\d+)\s*$""")

    fun parse(input: String): IntRange? {
        val match = RANGE_PATTERN.matchEntire(input) ?: return null
        val first = match.groupValues[1].toIntOrNull() ?: return null
        val last = match.groupValues[2].toIntOrNull() ?: return null
        return if (first > 0 && last >= first) first..last else null
    }

    fun isWithinPageCount(range: IntRange, pageCount: Int): Boolean =
        pageCount > 0 && range.first >= 1 && range.last >= range.first && range.last <= pageCount
}
