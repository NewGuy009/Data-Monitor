package com.example.mysecondapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StockMarketTest {

    @Test
    fun `representative a share symbols map to validated tencent provider codes`() {
        assertEquals("sh600000", StockIdentity("SH", "600000").providerCodeOrNull())
        assertEquals("sz000001", StockIdentity("SZ", "000001").providerCodeOrNull())
        assertEquals("sz300750", StockIdentity("SZ", "300750").providerCodeOrNull())
        assertEquals("sh688981", StockIdentity("SH", "688981").providerCodeOrNull())
    }

    @Test
    fun `invalid market and code combinations are rejected before provider request`() {
        assertNull(StockIdentity("SZ", "688981").providerCodeOrNull())
        assertNull(StockIdentity("UNKNOWN", "600000").providerCodeOrNull())
    }

    @Test
    fun `beijing market is explicit quote only capability until detail support is added`() {
        val capabilities = StockIdentity("BJ", "430047").marketCapabilities()

        assertTrue(capabilities?.canLoadQuote == true)
        assertFalse(capabilities?.canLoadIntraday == true)
        assertFalse(capabilities?.canLoadCandles == true)
    }
}
