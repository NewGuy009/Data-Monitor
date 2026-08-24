package com.example.mysecondapp.data.watchlist

import com.example.mysecondapp.data.local.WatchlistEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class WatchlistMapperTest {

    @Test
    fun `stored unicode escape name is decoded when read from Room`() {
        val item = WatchlistEntity(
            market = "SH",
            code = "688037",
            name = "\\u82af\\u6e90\\u5fae",
            groupName = null,
            order = 0,
        ).toDomainModel()

        assertEquals("芯源微", item.name)
    }
}
