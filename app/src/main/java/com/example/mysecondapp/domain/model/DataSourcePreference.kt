package com.example.mysecondapp.domain.model

data class DataSourcePreference(
    val primaryProviderId: DataProviderId = DataProviders.TENCENT,
    val fallbackProviderIds: List<DataProviderId> = listOf(DataProviders.SINA),
) {
    /** Primary is always tried first; duplicate fallback entries must not cause duplicate requests. */
    fun orderedProviderIds(): List<DataProviderId> =
        listOf(primaryProviderId) + fallbackProviderIds
            .filter { providerId -> providerId != primaryProviderId }
            .distinct()
}
