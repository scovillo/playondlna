package io.github.scovillo.playondlna.persistence

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.scovillo.playondlna.model.VideoQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val VIDEO_QUALITY = stringPreferencesKey("video_quality")
        val IS_SUBTITLE_ENABLED = booleanPreferencesKey("is_subtitle_enabled")
        val IS_INTERNAL_SUBTITLE_ENABLED = booleanPreferencesKey("is_internal_subtitle_enabled")
        val IS_WLAN_PROTECTION_ENABLED = booleanPreferencesKey("is_wlan_protection_enabled")
        val FAVORITE_LOCATIONS = stringPreferencesKey("favorite_locations")
        val NATIVE_PLAYLIST_DEVICES = stringSetPreferencesKey("native_playlist_devices")
        val APP_PLAYLIST_DEVICES = stringSetPreferencesKey("app_playlist_devices")
    }

    val videoQualityFlow: Flow<VideoQuality> =
        context.dataStore.data.map { prefs ->
            val name = prefs[Keys.VIDEO_QUALITY]
            if (name != null) {
                VideoQuality.valueOf(name)
            } else {
                VideoQuality.P720
            }
        }.distinctUntilChanged()

    val isSubtitleEnabledFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            val value = prefs[Keys.IS_SUBTITLE_ENABLED]
            value ?: false
        }.distinctUntilChanged()

    val isInternalSubtitleEnabledFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            val value = prefs[Keys.IS_INTERNAL_SUBTITLE_ENABLED]
            value ?: false
        }.distinctUntilChanged()

    val isWlanProtectionEnabledFlow: Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            val value = prefs[Keys.IS_WLAN_PROTECTION_ENABLED]
            value ?: true
        }.distinctUntilChanged()

    val favoriteDeviceLocationsFlow: Flow<List<String>> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.FAVORITE_LOCATIONS]
                ?.split("|")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        }

    suspend fun saveVideoQuality(value: VideoQuality) {
        context.dataStore.edit { prefs ->
            prefs[Keys.VIDEO_QUALITY] = value.name
        }
    }

    suspend fun saveSubtitleEnabled(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_SUBTITLE_ENABLED] = value
        }
    }

    suspend fun saveInternalSubtitleEnabled(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_INTERNAL_SUBTITLE_ENABLED] = value
        }
    }

    suspend fun saveWlanProtectionEnabled(value: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_WLAN_PROTECTION_ENABLED] = value
        }
    }

    suspend fun saveFavoriteDeviceLocation(location: String) {
        context.dataStore.edit { prefs ->
            val current =
                prefs[Keys.FAVORITE_LOCATIONS]
                    ?.split("|")
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()
            val updated = (current + location).distinct()
            prefs[Keys.FAVORITE_LOCATIONS] = updated.joinToString("|")
        }
    }

    suspend fun removeFavoriteLocation(location: String) {
        context.dataStore.edit { prefs ->
            val current =
                prefs[Keys.FAVORITE_LOCATIONS]
                    ?.split("|")
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()
            val updated = current.filterNot { it == location }
            prefs[Keys.FAVORITE_LOCATIONS] =
                updated.joinToString("|")
        }
    }

    suspend fun nativePlaylistSupport(deviceUsn: String): Boolean? {
        val preferences = context.dataStore.data.first()
        return when (deviceUsn) {
            in preferences[Keys.NATIVE_PLAYLIST_DEVICES].orEmpty() -> true
            in preferences[Keys.APP_PLAYLIST_DEVICES].orEmpty() -> false
            else -> null
        }
    }

    suspend fun saveNativePlaylistSupport(
        deviceUsn: String,
        supported: Boolean,
    ) {
        context.dataStore.edit { preferences ->
            val nativeDevices = preferences[Keys.NATIVE_PLAYLIST_DEVICES].orEmpty() - deviceUsn
            val appDevices = preferences[Keys.APP_PLAYLIST_DEVICES].orEmpty() - deviceUsn
            preferences[Keys.NATIVE_PLAYLIST_DEVICES] = if (supported) nativeDevices + deviceUsn else nativeDevices
            preferences[Keys.APP_PLAYLIST_DEVICES] = if (supported) appDevices else appDevices + deviceUsn
        }
    }
}
