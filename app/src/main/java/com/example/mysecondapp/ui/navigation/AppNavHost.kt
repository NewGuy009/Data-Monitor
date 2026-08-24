package com.example.mysecondapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.mysecondapp.ui.detail.StockDetailScreen
import com.example.mysecondapp.ui.settings.SettingsScreen
import com.example.mysecondapp.ui.watchlist.WatchlistScreen

/**
 * 全局导航图。
 *
 * 每个页面对应 [TopLevelDestination] 中的一个 route。后续要加子页面
 * （比如"个股详情"），直接在这里再挂一个 composable("detail/{code}") 即可。
 */
@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.Start.route,
        modifier = modifier,
    ) {
        composable(TopLevelDestination.Watchlist.route) {
            WatchlistScreen(
                onSnapshotClick = { item ->
                    navController.navigate(DetailDestination.createRoute(item.market, item.code))
                },
            )
        }
        composable(TopLevelDestination.Settings.route) {
            SettingsScreen()
        }
        composable(
            route = DetailDestination.route,
            arguments = listOf(
                navArgument(DetailDestination.MARKET_ARGUMENT) { type = NavType.StringType },
                navArgument(DetailDestination.CODE_ARGUMENT) { type = NavType.StringType },
            ),
        ) {
            StockDetailScreen(onBackClick = navController::navigateUp)
        }
    }
}
