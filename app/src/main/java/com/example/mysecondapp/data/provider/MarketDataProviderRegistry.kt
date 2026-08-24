package com.example.mysecondapp.data.provider

import com.example.mysecondapp.domain.model.DataProviderId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.jvm.JvmSuppressWildcards

/** Central lookup for registered providers; selection and fallback policy belongs to the next S.2 step. */
@Singleton
class MarketDataProviderRegistry @Inject constructor(
    providers: Set<@JvmSuppressWildcards MarketDataProvider>,
) {
    private val providersById: Map<DataProviderId, MarketDataProvider> = providers
        .also { registered ->
            require(registered.map { provider -> provider.id }.distinct().size == registered.size) {
                "Provider IDs must be unique."
            }
        }
        .associateBy { provider -> provider.id }

    val all: List<MarketDataProvider>
        get() = providersById.values.sortedBy { provider -> provider.id.value }

    fun find(id: DataProviderId): MarketDataProvider? = providersById[id]

    fun require(id: DataProviderId): MarketDataProvider =
        find(id) ?: error("Provider is not registered: ${id.value}")
}
