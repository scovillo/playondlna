package io.github.scovillo.playondlna.download

import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

internal class StreamDownloadUserAgent(
    private val url: String,
) {
    fun value(): String {
        val streamingUrl = url.replaceFirst("?", "?&")
        return when {
            YoutubeParsingHelper.isVisionOsStreamingUrl(streamingUrl) -> YoutubeParsingHelper.getVisionOsUserAgent(null)
            YoutubeParsingHelper.isIosStreamingUrl(streamingUrl) -> YoutubeParsingHelper.getIosUserAgent(null)
            else -> YoutubeParsingHelper.getAndroidUserAgent(null)
        }
    }
}
