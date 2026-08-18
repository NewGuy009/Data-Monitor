package com.example.mysecondapp.di

import com.example.mysecondapp.data.network.TencentStockApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
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
            // 腾讯财经要求带合法 User-Agent，否则可能返回空
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

    /**
     * 腾讯行情接口返回的是纯文本（非 JSON），
     * 所以 converter 用 ScalarsConverter 更合适。
     * 这里直接用 addConverterFactory(ScalarsConverterFactory.create())
     * 就够了；Json converter 留着给后续 JSON 接口用。
     */
    @Provides
    @Singleton
    fun provideTencentRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://qt.gtimg.cn/")
            .client(okHttpClient)
            // 纯文本响应用 scalars；JSON 接口后续再叠加 kotlinx-serialization converter
            .addConverterFactory(ScalarsConverterFactory())
            .build()

    @Provides
    @Singleton
    fun provideTencentStockApi(retrofit: Retrofit): TencentStockApi =
        retrofit.create(TencentStockApi::class.java)
}

/**
 * 极简 Scalars 转换器：把响应体直接作为 String 返回。
 * 避免引入 retrofit2:converter-scalars 额外依赖。
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
