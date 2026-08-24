package com.example.mysecondapp.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KlineDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: KlineDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.klineDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun recent_query_is_descending_and_periods_are_isolated() = runBlocking {
        dao.upsertAll(
            listOf(
                candle("DAY", 1_000L, 10.01),
                candle("DAY", 2_000L, 10.02),
                candle("DAY", 3_000L, 10.03),
                candle("WEEK", 4_000L, 11.04),
            ),
        )

        val recentDays = dao.getRecent("SH", "600000", "DAY", limit = 2)
        val weeks = dao.getRecent("SH", "600000", "WEEK", limit = 10)

        assertEquals(listOf(3_000L, 2_000L), recentDays.map { it.timestampMillis })
        assertEquals(listOf(4_000L), weeks.map { it.timestampMillis })
    }

    @Test
    fun delete_except_recent_keeps_only_requested_count() = runBlocking {
        dao.upsertAll(
            (1L..4L).map { index -> candle("DAY", index * 1_000L, 10.0 + index) },
        )

        dao.deleteExceptRecent("SH", "600000", "DAY", keepCount = 2)

        assertEquals(2, dao.count())
        assertEquals(
            listOf(4_000L, 3_000L),
            dao.getRecent("SH", "600000", "DAY", limit = 10).map { it.timestampMillis },
        )
    }

    @Test
    fun adjustment_isolates_raw_and_qfq_series_with_same_timestamp() = runBlocking {
        dao.upsertAll(
            listOf(
                candle("DAY", 1_000L, 10.0, adjustment = "QFQ"),
                candle("DAY", 1_000L, 20.0, adjustment = "RAW"),
            ),
        )

        val qfq = dao.getRecent("SH", "600000", "DAY", adjustment = "QFQ", limit = 10)
        val raw = dao.getRecent("SH", "600000", "DAY", adjustment = "RAW", limit = 10)

        assertEquals(10.0, qfq.single().close, 0.0001)
        assertEquals(20.0, raw.single().close, 0.0001)
    }

    @Test
    fun provider_id_isolates_identical_kline_series() = runBlocking {
        dao.upsertAll(
            listOf(
                candle("DAY", 1_000L, 10.0, providerId = "tencent"),
                candle("DAY", 1_000L, 20.0, providerId = "sina"),
            ),
        )

        val tencent = dao.getRecent("SH", "600000", "DAY", providerId = "tencent", limit = 10)
        val sina = dao.getRecent("SH", "600000", "DAY", providerId = "sina", limit = 10)

        assertEquals(10.0, tencent.single().close, 0.0001)
        assertEquals(20.0, sina.single().close, 0.0001)
    }

    private fun candle(
        period: String,
        timestampMillis: Long,
        close: Double,
        adjustment: String = "QFQ",
        providerId: String = "tencent",
    ): KlineEntity = KlineEntity(
        market = "SH",
        code = "600000",
        period = period,
        adjustment = adjustment,
        providerId = providerId,
        timestampMillis = timestampMillis,
        open = close - 0.1,
        high = close + 0.1,
        low = close - 0.2,
        close = close,
        volume = 100L,
        turnover = 1_000.0,
        fetchedAtMillis = timestampMillis,
    )
}
