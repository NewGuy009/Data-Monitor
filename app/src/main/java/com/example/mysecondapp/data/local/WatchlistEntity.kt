package com.example.mysecondapp.data.local

import androidx.room.Entity

@Entity(
    tableName = "watchlist",
    primaryKeys = ["market", "code"],
)
data class WatchlistEntity(
    val market: String,
    val code: String,
    val name: String,
    val sortOrder: Int,
)
