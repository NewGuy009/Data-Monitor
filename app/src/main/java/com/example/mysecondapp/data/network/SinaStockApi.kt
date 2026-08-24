package com.example.mysecondapp.data.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path

interface SinaStockApi {

    @Headers(
        "Referer: https://finance.sina.com.cn",
        "Accept-Language: zh-CN,zh;q=0.9",
    )
    @GET("list={codes}")
    suspend fun getQuotes(
        @Path("codes", encoded = true) codes: String,
    ): ResponseBody
}
