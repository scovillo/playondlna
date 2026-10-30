package io.github.scovillo.playondlna.persistence

import org.json.JSONArray
import org.json.JSONObject

data class PlaylistPersistence(
    val id: String,
    val name: String,
    val videoIds: List<String>,
) {
    fun toJson(): JSONObject =
        JSONObject().apply {
            put("id", id)
            put("name", name)
            put("videoIds", JSONArray(videoIds))
        }

    companion object {
        fun fromJson(json: JSONObject): PlaylistPersistence {
            val ids = json.optJSONArray("videoIds") ?: JSONArray()
            return PlaylistPersistence(
                id = json.getString("id"),
                name = json.getString("name"),
                videoIds =
                    buildList {
                        for (index in 0 until ids.length()) {
                            ids.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                        }
                    }.distinct(),
            )
        }
    }
}
