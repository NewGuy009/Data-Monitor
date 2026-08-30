package com.example.mysecondapp.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class StockChartsTest {
    @Test
    fun `x axis formatter index rounds and clamps to available labels`() {
        assertEquals(0, axisLabelIndex(-2.0, 10))
        assertEquals(2, axisLabelIndex(1.6, 10))
        assertEquals(9, axisLabelIndex(20.0, 10))
        assertEquals(0, axisLabelIndex(3.0, 0))
    }

    @Test
    fun `intraday chart labels use the data market timezone`() {
        val shanghaiMorningTimestamp = 1_787_535_000_000L

        assertEquals("09:30", shanghaiMorningTimestamp.formatChartTime("Asia/Shanghai"))
        assertEquals("01:30", shanghaiMorningTimestamp.formatChartTime("UTC"))
    }

    @Test
    fun `price axis follows the data range instead of starting at zero`() {
        val range = adaptivePriceRange(minY = 0.50, maxY = 0.51)

        assertEquals(0.4992, range.first, 0.000001)
        assertEquals(0.5108, range.second, 0.000001)
    }

    @Test
    fun `price axis adds a visible margin when all prices are equal`() {
        val range = adaptivePriceRange(minY = 0.50, maxY = 0.50)

        assertEquals(0.49, range.first, 0.000001)
        assertEquals(0.51, range.second, 0.000001)
    }
}
