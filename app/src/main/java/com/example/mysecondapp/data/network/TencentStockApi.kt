package com.example.mysecondapp.data.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 腾讯财经行情接口。
 *
 * 协议说明：返回纯文本，格式如下（每只股票一行）：
 *   v_sh600000="1~浦发银行~600000~10.50~10.40~10.60~..."
 *
 * 字段以 ~ 分隔，后续在 Repository 层解析。
 * 多只股票用逗号拼接 code，例如 "sh600000,sz000001"。
 *
 * BaseUrl: https://qt.gtimg.cn/
 */
interface TencentStockApi {

    @GET("q")
    suspend fun getQuotes(@Query("q") codes: String): String
}
