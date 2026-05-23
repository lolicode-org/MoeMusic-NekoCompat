package org.lolicode.moemusic.nekocompat

import org.lolicode.moemusic.api.LocalizedText
import org.lolicode.moemusic.api.plugin.Plugin
import org.lolicode.moemusic.api.plugin.ServerRuntimeContext
import org.lolicode.moemusic.api.plugin.ServerSessionContext

object NekoCompatPlugin : Plugin {
    override val id: String = "moemusic_neko_compat"
    override val configId: String = id
    override val version: String = "1.0.0"
    override val supportedApiVersions: String = ">=1.0.0 <2.0.0"
    override val displayName: LocalizedText = LocalizedText.plain("NekoMusic Compatibility Plugin")

    override fun onServerRuntimeLoad(ctx: ServerRuntimeContext) {
        NekoCompatBridge.onRuntimeLoad(ctx)
    }

    override fun onServerSessionLoad(ctx: ServerSessionContext) {
        NekoCompatBridge.onServerSessionLoad(ctx)
    }

    override fun onServerSessionUnload() {
        NekoCompatBridge.onServerSessionUnload()
    }

    override fun onServerRuntimeUnload() {
        NekoCompatBridge.onServerRuntimeUnload()
    }
}
