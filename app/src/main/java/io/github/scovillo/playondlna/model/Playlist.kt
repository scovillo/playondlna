package io.github.scovillo.playondlna.model

import org.json.JSONArray
import org.json.JSONObject

data class Playlist(
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
        fun fromJson(json: JSONObject): Playlist {
            val ids = json.optJSONArray("videoIds") ?: JSONArray()
            return Playlist(
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
