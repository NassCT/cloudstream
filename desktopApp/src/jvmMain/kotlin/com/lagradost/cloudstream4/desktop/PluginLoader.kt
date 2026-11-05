package com.lagradost.cloudstream4.desktop

import android.content.DesktopContextHolder
import com.fasterxml.jackson.module.kotlin.readValue
import com.googlecode.d2j.dex.Dex2jar
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.extractorApis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.zip.ZipFile

/**
 * Loads CloudStream extensions on the JVM.
 *
 * A `.cs3` file is a zip holding `manifest.json` + `classes.dex` (Android bytecode).
 * We translate the dex into a regular jar with dex2jar once (cached next to the .cs3),
 * then load it in its own URLClassLoader whose parent is the app class loader, which
 * provides the CloudStream `library` API and the small Android stubs.
 */
object PluginLoader {
    private const val TAG = "PluginLoader"

    data class LoadedPlugin(
        val file: File,
        val name: String,
        val version: Int,
        val instance: BasePlugin,
        val classLoader: URLClassLoader,
    )

    sealed class LoadState {
        data class Loaded(val providers: List<String>) : LoadState()
        data class Failed(val error: String) : LoadState()
    }

    private val loaded = mutableMapOf<String, LoadedPlugin>()

    private val _states = MutableStateFlow<Map<String, LoadState>>(emptyMap())
    /** Load state per installed plugin internal name. */
    val states: StateFlow<Map<String, LoadState>> = _states.asStateFlow()

    private val _providers = MutableStateFlow<List<MainAPI>>(emptyList())
    /** All MainAPI providers currently registered. */
    val providers: StateFlow<List<MainAPI>> = _providers.asStateFlow()

    init {
        DesktopContextHolder.dataDir = AppDirs.root
        runCatching { NewPipeDownloader.init() }.onFailure { it.printStackTrace() }
    }

    private fun refreshProviders() {
        _providers.value = APIHolder.allProviders.withLock { APIHolder.allProviders.toList() }
            .distinctBy { it.name }
            .sortedBy { it.name.lowercase() }
    }

    /** Bump when the conversion changes so cached jars are rebuilt. */
    private const val CONVERTER_VERSION = 3

    private fun jarFor(cs3: File) = File(cs3.parentFile, "${cs3.nameWithoutExtension}.v$CONVERTER_VERSION.jar")

    /** dex -> jar. The manifest is copied into the jar so the class loader can read it too. */
    private fun convert(cs3: File): File {
        val jar = jarFor(cs3)
        if (jar.exists() && jar.lastModified() >= cs3.lastModified() && jar.length() > 0) return jar
        jar.delete()
        // Jars from older converter versions
        cs3.parentFile.listFiles { f -> f.name.startsWith(cs3.nameWithoutExtension + ".") && f.extension == "jar" }
            ?.forEach { it.delete() }

        ZipFile(cs3).use { zip ->
            val dexEntries = zip.entries().asSequence()
                .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
                .toList()
            require(dexEntries.isNotEmpty()) { "No classes.dex in ${cs3.name}" }

            val uri = URI.create("jar:" + jar.toURI())
            FileSystems.newFileSystem(uri, mapOf("create" to "true")).use { fs ->
                for (entry in dexEntries) {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    Dex2jar.from(bytes)
                        .skipDebug(false)
                        .topoLogicalSort()
                        .skipExceptions(false)
                        .reUseReg(false)
                        // Keep Kotlin's mangled names (e.g. `constructor-impl`) instead of turning '-' into '_',
                        // otherwise calls into the Kotlin stdlib and the library no longer link
                        .dontSanitizeNames(true)
                        .doTranslate(fs.getPath("/"))
                }
                BytecodeCompat.apply(fs).takeIf { it > 0 }?.let {
                    android.util.Log.i(TAG, "${cs3.name}: adapted $it call(s) to the current API")
                }
                zip.getEntry("manifest.json")?.let { m ->
                    Files.write(fs.getPath("/manifest.json"), zip.getInputStream(m).use { it.readBytes() })
                }
            }
        }
        return jar
    }

    private fun readManifest(cs3: File): BasePlugin.Manifest = ZipFile(cs3).use { zip ->
        val entry = zip.getEntry("manifest.json") ?: error("No manifest.json in ${cs3.name}")
        desktopMapper.readValue(zip.getInputStream(entry).use { it.readBytes() })
    }

    /** Loads one .cs3 file. Safe to call from any thread. */
    @Synchronized
    fun load(cs3: File, internalName: String): LoadState {
        val key = cs3.absolutePath
        loaded[key]?.let { return LoadState.Loaded(providersOf(it)) }

        val state = runCatching {
            val manifest = readManifest(cs3)
            val className = manifest.pluginClassName ?: error("Manifest has no pluginClassName")
            val jar = convert(cs3)
            val loader = URLClassLoader(arrayOf(jar.toURI().toURL()), PluginLoader::class.java.classLoader)
            try {
                val instance = loader.loadClass(className).getDeclaredConstructor().newInstance() as BasePlugin
                instance.filename = key
                if (instance is Plugin) instance.load(DesktopContextHolder.instance) else instance.load()
                val plugin = LoadedPlugin(cs3, manifest.name ?: internalName, manifest.version ?: 0, instance, loader)
                loaded[key] = plugin
                android.util.Log.i(TAG, "Loaded $internalName -> ${providersOf(plugin)}")
                LoadState.Loaded(providersOf(plugin))
            } catch (t: Throwable) {
                // Drop what it registered before failing, otherwise broken providers show up in search
                removeRegistrations(key)
                runCatching { loader.close() }
                throw t
            }
        }.getOrElse { t ->
            android.util.Log.e(TAG, "Failed to load ${cs3.name}", t)
            val root = generateSequence(t) { it.cause }.last()
            LoadState.Failed("${root::class.simpleName}: ${root.message}")
        }

        _states.update { it + (internalName to state) }
        refreshProviders()
        return state
    }

    private fun providersOf(plugin: LoadedPlugin): List<String> =
        APIHolder.allProviders.withLock {
            APIHolder.allProviders.filter { it.sourcePlugin == plugin.instance.filename }.map { it.name }
        }

    private fun removeRegistrations(key: String) {
        APIHolder.apis.filter { it.sourcePlugin == key }.forEach { APIHolder.removePluginMapping(it) }
        APIHolder.allProviders.withLock { APIHolder.allProviders.removeAll { it.sourcePlugin == key } }
        extractorApis.withLock { extractorApis.removeAll { it.sourcePlugin == key } }
    }

    @Synchronized
    fun unload(cs3: File, internalName: String) {
        val key = cs3.absolutePath
        val plugin = loaded.remove(key)
        if (plugin != null) {
            runCatching { plugin.instance.beforeUnload() }
            removeRegistrations(key)
            runCatching { plugin.classLoader.close() }
        }
        _states.update { it - internalName }
        refreshProviders()
    }

    /** Loads every installed and enabled plugin. */
    suspend fun loadAllInstalled() = withContext(Dispatchers.IO) {
        recoverOrphans()
        for (p in SettingsStore.value.installedPlugins) {
            if (!p.enabled) continue
            val file = File(p.filePath)
            if (!file.exists()) {
                _states.update { it + (p.internalName to LoadState.Failed("File missing, reinstall the extension")) }
                continue
            }
            load(file, p.internalName)
        }
        refreshProviders()
    }

    /**
     * Extensions downloaded but missing from the settings (e.g. the settings couldn't be saved)
     * are added back, so they aren't silently lost.
     */
    private fun recoverOrphans() {
        val known = SettingsStore.value.installedPlugins.map { File(it.filePath).absolutePath }.toSet()
        val orphans = AppDirs.plugins.walk().maxDepth(2)
            .filter { it.isFile && it.extension == "cs3" && it.absolutePath !in known }
            .mapNotNull { file ->
                runCatching {
                    val manifest = readManifest(file)
                    InstalledPlugin(
                        internalName = file.nameWithoutExtension,
                        name = manifest.name ?: file.nameWithoutExtension,
                        version = manifest.version ?: 0,
                        url = "",
                        repositoryUrl = "",
                        filePath = file.absolutePath,
                    )
                }.getOrNull()
            }.toList()
        // Same extension installed from another repository: already known under its other file
        val missing = orphans
            .filter { o -> SettingsStore.value.installedPlugins.none { it.internalName == o.internalName } }
            .distinctBy { it.internalName }
        if (missing.isEmpty()) return
        android.util.Log.i(TAG, "Recovered ${missing.size} extension(s) missing from the settings")
        SettingsStore.update { s ->
            s.copy(installedPlugins = s.installedPlugins + missing.filter { o -> s.installedPlugins.none { it.internalName == o.internalName } })
        }
    }

    private fun fileNameFor(plugin: OnlinePlugin): String =
        plugin.internalName.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".cs3"

    /** Downloads (or updates) and loads a plugin from a repository. */
    suspend fun install(repoPlugin: RepoPlugin): LoadState = withContext(Dispatchers.IO) {
        val p = repoPlugin.plugin
        val repoDir = File(AppDirs.plugins, repoPlugin.repositoryUrl.hashCode().toUInt().toString(16)).apply { mkdirs() }
        val file = File(repoDir, fileNameFor(p))

        // Unload the old version first, Windows keeps the jar locked while loaded
        unload(file, p.internalName)
        jarFor(file).delete()

        val bytes = app.get(Repositories.convertRawGitUrl(p.url)).body.bytes()
        require(bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            "Downloaded file is not a valid extension (${bytes.size} bytes)"
        }
        file.writeBytes(bytes)

        SettingsStore.update { s ->
            val entry = InstalledPlugin(
                internalName = p.internalName,
                name = p.name,
                version = p.version,
                url = p.url,
                repositoryUrl = repoPlugin.repositoryUrl,
                filePath = file.absolutePath,
                iconUrl = p.iconUrl,
            )
            s.copy(installedPlugins = s.installedPlugins.filterNot { it.internalName == p.internalName } + entry)
        }
        load(file, p.internalName)
    }

    suspend fun uninstall(internalName: String) = withContext(Dispatchers.IO) {
        val entry = SettingsStore.value.installedPlugins.firstOrNull { it.internalName == internalName } ?: return@withContext
        val file = File(entry.filePath)
        unload(file, internalName)
        jarFor(file).delete()
        file.delete()
        SettingsStore.update { s -> s.copy(installedPlugins = s.installedPlugins.filterNot { it.internalName == internalName }) }
    }
}
