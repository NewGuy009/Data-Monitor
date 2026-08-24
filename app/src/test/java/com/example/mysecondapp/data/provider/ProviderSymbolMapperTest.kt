package com.example.mysecondapp.data.provider

import com.example.mysecondapp.domain.model.StockIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderSymbolMapperTest {

    @Test
    fun `Tencent mapper formats supported mainland symbols`() {
        assertEquals("sh600000", TencentSymbolMapper.toProviderSymbol(StockIdentity("SH", "600000")))
        assertEquals("sz000001", TencentSymbolMapper.toProviderSymbol(StockIdentity("SZ", "000001")))
        assertEquals("sz300750", TencentSymbolMapper.toProviderSymbol(StockIdentity("SZ", "300750")))
        assertEquals("sh688981", TencentSymbolMapper.toProviderSymbol(StockIdentity("SH", "688981")))
        assertEquals("bj430047", TencentSymbolMapper.toProviderSymbol(StockIdentity("BJ", "430047")))
    }

    @Test
    fun `Sina mapper uses the same mainland quote symbol contract`() {
        assertEquals("sh600000", SinaSymbolMapper.toProviderSymbol(StockIdentity("SH", "600000")))
        assertEquals("sz000001", SinaSymbolMapper.toProviderSymbol(StockIdentity("SZ", "000001")))
    }

    @Test
    fun `mappers reject invalid or not yet supported identities`() {
        assertNull(TencentSymbolMapper.toProviderSymbol(StockIdentity("SZ", "688981")))
        assertNull(TencentSymbolMapper.toProviderSymbol(StockIdentity("US-NASDAQ", "AAPL")))
        assertNull(SinaSymbolMapper.toProviderSymbol(StockIdentity("KR-KOSPI", "005930")))
        assertNull(TencentSymbolMapper.toIdentity("hk00700"))
        assertNull(TencentSymbolMapper.toIdentity("sz688981"))
    }

    @Test
    fun `provider symbols are normalized back to stable identities`() {
        assertEquals(StockIdentity("SH", "600000"), TencentSymbolMapper.toIdentity("SH600000"))
        assertEquals(StockIdentity("SZ", "300750"), SinaSymbolMapper.toIdentity("sz300750"))
        assertNull(TencentSymbolMapper.toIdentity("sh60000"))
    }

    @Test
    fun `US mapper preserves exchange and normalizes ticker casing`() {
        assertEquals("US-NASDAQ:AAPL", UsSymbolMapper.toProviderSymbol(StockIdentity("us-nasdaq", "aapl")))
        assertEquals(StockIdentity("US-NYSE", "IBM"), UsSymbolMapper.toIdentity("us-nyse:ibm"))
        assertNull(UsSymbolMapper.toProviderSymbol(StockIdentity("US-NASDAQ", "00AAPL")))
        assertNull(UsSymbolMapper.toProviderSymbol(StockIdentity("US-OTC", "AAPL")))
    }

    @Test
    fun `Korean mapper preserves six digit security codes`() {
        assertEquals("KR-KOSPI:005930", KoreanSymbolMapper.toProviderSymbol(StockIdentity("KR-KOSPI", "005930")))
        assertEquals(StockIdentity("KR-KOSDAQ", "035720"), KoreanSymbolMapper.toIdentity("kr-kosdaq:035720"))
        assertNull(KoreanSymbolMapper.toProviderSymbol(StockIdentity("KR-KOSPI", "5930")))
        assertNull(KoreanSymbolMapper.toIdentity("kr-kospi:0059300"))
    }
}
