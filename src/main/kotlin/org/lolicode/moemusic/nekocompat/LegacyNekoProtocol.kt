package org.lolicode.moemusic.nekocompat

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import net.fabricmc.fabric.api.networking.v1.FriendlyByteBufs
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.Identifier
import org.lolicode.moemusic.api.model.PlaybackResource
import org.lolicode.moemusic.api.model.PlaybackState
import org.lolicode.moemusic.api.model.TrackContext
import org.lolicode.moemusic.api.model.TrackInfo
import java.net.URI

internal enum class LegacyPlaybackSupport {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN,
}

internal object LegacyNekoProtocol {
    private const val MOD_ID = "nekomusic"
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    private val allowedSuffixes = setOf(".mp3", ".flac", ".ogg", ".oga")

    val METADATA_PACKET_ID: Identifier = Identifier.fromNamespaceAndPath(MOD_ID, "metadata")
    val PLAYLIST_PACKET_ID: Identifier = Identifier.fromNamespaceAndPath(MOD_ID, "list")
    val CLIENT_HELLO_PACKET_ID: Identifier = Identifier.fromNamespaceAndPath(MOD_ID, "client_hello")
    val CLIENT_BYE_PACKET_ID: Identifier = Identifier.fromNamespaceAndPath(MOD_ID, "client_bye")

    fun metadataBuffer(track: TrackInfo, playback: PlaybackResource, positionMs: Long): FriendlyByteBuf =
        FriendlyByteBufs.create().writeUtf(gson.toJson(metadataPayload(track, playback, positionMs)))

    fun metadataBuffer(track: TrackContext): FriendlyByteBuf =
        metadataBuffer(track.track, track.playback, positionMs(track))

    fun stopBuffer(track: TrackInfo? = null): FriendlyByteBuf =
        FriendlyByteBufs.create().writeUtf(gson.toJson(stopPayload(track)))

    fun playlistBuffer(tracks: List<TrackInfo>): FriendlyByteBuf =
        FriendlyByteBufs.create().writeUtf(gson.toJson(playlistPayload(tracks)))

    fun metadataPayload(track: TrackInfo, playback: PlaybackResource, positionMs: Long): LegacyMusicPayload {
        val durationMs = track.durationMs.coerceAtLeast(0L)
        val positionSeconds = if (durationMs > 0L) {
            (positionMs.coerceAtLeast(0L) / 1000L).coerceAtMost(durationMs / 1000L)
        } else {
            positionMs.coerceAtLeast(0L) / 1000L
        }
        return LegacyMusicPayload(
            artists = track.artists.map { LegacyArtistPayload(name = it.displayName, id = it.id) },
            name = track.title,
            id = legacyTrackId(track),
            url = playback.url,
            durationMs = durationMs,
            timeMs = durationMs,
            player = track.submittedByUserName,
            lyric = track.lyricLrc?.let { lyric ->
                LegacyLyricPayload(
                    lrc = LegacyLyricLinePayload(lyric),
                    translation = track.secondaryLyricLrc?.let(::LegacyLyricLinePayload),
                )
            },
            album = if (track.album != null || track.coverUrl != null) {
                LegacyAlbumPayload(
                    name = track.album,
                    id = albumCacheId(track),
                    picUrl = track.coverUrl,
                )
            } else {
                null
            },
            bitrate = 0,
            seekToSeconds = positionSeconds,
        )
    }

    fun stopPayload(track: TrackInfo? = null): LegacyMusicPayload =
        LegacyMusicPayload(
            artists = track?.artists?.map { LegacyArtistPayload(name = it.displayName, id = it.id) } ?: emptyList(),
            name = track?.title.orEmpty(),
            id = track?.let(::legacyTrackId) ?: 0L,
            url = "",
            durationMs = track?.durationMs?.coerceAtLeast(0L) ?: 0L,
            timeMs = track?.durationMs?.coerceAtLeast(0L) ?: 0L,
            player = track?.submittedByUserName,
            lyric = null,
            album = null,
            bitrate = 0,
            seekToSeconds = 0L,
        )

    fun playlistPayload(tracks: List<TrackInfo>): LegacyPlaylistPayload =
        LegacyPlaylistPayload(
            tracks = tracks.take(10).map { track ->
                LegacyPlaylistEntry(
                    name = track.title,
                    artist = track.artists.joinToString(", ") { it.displayName },
                    album = track.album,
                )
            }.toTypedArray(),
        )

    fun positionMs(track: TrackContext, nowNanos: Long = System.nanoTime()): Long =
        when (val state = track.state) {
            is PlaybackState.Playing -> ((nowNanos - track.serverStartMonotonic) / 1_000_000L).coerceAtLeast(0L)
            is PlaybackState.Paused -> state.positionMs
            PlaybackState.Stopped -> 0L
        }

    fun playbackSupport(url: String): LegacyPlaybackSupport {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return LegacyPlaybackSupport.UNKNOWN

        val uri = runCatching { URI(trimmed) }.getOrNull()
        val rawPath = uri?.path?.takeIf { it.isNotBlank() }
            ?: trimmed.substringBefore('?').substringBefore('#')
        val normalized = rawPath.lowercase()
        val fileName = normalized.substringAfterLast('/').trim()

        if (fileName.isEmpty()) return LegacyPlaybackSupport.UNKNOWN
        if (allowedSuffixes.any(fileName::endsWith)) return LegacyPlaybackSupport.SUPPORTED
        if (!fileName.contains('.')) return LegacyPlaybackSupport.UNKNOWN
        return LegacyPlaybackSupport.UNSUPPORTED
    }

    fun legacyTrackId(track: TrackInfo): Long {
        val key = buildString {
            append(track.sourceId.orEmpty())
            append('\u0000')
            append(track.id)
            if (track.sourceId.isNullOrBlank() || track.id.isBlank()) {
                append('\u0000')
                append(track.title)
                append('\u0000')
                append(track.artists.joinToString(",") { it.id.ifBlank { it.displayName } })
            }
        }
        // FNV-1a hash
        var hash = 0xcbf29ce484222325UL
        for (byte in key.encodeToByteArray()) {
            hash = (hash xor byte.toUByte().toULong()) * 0x100000001b3UL
        }
        val positive = (hash and Long.MAX_VALUE.toULong()).toLong()
        return if (positive == 0L) 1L else positive
    }

    private fun albumCacheId(track: TrackInfo): String =
        buildString {
            append(track.sourceId ?: "unknown")
            append(':')
            append(track.album ?: track.id.ifBlank { track.title })
        }
}

internal data class LegacyMusicPayload(
    @SerializedName("ar")
    val artists: List<LegacyArtistPayload>,
    val name: String,
    val id: Long,
    val url: String,
    @SerializedName("dt")
    val durationMs: Long,
    @SerializedName("time")
    val timeMs: Long,
    val player: String? = null,
    val lyric: LegacyLyricPayload? = null,
    @SerializedName("al")
    val album: LegacyAlbumPayload? = null,
    @SerializedName("br")
    val bitrate: Int = 0,
    @SerializedName("seek_to")
    val seekToSeconds: Long = 0L,
)

internal data class LegacyArtistPayload(
    val name: String,
    val id: String,
)

internal data class LegacyAlbumPayload(
    val name: String?,
    val id: String,
    @SerializedName("picUrl")
    val picUrl: String?,
)

internal data class LegacyLyricPayload(
    @SerializedName("lrc")
    val lrc: LegacyLyricLinePayload,
    @SerializedName("tlyric")
    val translation: LegacyLyricLinePayload? = null,
)

internal data class LegacyLyricLinePayload(
    val lyric: String,
)

internal data class LegacyPlaylistPayload(
    @SerializedName("musics")
    val tracks: Array<LegacyPlaylistEntry>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LegacyPlaylistPayload

        return tracks.contentEquals(other.tracks)
    }

    override fun hashCode(): Int {
        return tracks.contentHashCode()
    }
}

internal data class LegacyPlaylistEntry(
    val name: String,
    val artist: String,
    val album: String?,
)
