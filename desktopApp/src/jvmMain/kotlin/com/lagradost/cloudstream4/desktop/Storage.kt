package com.lagradost.cloudstream4.desktop

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Where CloudStream Plus keeps its data: %APPDATA%\CloudStreamPlus on Windows. */
object AppDirs {
    val root: File = run {
        val appData = System.getenv("APPDATA")
        val base = if (!appData.isNullOrBlank()) File(appData, "CloudStreamPlus")
        else File(System.getProperty("user.home"), ".cloudstreamplus")
        base.apply { mkdirs() }
    }
    val plugins: File = File(root, "plugins").apply { mkdirs() }
    val cache: File = File(root, "cache").apply { mkdirs() }
    val settingsFile: File = File(root, "settings.json")
}

val desktopMapper = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .configure(SerializationFeature.INDENT_OUTPUT, true)

data class SavedRepository(
    val url: String,
    val name: String,
    val iconUrl: String? = null,
    val description: String? = null,
)

data class InstalledPlugin(
    val internalName: String,
    val name: String,
    val version: Int,
    val url: String,
    val repositoryUrl: String,
    val filePath: String,
    val iconUrl: String? = null,
    val enabled: Boolean = true,
)

enum class PlayerChoice { Auto, Vlc, Mpv }

data class DesktopSettings(
    val repositories: List<SavedRepository> = emptyList(),
    val installedPlugins: List<InstalledPlugin> = emptyList(),
    val player: PlayerChoice = PlayerChoice.Auto,
    val vlcPath: String? = null,
    val mpvPath: String? = null,
    val lastProvider: String? = null,
    val searchProviders: Set<String>? = null,
    val useBuiltInPlayer: Boolean = true,
    val autoSelectSubtitles: Boolean = true,
    val dns: DnsChoice = DnsChoice.Auto,
)

object SettingsStore {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<DesktopSettings> = _state.asStateFlow()
    val value: DesktopSettings get() = _state.value

    private fun load(): DesktopSettings = runCatching {
        if (AppDirs.settingsFile.exists()) desktopMapper.readValue<DesktopSettings>(AppDirs.settingsFile)
        else DesktopSettings()
    }.getOrElse {
        it.printStackTrace()
        DesktopSettings()
    }

    fun update(transform: (DesktopSettings) -> DesktopSettings) {
        _state.update(transform)
        save(_state.value)
    }

    @Synchronized
    private fun save(settings: DesktopSettings) {
        val tmp = File(AppDirs.settingsFile.parentFile, "settings.json.tmp")
        // Retries: antivirus or backup tools can briefly lock the file on Windows
        for (attempt in 1..5) {
            val result = runCatching {
                desktopMapper.writeValue(tmp, settings)
                Files.move(tmp.toPath(), AppDirs.settingsFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            if (result.isSuccess) return
            System.err.println("Saving settings failed (attempt $attempt): ${result.exceptionOrNull()}")
            if (attempt == 5) result.exceptionOrNull()?.printStackTrace() else Thread.sleep(200L * attempt)
        }
    }
}
