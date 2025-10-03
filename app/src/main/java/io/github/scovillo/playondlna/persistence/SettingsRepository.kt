package io.github.scovillo.playondlna.persistence

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.scovillo.playondlna.model.VideoQuality
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore("settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val VIDEO_QUALITY = stringPreferencesKey("video_quality")
    }

    val videoQualityFlow: Flow<VideoQuality> =
        context.dataStore.data.map { prefs ->
            val name = prefs[Keys.VIDEO_QUALITY]
            if (name != null) {
                VideoQuality.valueOf(name)
            } else VideoQuality.P720
        }.distinctUntilChanged()

    suspend fun saveVideoQuality(value: VideoQuality) {
        context.dataStore.edit { prefs ->
            prefs[Keys.VIDEO_QUALITY] = value.name
        }
    }
}
