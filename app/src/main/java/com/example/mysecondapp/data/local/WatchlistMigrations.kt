package com.example.mysecondapp.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object WatchlistMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 旧表只有 sortOrder，没有分组字段；这里建新表后搬运数据，避免列重命名兼容问题。
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS watchlist_new (
                    market TEXT NOT NULL,
                    code TEXT NOT NULL,
                    name TEXT NOT NULL,
                    group_name TEXT,
                    display_order INTEGER NOT NULL,
                    PRIMARY KEY(market, code)
                )
                """.trimIndent(),
            )

            db.execSQL(
                """
                INSERT INTO watchlist_new (market, code, name, group_name, display_order)
                SELECT market, code, name, NULL, sortOrder
                FROM watchlist
                """.trimIndent(),
            )

            db.execSQL("DROP TABLE watchlist")
            db.execSQL("ALTER TABLE watchlist_new RENAME TO watchlist")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // M2 新增独立 K 线表，不改动 watchlist，确保已有自选股数据原样保留。
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS kline (
                    market TEXT NOT NULL,
                    code TEXT NOT NULL,
                    period TEXT NOT NULL,
                    timestamp_millis INTEGER NOT NULL,
                    open REAL NOT NULL,
                    high REAL NOT NULL,
                    low REAL NOT NULL,
                    close REAL NOT NULL,
                    volume INTEGER,
                    turnover REAL,
                    fetched_at_millis INTEGER NOT NULL,
                    PRIMARY KEY(market, code, period, timestamp_millis)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_kline_market_code_period_timestamp_millis
                ON kline(market, code, period, timestamp_millis)
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Adjustment is part of the series identity so raw and adjusted K-lines never overwrite each other.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS kline_new (
                    market TEXT NOT NULL,
                    code TEXT NOT NULL,
                    period TEXT NOT NULL,
                    adjustment TEXT NOT NULL DEFAULT 'QFQ',
                    timestamp_millis INTEGER NOT NULL,
                    open REAL NOT NULL,
                    high REAL NOT NULL,
                    low REAL NOT NULL,
                    close REAL NOT NULL,
                    volume INTEGER,
                    turnover REAL,
                    fetched_at_millis INTEGER NOT NULL,
                    PRIMARY KEY(market, code, period, adjustment, timestamp_millis)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO kline_new (
                    market, code, period, adjustment, timestamp_millis,
                    open, high, low, close, volume, turnover, fetched_at_millis
                )
                SELECT market, code, period, 'QFQ', timestamp_millis,
                    open, high, low, close, volume, turnover, fetched_at_millis
                FROM kline
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE kline")
            db.execSQL("ALTER TABLE kline_new RENAME TO kline")
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_kline_market_code_period_adjustment_timestamp_millis
                ON kline(market, code, period, adjustment, timestamp_millis)
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Existing K-lines are normalized A-share values from the current providers.
            db.execSQL("ALTER TABLE kline ADD COLUMN currency TEXT NOT NULL DEFAULT 'CNY'")
            db.execSQL("ALTER TABLE kline ADD COLUMN volume_unit TEXT NOT NULL DEFAULT 'SHARES'")
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // v5 K-lines were produced only by Tencent, so migration can preserve them deterministically.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS kline_new (
                    market TEXT NOT NULL,
                    code TEXT NOT NULL,
                    period TEXT NOT NULL,
                    adjustment TEXT NOT NULL DEFAULT 'QFQ',
                    provider_id TEXT NOT NULL DEFAULT 'tencent',
                    timestamp_millis INTEGER NOT NULL,
                    open REAL NOT NULL,
                    high REAL NOT NULL,
                    low REAL NOT NULL,
                    close REAL NOT NULL,
                    volume INTEGER,
                    turnover REAL,
                    currency TEXT NOT NULL DEFAULT 'CNY',
                    volume_unit TEXT NOT NULL DEFAULT 'SHARES',
                    fetched_at_millis INTEGER NOT NULL,
                    PRIMARY KEY(market, code, period, adjustment, provider_id, timestamp_millis)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO kline_new (
                    market, code, period, adjustment, provider_id, timestamp_millis,
                    open, high, low, close, volume, turnover, currency, volume_unit, fetched_at_millis
                )
                SELECT market, code, period, adjustment, 'tencent', timestamp_millis,
                    open, high, low, close, volume, turnover, currency, volume_unit, fetched_at_millis
                FROM kline
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE kline")
            db.execSQL("ALTER TABLE kline_new RENAME TO kline")
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_kline_market_code_period_adjustment_provider_id_timestamp_millis
                ON kline(market, code, period, adjustment, provider_id, timestamp_millis)
                """.trimIndent(),
            )
        }
    }
}
