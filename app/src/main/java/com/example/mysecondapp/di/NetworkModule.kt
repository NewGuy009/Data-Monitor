package com.example.mysecondapp.di

import com.example.mysecondapp.data.network.SinaStockApi
import com.example.mysecondapp.data.network.TencentDetailApi
import com.example.mysecondapp.data.network.TencentStockSearchApi
import com.example.mysecondapp.data.network.TencentStockApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(logging)
            // 统一补 UA，避免部分财经接口直接拒绝空头请求。
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideTencentStockApi(okHttpClient: OkHttpClient): TencentStockApi =
        Retrofit.Builder()
            .baseUrl("https://qt.gtimg.cn/")
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory())
            .build()
            .create(TencentStockApi::class.java)

    @Provides
    @Singleton
    fun provideTencentDetailApi(okHttpClient: OkHttpClient): TencentDetailApi =
        Retrofit.Builder()
            .baseUrl("https://web.ifzq.gtimg.cn/")
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory())
            .build()
            .create(TencentDetailApi::class.java)

    @Provides
    @Singleton
    fun provideTencentStockSearchApi(okHttpClient: OkHttpClient): TencentStockSearchApi =
        Retrofit.Builder()
            .baseUrl("https://smartbox.gtimg.cn/")
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory())
            .build()
            .create(TencentStockSearchApi::class.java)

    @Provides
    @Singleton
    fun provideSinaStockApi(okHttpClient: OkHttpClient): SinaStockApi =
        Retrofit.Builder()
            .baseUrl("https://hq.sinajs.cn/")
            .client(okHttpClient)
            .build()
            .create(SinaStockApi::class.java)
}

/**
 * 腾讯接口返回纯文本，这里把响应体直接映射成 String。
 * 这是 M1 里最轻量的接入方式，后续若换 JSON 源再补 kotlinx-serialization 即可。
 */
private class ScalarsConverterFactory : retrofit2.Converter.Factory() {
    override fun responseBodyConverter(
        type: java.lang.reflect.Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): retrofit2.Converter<okhttp3.ResponseBody, *>? {
        if (type != String::class.java) return null
        return retrofit2.Converter<okhttp3.ResponseBody, String> { body -> body.string() }
    }
}
