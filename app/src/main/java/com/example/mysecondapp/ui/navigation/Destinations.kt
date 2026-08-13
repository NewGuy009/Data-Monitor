package com.example.mysecondapp.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * App 内的顶层目的地（底部导航项）。
 *
 * 每新增一个 Tab，只需在这里加一个枚举值 + 在 AppNavHost 里加对应的 composable(...) 项。
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Watchlist(
        route = "watchlist",
        label = "自选",
        icon = Icons.AutoMirrored.Filled.ShowChart,
    ),
    Settings(
        route = "settings",
        label = "设置",
        icon = Icons.Filled.Settings,
    );

    companion object {
        val Start: TopLevelDestination = Watchlist
    }
}
