package org.lolicode.moemusic.nekocompat

import lol.bai.badpackets.api.PacketSender
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import org.lolicode.moemusic.api.MoeMusicUser
import org.lolicode.moemusic.api.event.*
import org.lolicode.moemusic.api.model.PlaybackResource
import org.lolicode.moemusic.api.model.PlaybackState
import org.lolicode.moemusic.api.model.TrackAddResult
import org.lolicode.moemusic.api.model.TrackContext
import org.lolicode.moemusic.api.model.TrackInfo
import org.lolicode.moemusic.api.plugin.PlaybackAudienceLease
import org.lolicode.moemusic.api.plugin.ServerRuntimeContext
import org.lolicode.moemusic.api.plugin.ServerSessionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

internal object NekoCompatBridge {
    private val lock = Any()

    private var sessionContext: ServerSessionContext? = null
    private var audienceLease: PlaybackAudienceLease? = null
    private val legacyPlayers: LinkedHashMap<UUID, ServerPlayer> = linkedMapOf()
    private val connectedPlayers: LinkedHashMap<UUID, MoeMusicUser> = linkedMapOf()
    private var logger: Logger = LoggerFactory.getLogger("MoeMusic/NekoCompat")

    fun onRuntimeLoad(ctx: ServerRuntimeContext) {
        logger = ctx.logger

        ctx.eventBus.subscribe<OnServerPlayerConnected> {
            synchronized(lock) {
                connectedPlayers[it.user.id] = it.user
            }
        }
        ctx.eventBus.subscribe<OnServerPlayerDisconnected> {
            synchronized(lock) {
                connectedPlayers.remove(it.user.id)
            }
            removeLegacyPlayer(it.user.id)
        }
        ctx.eventBus.subscribe<OnPlaybackStarted> {
            broadcastPlayback(it.track, it.playback, positionMs = 0L)
            broadcastPlaylist()
        }
        ctx.eventBus.subscribe<OnPlaybackPaused> {
            broadcastStop(it.track)
            broadcastPlaylist()
        }
        ctx.eventBus.subscribe<OnPlaybackResumed> {
            currentContext()?.let { context ->
                broadcastPlayback(context.track, context.playback, positionMs = it.positionMs)
                broadcastPlaylist()
            }
        }
        ctx.eventBus.subscribe<OnPlaybackSeeked> {
            if (!it.wasPlaying) return@subscribe
            currentContext()?.let { context ->
                broadcastPlayback(context.track, context.playback, positionMs = it.positionMs)
            }
        }
        ctx.eventBus.subscribe<OnPlaybackStopped> {
            broadcastStop(it.track)
            broadcastPlaylist()
        }
        ctx.eventBus.subscribe<OnTrackSubmitted> {
            if (it.result == TrackAddResult.QUEUED) {
                broadcastPlaylist()
            }
        }
        ctx.eventBus.subscribe<OnQueueTrackRemoved> {
            broadcastPlaylist()
        }
        ctx.eventBus.subscribe<OnPlaybackStartFailed> {
            if (!it.fromAutoplay) {
                broadcastPlaylist()
            }
        }
    }

    fun onServerSessionLoad(ctx: ServerSessionContext) {
        synchronized(lock) {
            audienceLease?.release()
            audienceLease = null
            legacyPlayers.clear()
            connectedPlayers.clear()
            sessionContext = ctx
        }
    }

    fun onServerSessionUnload() {
        synchronized(lock) {
            audienceLease?.release()
            audienceLease = null
            legacyPlayers.clear()
            connectedPlayers.clear()
            sessionContext = null
        }
    }

    fun onServerRuntimeUnload() {
        onServerSessionUnload()
    }

    fun onClientHello(player: ServerPlayer) {
        val currentBefore = synchronized(lock) {
            val session = sessionContext ?: return
            legacyPlayers[player.uuid] = player
            if (audienceLease == null) {
                audienceLease = session.acquirePlaybackAudienceLease()
            }
            session.playbackController.currentContext
        }
        if (currentBefore?.state is PlaybackState.Playing) {
            sendCurrentStateToPlayer(player, currentBefore)
        }
        sendPlaylistToPlayer(player)
    }

    fun onClientBye(player: ServerPlayer) {
        removeLegacyPlayer(player.uuid)
    }

    private fun removeLegacyPlayer(playerId: UUID) {
        synchronized(lock) {
            legacyPlayers.remove(playerId)
            if (legacyPlayers.isEmpty()) {
                audienceLease?.release()
                audienceLease = null
            }
        }
    }

    private fun currentContext(): TrackContext? =
        synchronized(lock) { sessionContext?.playbackController?.currentContext }

    private fun broadcastPlayback(track: TrackInfo, playback: PlaybackResource, positionMs: Long) {
        val players = playerSnapshot()
        if (players.isEmpty()) return
        when (LegacyNekoProtocol.playbackSupport(playback.url)) {
            LegacyPlaybackSupport.UNSUPPORTED -> {
                players.forEach {
                    sendPacket(it, LegacyNekoProtocol.METADATA_PACKET_ID) {
                        LegacyNekoProtocol.stopBuffer(track)
                    }
                }
            }
            LegacyPlaybackSupport.SUPPORTED, LegacyPlaybackSupport.UNKNOWN -> {
                players.forEach {
                    sendPacket(it, LegacyNekoProtocol.METADATA_PACKET_ID) {
                        LegacyNekoProtocol.metadataBuffer(track, playback, positionMs)
                    }
                }
            }
        }
    }

    private fun broadcastStop(track: TrackInfo?) {
        val players = playerSnapshot()
        if (players.isEmpty()) return
        players.forEach {
            sendPacket(it, LegacyNekoProtocol.METADATA_PACKET_ID) {
                LegacyNekoProtocol.stopBuffer(track)
            }
        }
    }

    private fun broadcastPlaylist() {
        val players = playerSnapshot()
        if (players.isEmpty()) return
        players.forEach {
            sendPacket(it, LegacyNekoProtocol.PLAYLIST_PACKET_ID) {
                LegacyNekoProtocol.playlistBuffer(visibleQueueForPlayer(it.uuid))
            }
        }
    }

    private fun sendPlaylistToPlayer(player: ServerPlayer) {
        sendPacket(player, LegacyNekoProtocol.PLAYLIST_PACKET_ID) {
            LegacyNekoProtocol.playlistBuffer(visibleQueueForPlayer(player.uuid))
        }
    }

    private fun sendCurrentStateToPlayer(player: ServerPlayer, context: TrackContext) {
        when (LegacyNekoProtocol.playbackSupport(context.playback.url)) {
            LegacyPlaybackSupport.UNSUPPORTED -> {
                sendPacket(player, LegacyNekoProtocol.METADATA_PACKET_ID) {
                    LegacyNekoProtocol.stopBuffer(context.track)
                }
            }
            LegacyPlaybackSupport.SUPPORTED, LegacyPlaybackSupport.UNKNOWN -> {
                val positionMs = LegacyNekoProtocol.positionMs(context)
                sendPacket(player, LegacyNekoProtocol.METADATA_PACKET_ID) {
                    LegacyNekoProtocol.metadataBuffer(context.track, context.playback, positionMs)
                }
            }
        }
    }

    private fun playerSnapshot(): List<ServerPlayer> =
        synchronized(lock) { legacyPlayers.values.toList() }

    private fun visibleQueueForPlayer(playerId: UUID): List<TrackInfo> =
        synchronized(lock) {
            val session = sessionContext ?: return@synchronized emptyList()
            val viewer = connectedPlayers[playerId]
            val queue = session.playbackController.userQueueSnapshot()
            visibleQueueForPlayer(queue, session.permissionService, viewer)
        }

    private fun sendPacket(
        player: ServerPlayer,
        packetId: Identifier,
        payloadFactory: () -> FriendlyByteBuf,
    ) {
        try {
            PacketSender.s2c(player).send(packetId, payloadFactory())
        } catch (e: Exception) {
            logger.warn("Failed to send legacy Neko packet {} to {}", packetId, player.gameProfile.name, e)
        }
    }
}
