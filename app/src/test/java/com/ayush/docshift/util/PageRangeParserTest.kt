package com.ayush.docshift.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageRangeParserTest {
    @Test
    fun parsesValidRangesAndWhitespace() {
        assertEquals(1..4, PageRangeParser.parse("1-4"))
        assertEquals(2..6, PageRangeParser.parse("  2 - 6  "))
        assertEquals(3..3, PageRangeParser.parse("3-3"))
    }

    @Test
    fun rejectsMalformedRanges() {
        listOf("", "1", "1-2-3", "a-3", "1-", "-3", "1 - x").forEach {
            assertNull("Expected invalid range: '$it'", PageRangeParser.parse(it))
        }
    }

    @Test
    fun rejectsZeroReversedAndOverflowingRanges() {
        listOf("0-2", "1-0", "5-2", "-1-2", "1-999999999999999999999").forEach {
            assertNull("Expected invalid range: '$it'", PageRangeParser.parse(it))
        }
    }

    @Test
    fun validatesRangeAgainstPdfPageCount() {
        assertTrue(PageRangeParser.isWithinPageCount(1..4, 4))
        assertTrue(PageRangeParser.isWithinPageCount(2..3, 5))
        assertFalse(PageRangeParser.isWithinPageCount(1..5, 4))
        assertFalse(PageRangeParser.isWithinPageCount(0..2, 4))
        assertFalse(PageRangeParser.isWithinPageCount(3..2, 4))
        assertFalse(PageRangeParser.isWithinPageCount(1..1, 0))
    }
}
