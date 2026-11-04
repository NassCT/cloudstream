package android.content

import java.io.File
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * Replacement for the android.content.SharedPreferences interface (same shape, so extension
 * bytecode using invokeinterface links). Backed by a properties file per preference name.
 */
interface SharedPreferences {
    fun getString(key: String?, defValue: String?): String?
    fun getBoolean(key: String?, defValue: Boolean): Boolean
    fun getInt(key: String?, defValue: Int): Int
    fun getLong(key: String?, defValue: Long): Long
    fun getFloat(key: String?, defValue: Float): Float
    fun contains(key: String?): Boolean
    fun getAll(): Map<String, *>
    fun edit(): Editor

    interface Editor {
        fun putString(key: String?, value: String?): Editor
        fun putBoolean(key: String?, value: Boolean): Editor
        fun putInt(key: String?, value: Int): Editor
        fun putLong(key: String?, value: Long): Editor
        fun putFloat(key: String?, value: Float): Editor
        fun remove(key: String?): Editor
        fun clear(): Editor
        fun commit(): Boolean
        fun apply()
    }

    companion object {
        private val cache = ConcurrentHashMap<String, SharedPreferences>()

        fun forName(name: String): SharedPreferences = cache.getOrPut(name) {
            val dir = File(DesktopContextHolder.dataDir, "shared_prefs").apply { mkdirs() }
            FileSharedPreferences(File(dir, "${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}.properties"))
        }
    }
}

class FileSharedPreferences(private val file: File) : SharedPreferences {
    private val props = Properties().apply {
        if (file.exists()) runCatching { file.inputStream().use { load(it) } }
    }

    private fun get(key: String?): String? = key?.let { synchronized(props) { props.getProperty(it) } }

    override fun getString(key: String?, defValue: String?): String? = get(key) ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = get(key)?.toBooleanStrictOrNull() ?: defValue
    override fun getInt(key: String?, defValue: Int): Int = get(key)?.toIntOrNull() ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = get(key)?.toLongOrNull() ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = get(key)?.toFloatOrNull() ?: defValue
    override fun contains(key: String?): Boolean = get(key) != null
    override fun getAll(): Map<String, *> = synchronized(props) { props.entries.associate { it.key.toString() to it.value } }
    override fun edit(): SharedPreferences.Editor = EditorImpl()

    private inner class EditorImpl : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, String?>()
        private var clear = false

        override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
        override fun putBoolean(key: String?, value: Boolean) = putString(key, value.toString())
        override fun putInt(key: String?, value: Int) = putString(key, value.toString())
        override fun putLong(key: String?, value: Long) = putString(key, value.toString())
        override fun putFloat(key: String?, value: Float) = putString(key, value.toString())
        override fun remove(key: String?) = putString(key, null)
        override fun clear() = apply { clear = true }

        override fun commit(): Boolean = runCatching {
            synchronized(props) {
                if (clear) props.clear()
                pending.forEach { (k, v) -> if (v == null) props.remove(k) else props.setProperty(k, v) }
                file.parentFile?.mkdirs()
                file.outputStream().use { props.store(it, null) }
            }
        }.isSuccess

        override fun apply() {
            commit()
        }
    }
}
