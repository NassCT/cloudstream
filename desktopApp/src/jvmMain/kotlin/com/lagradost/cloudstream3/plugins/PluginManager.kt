package com.lagradost.cloudstream3.plugins

import android.content.Context
import androidx.fragment.app.FragmentActivity
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream4.desktop.PluginLoader
import com.lagradost.cloudstream4.desktop.SettingsStore
import java.io.File

/** Same shape as the app's PluginData. */
data class PluginData(
    @JsonProperty("internalName") val internalName: String,
    @JsonProperty("url") val url: String?,
    @JsonProperty("isOnline") val isOnline: Boolean,
    @JsonProperty("filePath") val filePath: String,
    @JsonProperty("version") val version: Int,
)

/**
 * Desktop version of the app's PluginManager, limited to what extensions call
 * (reloading themselves after their settings change).
 */
object PluginManager {
    fun getPluginsOnline(): Array<PluginData> =
        SettingsStore.value.installedPlugins
            .map { PluginData(it.internalName, it.url, true, it.filePath, it.version) }
            .toTypedArray()

    fun unloadPlugin(absolutePath: String) {
        val plugin = SettingsStore.value.installedPlugins.firstOrNull { it.filePath == absolutePath } ?: return
        PluginLoader.unload(File(absolutePath), plugin.internalName)
    }

    suspend fun loadSinglePlugin(context: Context, apiName: String): Boolean {
        val plugin = SettingsStore.value.installedPlugins.firstOrNull {
            it.internalName.equals(apiName, true) || File(it.filePath).nameWithoutExtension.equals(apiName, true)
        } ?: return false
        return PluginLoader.load(File(plugin.filePath), plugin.internalName) is PluginLoader.LoadState.Loaded
    }

    fun hotReloadAllLocalPlugins(activity: FragmentActivity?) {}
}
