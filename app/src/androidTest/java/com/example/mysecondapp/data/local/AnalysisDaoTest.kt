package com.example.mysecondapp.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mysecondapp.domain.model.CandleAdjustment
import com.example.mysecondapp.domain.model.CandlePeriod
import com.example.mysecondapp.domain.model.DataProviders
import com.example.mysecondapp.domain.model.StockIdentity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalysisDaoTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun signalDao_ignores_duplicate_identity() = runBlocking {
        val first = signalEntity(createdAtMillis = 1_000L)
        val second = first.copy(createdAtMillis = 2_000L)

        assertTrue(database.signalDao().insertIgnore(first) != -1L)
        assertEquals(-1L, database.signalDao().insertIgnore(second))
        assertEquals(1, database.signalDao().getForStock("SH", "600000").size)
    }

    @Test
    fun ruleDao_replaces_version_and_condition_for_same_rule_id() = runBlocking {
        database.analysisRuleDao().upsert(
            AnalysisRuleEntity("rule", 1, "Old", "DAY", "BULLISH", "{}", true),
        )
        database.analysisRuleDao().upsert(
            AnalysisRuleEntity("rule", 2, "New", "DAY", "BULLISH", "{\"conditionType\":\"TechnicalEvent\"}", false),
        )

        assertEquals(2, database.analysisRuleDao().getAll().single().version)
        assertTrue(database.analysisRuleDao().getAll().single().name == "New")
    }

    private fun signalEntity(createdAtMillis: Long) = SignalEntity(
        ruleId = "ema_golden_cross",
        ruleVersion = 1,
        market = "SH",
        code = "600000",
        period = CandlePeriod.DAY.name,
        adjustment = CandleAdjustment.QFQ.name,
        providerId = DataProviders.TENCENT.value,
        signalBarTimestampMillis = 10_000L,
        direction = "BULLISH",
        cutoffMillis = 10_000L,
        evidenceJson = "[]",
        createdAtMillis = createdAtMillis,
    )
}
