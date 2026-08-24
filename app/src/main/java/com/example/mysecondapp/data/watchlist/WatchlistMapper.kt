package com.example.mysecondapp.data.watchlist

import com.example.mysecondapp.data.decodeUnicodeEscapes
import com.example.mysecondapp.data.local.WatchlistEntity
import com.example.mysecondapp.domain.model.WatchlistItem

internal fun WatchlistEntity.toDomainModel(): WatchlistItem = WatchlistItem(
    market = market,
    code = code,
    // Decode previously persisted Smartbox names so existing watchlist entries recover on read.
    name = name.decodeUnicodeEscapes(),
    groupName = groupName,
    order = order,
)

internal fun WatchlistItem.toEntity(): WatchlistEntity = WatchlistEntity(
    market = market,
    code = code,
    name = name.decodeUnicodeEscapes(),
    groupName = groupName,
    order = order,
)
