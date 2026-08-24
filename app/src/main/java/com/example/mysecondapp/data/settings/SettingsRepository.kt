package com.example.mysecondapp.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.example.mysecondapp.domain.model.DataProviderId
import com.example.mysecondapp.domain.model.DataProviders
import com.example.mysecondapp.domain.model.DataSourcePreference
import javax.inject.Inject
import javax.inject.Singleton

private const val SETTINGS_DATASTORE_NAME = "settings"
private const val DEFAULT_REFRESH_INTERVAL_SECONDS = 15
private const val MIN_REFRESH_INTERVAL_SECONDS = 5
private const val MAX_REFRESH_INTERVAL_SECONDS = 60

private val Context.settingsDataStore by preferencesDataStore(
    name = SETTINGS_DATASTORE_NAME,
)

@Singleton
class SettingsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val refreshIntervalKey = intPreferencesKey("refresh_interval_seconds")
    private val primaryProviderKey = stringPreferencesKey("primary_provider_id")
    private val fallbackProviderKey = stringPreferencesKey("fallback_provider_ids")

    val refreshIntervalSeconds: Flow<Int> =
        context.settingsDataStore.data
            .map { preferences ->
                preferences[refreshIntervalKey] ?: DEFAULT_REFRESH_INTERVAL_SECONDS
            }
            .distinctUntilChanged()

    val dataSourcePreference: Flow<DataSourcePreference> =
        context.settingsDataStore.data
            .map { preferences ->
                DataSourcePreference(
                    primaryProviderId = DataProviderId(
                        preferences[primaryProviderKey] ?: DataProviders.TENCENT.value,
                    ),
                    fallbackProviderIds = preferences[fallbackProviderKey]
                        ?.split(PROVIDER_ID_SEPARATOR)
                        ?.filter(String::isNotBlank)
                        ?.map(::DataProviderId)
                        ?: listOf(DataProviders.SINA),
                )
            }
            .distinctUntilChanged()

    suspend fun updateRefreshIntervalSeconds(seconds: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[refreshIntervalKey] =
                seconds.coerceIn(
                    minimumValue = MIN_REFRESH_INTERVAL_SECONDS,
                    maximumValue = MAX_REFRESH_INTERVAL_SECONDS,
                )
        }
    }

    suspend fun updateDataSourcePreference(preference: DataSourcePreference) {
        context.settingsDataStore.edit { preferences ->
            preferences[primaryProviderKey] = preference.primaryProviderId.value
            preferences[fallbackProviderKey] = preference.fallbackProviderIds
                .filter { providerId -> providerId != preference.primaryProviderId }
                .distinct()
                .joinToString(PROVIDER_ID_SEPARATOR) { providerId -> providerId.value }
        }
    }

    private companion object {
        const val PROVIDER_ID_SEPARATOR = ","
    }
}
