package com.example.mysecondapp.data.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 腾讯财经个股详情接口。
 *
 * 两个接口都返回 JSON，但内部结构会随周期变化；因此先作为 String 交给数据源做
 * 容错解析，避免网络层 DTO 直接绑定不稳定的外部字段。
 */
interface TencentDetailApi {

    @GET("appstock/app/minute/query")
    suspend fun getIntraday(@Query("code") code: String): String

    @GET("appstock/app/fqkline/get")
    suspend fun getForwardAdjustedKlines(@Query("param") parameter: String): String
}
