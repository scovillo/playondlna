package io.github.scovillo.playondlna.model

import org.json.JSONObject

data class LibraryMetadata(
    val id: String,
    val title: String,
    val uploader: String,
    val durationInSeconds: Long,
    val thumbnailUri: String? = null,
    val qualityName: String? = null,
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("id", id)
        json.put("title", title)
        json.put("uploader", uploader)
        json.put("durationInSeconds", durationInSeconds)
        json.put("thumbnailUri", thumbnailUri)
        json.put("qualityName", qualityName)
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
                thumbnailUri = if (json.has("thumbnailUri") && !json.isNull("thumbnailUri")) json.getString("thumbnailUri") else null,
                qualityName = if (json.has("qualityName") && !json.isNull("qualityName")) json.getString("qualityName") else null,
            )
        }
    }
}
