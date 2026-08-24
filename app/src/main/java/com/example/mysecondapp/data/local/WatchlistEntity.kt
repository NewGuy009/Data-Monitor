package com.example.mysecondapp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "watchlist",
    primaryKeys = ["market", "code"],
)
data class WatchlistEntity(
    val market: String,
    val code: String,
    val name: String,
    @ColumnInfo(name = "group_name")
    val groupName: String?,
    @ColumnInfo(name = "display_order")
    val order: Int,
)
