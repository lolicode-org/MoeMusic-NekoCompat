package org.lolicode.moemusic.nekocompat

import lol.bai.badpackets.api.play.PlayPackets
import net.fabricmc.api.ModInitializer
import org.lolicode.moemusic.api.MoeMusicApi

object NekoCompatMod : ModInitializer {
    override fun onInitialize() {
        MoeMusicApi.registerPlugin(NekoCompatPlugin)
        registerChannels()
    }

    private fun registerChannels() {
        PlayPackets.registerClientChannel(LegacyNekoProtocol.METADATA_PACKET_ID)
        PlayPackets.registerClientChannel(LegacyNekoProtocol.PLAYLIST_PACKET_ID)

        PlayPackets.registerServerChannel(LegacyNekoProtocol.CLIENT_HELLO_PACKET_ID)
        PlayPackets.registerServerReceiver(LegacyNekoProtocol.CLIENT_HELLO_PACKET_ID) { ctx, _ ->
            try {
                NekoCompatBridge.onClientHello(ctx.player())
            } catch (e: Exception) {
                org.slf4j.LoggerFactory.getLogger(NekoCompatMod::class.java).error("Error handling NekoCompat CLIENT_HELLO", e)
            }
        }

        PlayPackets.registerServerChannel(LegacyNekoProtocol.CLIENT_BYE_PACKET_ID)
        PlayPackets.registerServerReceiver(LegacyNekoProtocol.CLIENT_BYE_PACKET_ID) { ctx, _ ->
            try {
                NekoCompatBridge.onClientBye(ctx.player())
            } catch (e: Exception) {
                org.slf4j.LoggerFactory.getLogger(NekoCompatMod::class.java).error("Error handling NekoCompat CLIENT_BYE", e)
            }
        }
    }
}
