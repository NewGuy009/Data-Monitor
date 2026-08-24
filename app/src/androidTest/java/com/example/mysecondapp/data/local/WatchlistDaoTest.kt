package com.example.mysecondapp.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WatchlistDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: WatchlistDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.watchlistDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun observeAll_orders_by_group_then_display_order() = runBlocking {
        // Grouped items should stay together, and ungrouped items should be pushed to the end.
        dao.upsert(
            WatchlistEntity(
                market = "SZ",
                code = "000333",
                name = "美的集团",
                groupName = "家电",
                order = 2,
            ),
        )
        dao.upsert(
            WatchlistEntity(
                market = "SH",
                code = "600519",
                name = "贵州茅台",
                groupName = "白马",
                order = 1,
            ),
        )
        dao.upsert(
            WatchlistEntity(
                market = "SH",
                code = "600000",
                name = "浦发银行",
                groupName = "白马",
                order = 0,
            ),
        )
        dao.upsert(
            WatchlistEntity(
                market = "SZ",
                code = "300750",
                name = "宁德时代",
                groupName = null,
                order = 3,
            ),
        )

        val items = dao.observeAll().first()

        assertEquals(listOf("600000", "600519", "000333", "300750"), items.map { it.code })
    }

    @Test
    fun exists_and_group_queries_work() = runBlocking {
        dao.upsert(
            WatchlistEntity(
                market = "SH",
                code = "600000",
                name = "浦发银行",
                groupName = "银行",
                order = 0,
            ),
        )
        dao.upsert(
            WatchlistEntity(
                market = "SZ",
                code = "000001",
                name = "平安银行",
                groupName = null,
                order = 1,
            ),
        )

        assertTrue(dao.exists("SH", "600000"))
        assertFalse(dao.exists("SH", "600519"))
        assertEquals(3, dao.getMaxDisplayOrder())
        assertEquals(listOf("600000"), dao.observeByGroup("银行").first().map { it.code })
        assertEquals(listOf("000001"), dao.observeUngrouped().first().map { it.code })
    }
}
