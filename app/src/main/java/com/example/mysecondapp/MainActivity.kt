package com.example.mysecondapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.mysecondapp.ui.navigation.AppBottomBar
import com.example.mysecondapp.ui.navigation.AppNavHost
import com.example.mysecondapp.ui.theme.MySecondAppTheme

import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MySecondAppTheme {
                AppRoot()
            }
        }
    }
}

/**
 * App 最外层的框架：底部导航 + NavHost 组合而成的主壳。
 *
 * 拆出来单独一个 Composable，方便未来在 Preview / 测试里独立渲染。
 */
@Composable
private fun AppRoot() {
    val navController = rememberNavController()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { AppBottomBar(navController = navController) },
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
