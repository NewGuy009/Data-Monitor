package com.example.mysecondapp.di

import com.example.mysecondapp.data.provider.MarketDataProvider
import com.example.mysecondapp.data.provider.DefaultMarketDataProviderSelector
import com.example.mysecondapp.data.provider.MarketDataProviderSelector
import com.example.mysecondapp.data.provider.SinaMarketDataProvider
import com.example.mysecondapp.data.provider.TencentMarketDataProvider
import com.example.mysecondapp.data.provider.UsMarketDataProvider
import com.example.mysecondapp.data.provider.KoreanMarketDataProvider
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ProviderModule {

    @Binds
    abstract fun bindMarketDataProviderSelector(
        selector: DefaultMarketDataProviderSelector,
    ): MarketDataProviderSelector

    @Binds
    @IntoSet
    abstract fun bindTencentMarketDataProvider(
        provider: TencentMarketDataProvider,
    ): MarketDataProvider

    @Binds
    @IntoSet
    abstract fun bindSinaMarketDataProvider(
        provider: SinaMarketDataProvider,
    ): MarketDataProvider

    @Binds
    @IntoSet
    abstract fun bindUsMarketDataProvider(
        provider: UsMarketDataProvider,
    ): MarketDataProvider

    @Binds
    @IntoSet
    abstract fun bindKoreanMarketDataProvider(
        provider: KoreanMarketDataProvider,
    ): MarketDataProvider
}
