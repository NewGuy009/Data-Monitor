package com.example.mysecondapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.mysecondapp.ui.navigation.AppBottomBar
import com.example.mysecondapp.ui.navigation.AppNavHost
import com.example.mysecondapp.ui.navigation.TopLevelDestination
import com.example.mysecondapp.ui.theme.MySecondAppTheme
import com.example.mysecondapp.service.monitor.PingWorker
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.concurrent.TimeUnit

private const val PING_WORK_NAME = "ping-work"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enqueuePingWorker()
        setContent {
            MySecondAppTheme {
                AppRoot()
            }
        }
    }
}

private fun ComponentActivity.enqueuePingWorker() {
    val request = PeriodicWorkRequestBuilder<PingWorker>(15, TimeUnit.MINUTES).build()

    WorkManager.getInstance(this).enqueueUniquePeriodicWork(
        PING_WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        request,
    )

    Timber.i("PingWorker scheduled")
}

/**
 * App 最外层的框架：底部导航 + NavHost 组合而成的主壳。
 *
 * 拆出来单独一个 Composable，方便未来在 Preview / 测试里独立渲染。
 */
@Composable
private fun AppRoot() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val isTopLevelDestination = TopLevelDestination.entries.any { destination ->
        destination.route == currentBackStackEntry?.destination?.route
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        // Detail is a focused child page; tabs return after navigating back to a top-level destination.
        bottomBar = {
            if (isTopLevelDestination) {
                AppBottomBar(navController = navController)
            }
        },
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
