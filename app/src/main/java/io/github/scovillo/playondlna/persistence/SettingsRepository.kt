package io.github.scovillo.playondlna.persistence

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.scovillo.playondlna.model.VideoQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.json.JSONObject

val Context.dataStore by preferencesDataStore("settings")

data class DeviceSettings(
    val forcePlayOnDlnaManagedPlaylist: Boolean = false,
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val VIDEO_QUALITY = stringPreferencesKey("video_quality")
        val IS_SUBTITLE_ENABLED = booleanPreferencesKey("is_subtitle_enabled")
        val IS_INTERNAL_SUBTITLE_ENABLED = booleanPreferencesKey("is_internal_subtitle_enabled")
        val IS_WLAN_PROTECTION_ENABLED = booleanPreferencesKey("is_wlan_protection_enabled")
        val FAVORITE_LOCATIONS = stringPreferencesKey("favorite_locations")
        val FORCE_PLAY_ON_DLNA_MANAGED_PLAYLIST_USNS =
            stringPreferencesKey("force_play_on_dlna_managed_playlist_usns")
        val DEVICE_SETTINGS = stringPreferencesKey("device_settings")
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

    /**
     * Settings are keyed by the stable UPnP USN and stored as a JSON object per device.
     * Add fields to [DeviceSettings] and [encodeDeviceSettings] as further per-device options
     * become necessary.
     */
    val deviceSettingsFlow: Flow<Map<String, DeviceSettings>> =
        context.dataStore.data.map { prefs ->
            decodeDeviceSettings(prefs[Keys.DEVICE_SETTINGS])
                .ifEmpty { legacyDeviceSettings(prefs) }
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

    suspend fun saveDeviceSettings(
        deviceUsn: String,
        settings: DeviceSettings,
    ) {
        context.dataStore.edit { prefs ->
            val updated = legacyDeviceSettings(prefs) + decodeDeviceSettings(prefs[Keys.DEVICE_SETTINGS]) + (deviceUsn to settings)
            prefs[Keys.DEVICE_SETTINGS] = encodeDeviceSettings(updated)
            prefs.remove(Keys.FORCE_PLAY_ON_DLNA_MANAGED_PLAYLIST_USNS)
        }
    }

    private fun legacyDeviceSettings(prefs: androidx.datastore.preferences.core.Preferences): Map<String, DeviceSettings> =
        prefs[Keys.FORCE_PLAY_ON_DLNA_MANAGED_PLAYLIST_USNS]
            ?.split("|")
            ?.filter { it.isNotBlank() }
            ?.map(::decodeUsn)
            .orEmpty()
            .associateWith { DeviceSettings(forcePlayOnDlnaManagedPlaylist = true) }

    private fun decodeDeviceSettings(serializedSettings: String?): Map<String, DeviceSettings> =
        runCatching {
            val settings = JSONObject(serializedSettings ?: "{}")
            settings.keys().asSequence().associateWith { usn ->
                val deviceSettings = settings.getJSONObject(usn)
                DeviceSettings(
                    forcePlayOnDlnaManagedPlaylist =
                        deviceSettings.optBoolean("forcePlayOnDlnaManagedPlaylist"),
                )
            }
        }.getOrDefault(emptyMap())

    private fun encodeDeviceSettings(settings: Map<String, DeviceSettings>): String =
        JSONObject().apply {
            settings.forEach { (usn, deviceSettings) ->
                put(
                    usn,
                    JSONObject().put(
                        "forcePlayOnDlnaManagedPlaylist",
                        deviceSettings.forcePlayOnDlnaManagedPlaylist,
                    ),
                )
            }
        }.toString()

    private fun decodeUsn(encodedUsn: String): String = String(Base64.decode(encodedUsn, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
}
