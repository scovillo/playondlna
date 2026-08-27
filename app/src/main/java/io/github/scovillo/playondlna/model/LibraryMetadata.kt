package io.github.scovillo.playondlna.model

import org.json.JSONObject

data class LibraryMetadata(
    val id: String,
    val title: String,
    val uploader: String,
    val durationInSeconds: Long,
    val qualityName: String,
    val isAudioOnly: Boolean,
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("title", title)
        json.put("uploader", uploader)
        json.put("durationInSeconds", durationInSeconds)
        json.put("qualityName", qualityName)
        json.put("isAudioOnly", isAudioOnly)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonString: String): LibraryMetadata {
            val json = JSONObject(jsonString)
            return LibraryMetadata(
                id = json.getString("id"),
                title = json.getString("title"),
                uploader = json.getString("uploader"),
                durationInSeconds = json.getLong("durationInSeconds"),
                qualityName = if (json.has("qualityName") && !json.isNull("qualityName")) json.getString("qualityName") else "",
                isAudioOnly = json.optBoolean("isAudioOnly", false),
            )
        }
    }
}
