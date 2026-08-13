package com.example.mysecondapp.ui.watchlist

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 自选股页面的 ViewModel。
 *
 * M0.2 阶段只持有一条测试文本，用来验证 Hilt 注入链路通畅。
 * M1 阶段会在这里注入 WatchlistRepository 并暴露真实行情 Flow。
 */
@HiltViewModel
class WatchlistViewModel @Inject constructor() : ViewModel() {

    private val _statusText = MutableStateFlow("Hilt 注入成功，ViewModel 已就绪")
    val statusText: StateFlow<String> = _statusText.asStateFlow()
}
