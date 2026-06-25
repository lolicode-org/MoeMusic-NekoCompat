package org.lolicode.moemusic.nekocompat

import org.lolicode.moemusic.api.model.PlaybackResource
import org.lolicode.moemusic.api.model.TrackInfo
import org.lolicode.moemusic.api.model.copy
import org.lolicode.moemusic.api.model.toArtistInfos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LegacyNekoProtocolTest {
    private val sampleTrack = TrackInfo(
        id = "track-1",
        title = "Track One",
        artists = listOf("Artist One").toArtistInfos(),
        durationMs = 123_000) {
            sourceId = "source-a"
            album = "Album One"
            coverUrl = "https://example.com/cover.jpg"
            submittedByUserName = "tester"
            lyricLrc = "[00:00.00]hello"
            secondaryLyricLrc = "[00:00.00]hola"
        }

    @Test
    fun `legacy hash is stable positive and source scoped`() {
        val a = LegacyNekoProtocol.legacyTrackId(sampleTrack)
        val b = LegacyNekoProtocol.legacyTrackId(sampleTrack)
        val c = LegacyNekoProtocol.legacyTrackId(sampleTrack.copy { sourceId = "source-b" })

        assertEquals(a, b)
        assertTrue(a > 0L)
        assertNotEquals(a, c)
    }

    @Test
    fun `metadata payload maps lyrics album and seek seconds`() {
        val payload = LegacyNekoProtocol.metadataPayload(
            track = sampleTrack,
            playback = PlaybackResource("https://example.com/audio.mp3"),
            positionMs = 12_345L,
        )

        assertEquals(sampleTrack.title, payload.name)
        assertEquals("https://example.com/audio.mp3", payload.url)
        assertEquals(12L, payload.seekToSeconds)
        assertEquals(sampleTrack.album, payload.album?.name)
        assertEquals(sampleTrack.coverUrl, payload.album?.picUrl)
        assertEquals("[00:00.00]hello", payload.lyric?.lrc?.lyric)
        assertEquals("[00:00.00]hola", payload.lyric?.translation?.lyric)
    }

    @Test
    fun `playlist payload is capped to ten tracks and preserves album`() {
        val payload = LegacyNekoProtocol.playlistPayload(
            (1..12).map { index ->
                sampleTrack.copy {
                    id = "track-$index"
                    title = "Track $index"
                    album = "Album $index"
                }
            }
        )

        assertEquals(10, payload.tracks.size)
        assertEquals("Track 1", payload.tracks.first().name)
        assertEquals("Album 10", payload.tracks.last().album)
    }

    @Test
    fun `format classifier rejects obvious unsupported suffixes and keeps unknowns best effort`() {
        assertEquals(LegacyPlaybackSupport.SUPPORTED, LegacyNekoProtocol.playbackSupport("https://x.test/a.mp3"))
        assertEquals(LegacyPlaybackSupport.SUPPORTED, LegacyNekoProtocol.playbackSupport("https://x.test/a.oga?sig=1"))
        assertEquals(LegacyPlaybackSupport.UNSUPPORTED, LegacyNekoProtocol.playbackSupport("https://x.test/a.aac"))
        assertEquals(LegacyPlaybackSupport.UNKNOWN, LegacyNekoProtocol.playbackSupport("https://x.test/stream"))
    }
}
