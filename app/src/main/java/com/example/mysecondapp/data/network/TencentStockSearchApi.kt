package com.example.mysecondapp.data.network

import retrofit2.http.GET
import retrofit2.http.Query

/** Tencent symbol suggestion endpoint. The response is a compact text protocol. */
interface TencentStockSearchApi {

    @GET("s3/")
    suspend fun search(
        @Query("v") version: Int = 2,
        @Query("q") query: String,
        @Query("t") type: String = "all",
    ): String
}
