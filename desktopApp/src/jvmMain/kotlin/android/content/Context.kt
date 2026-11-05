package android.content

import java.io.File

/**
 * Minimal stand-in for android.content.Context.
 *
 * Third-party extensions extend `com.lagradost.cloudstream3.plugins.Plugin` and override
 * `load(context: Context)`. Most of them only forward the context or ignore it, so the
 * desktop app hands them this object. Anything Android-specific will throw a clear error.
 */
open class Context {
    open val packageName: String get() = "com.lagradost.cloudstream3.desktop"

    open fun getFilesDir(): File = File(DesktopContextHolder.dataDir, "files").apply { mkdirs() }
    open fun getCacheDir(): File = File(DesktopContextHolder.dataDir, "cache").apply { mkdirs() }
    open fun getApplicationContext(): Context = this

    open fun getString(resId: Int): String = "res:$resId"
    open fun getString(resId: Int, vararg formatArgs: Any?): String = "res:$resId"

    open fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        SharedPreferences.forName(name)

    companion object {
        const val MODE_PRIVATE = 0
    }
}

/** Set by the desktop app at start-up. */
object DesktopContextHolder {
    @JvmStatic
    var dataDir: File = File(System.getProperty("user.home"), ".cloudstreamplus")

    /** An AppCompatActivity because many extensions cast the context they get to one. */
    @JvmStatic
    val instance: androidx.appcompat.app.AppCompatActivity by lazy { androidx.appcompat.app.AppCompatActivity() }
}
