package com.example.mysecondapp.domain.model

data class WatchlistItem(
    val market: String,
    val code: String,
    val name: String,
    val groupName: String? = null,
    val order: Int,
)
