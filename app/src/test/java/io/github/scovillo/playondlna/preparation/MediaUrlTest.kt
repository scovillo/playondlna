package io.github.scovillo.playondlna.preparation

import org.junit.Assert.assertEquals
import org.junit.Test
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService

class MediaUrlTest {
    private val normalizer = MediaUrl()

    @Test
    fun recognizesOnlySoundCloudShortLinkHost() {
        assertEquals(true, normalizer.isSoundCloudShortUrl("https://on.soundcloud.com/abc"))
        assertEquals(true, normalizer.isSoundCloudShortUrl("https://ON.SOUNDCLOUD.COM/abc"))
        listOf(
            "https://soundcloud.com/artist/track",
            "https://youtube.com/watch?v=yYmVqdoQU8A",
            "https://on.soundcloud.com.example.org/abc",
            "https://example.org/on.soundcloud.com/abc",
            "invalid URL",
        ).forEach { assertEquals(false, normalizer.isSoundCloudShortUrl(it)) }
    }

    @Test
    fun extractsUrlFromMarkdownShareText() {
        assertEquals(
            "https://www.youtube.com/playlist?list=PLbl2XIl2d6-k",
            normalizer.normalize(
                "[https://youtube.com/playlist?list=PLbl2XIl2d6-k\\&si=Q5SKJPwDESJsyD0F]" +
                    "(https://youtube.com/playlist?list=PLbl2XIl2d6-k\\&si=Q5SKJPwDESJsyD0F)",
            ),
        )
    }

    @Test
    fun translatesYMusicAppWatchUrlToYoutube() {
        assertEquals(
            "https://www.youtube.com/watch?v=UnztTWtCeG4",
            normalizer.normalize("https://ymusicapp.com/watch?v=UnztTWtCeG4"),
        )
    }

    @Test
    fun translatesYMusicAppUrlFromMarkdownShareText() {
        assertEquals(
            "https://www.youtube.com/watch?v=UnztTWtCeG4",
            normalizer.normalize(
                "[https://ymusicapp.com/watch?v=UnztTWtCeG4]" +
                    "(https://ymusicapp.com/watch?v=UnztTWtCeG4)",
            ),
        )
    }

    @Test
    fun preservesYMusicPlaylistContext() {
        assertEquals(
            "https://www.youtube.com/playlist?list=PLPeanjP0qwbU",
            normalizer.normalize("https://ymusicapp.com/watch?v=yYmVqdoQU8A&list=PLPeanjP0qwbU&pp=sAgC"),
        )
    }

    @Test
    fun recognizesNormalizedPlaylistLinksInExtractor() {
        listOf(
            "https://youtube.com/playlist?list=PLpL7EDN5GvBxDKfrPqokqNqCDLq0xam-6&si=hAoOOwGNQYzOZ8yW",
            "https://ymusicapp.com/watch?v=yYmVqdoQU8A&list=PLPeanjP0qwbU&pp=sAgC",
            "https://www.youtube.com/watch?v=yYmVqdoQU8A&list=PLpL7EDN5GvBxDKfrPqokqNqCDLq0xam-6",
        ).forEach {
            assertEquals(StreamingService.LinkType.PLAYLIST, ServiceList.YouTube.getLinkTypeByUrl(normalizer.normalize(it)))
        }
    }

    @Test
    fun extractsOrderedDistinctVideoIdsWithEncodedCommas() {
        assertEquals(
            listOf("https://www.youtube.com/watch?v=yYmVqdoQU8A", "https://www.youtube.com/watch?v=GNGP8KnGbDg"),
            normalizer.explicitPlaylistEntries(
                "https://www.youtube.com/watch_videos?video_ids=yYmVqdoQU8A%2CGNGP8KnGbDg,yYmVqdoQU8A",
            ),
        )
    }

    @Test
    fun recognizesExplicitPlaylistsBeforeValidatingEntries() {
        assertEquals(true, normalizer.hasExplicitPlaylistEntries("https://youtube.com/watch_videos?video_ids=yYmVqdoQU8A"))
        assertEquals(true, normalizer.hasExplicitPlaylistEntries("https://youtube.com/watch_videos?video_ids=invalid"))
        assertEquals(true, normalizer.hasExplicitPlaylistEntries("https://youtube.com/watch_videos?video_ids="))
        assertEquals(false, normalizer.hasExplicitPlaylistEntries("https://youtube.com.example.org/watch_videos?video_ids=yYmVqdoQU8A"))
        assertEquals(false, normalizer.hasExplicitPlaylistEntries("invalid URL"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPlaylistUrlsWhenReadingEntries() {
        normalizer.explicitPlaylistEntries("https://youtube.com/watch?v=yYmVqdoQU8A")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidVideoIds() {
        normalizer.explicitPlaylistEntries("https://youtube.com/watch_videos?video_ids=yYmVqdoQU8A,invalid")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmptyPlaylist() {
        normalizer.explicitPlaylistEntries("https://youtube.com/watch_videos?video_ids=")
    }

    @Test
    fun leavesSingleVideosAndOtherHostsUnchanged() {
        val video = "https://www.youtube.com/watch?v=yYmVqdoQU8A"
        assertEquals(video, normalizer.normalize(video))
        assertEquals(false, normalizer.hasExplicitPlaylistEntries(video))
        val unrelated = "https://youtube.com.example.org/watch?list=PL123"
        assertEquals(unrelated, normalizer.normalize(unrelated))
        assertEquals(false, normalizer.hasExplicitPlaylistEntries(unrelated))
    }
}
