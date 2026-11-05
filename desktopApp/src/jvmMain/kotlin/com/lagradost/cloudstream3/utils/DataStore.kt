package com.lagradost.cloudstream3.utils

import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJsonLiteral

const val PREFERENCES_NAME = "rebuild_preference"

/**
 * Desktop version of the Android app's DataStore (app module): JSON values stored in
 * SharedPreferences. Many extensions read their settings (site URL, ...) through it.
 */
object DataStore {
    /** Kept for old extensions that read JSON through `DataStore.mapper`. */
    val mapper get() = com.lagradost.cloudstream3.mapper

    fun Context.getSharedPrefs(): SharedPreferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun Context.getDefaultSharedPrefs(): SharedPreferences =
        getSharedPreferences("${packageName}_preferences", Context.MODE_PRIVATE)

    fun getFolderName(folder: String, path: String): String = "$folder/$path"

    fun Context.getKeys(folder: String): List<String> {
        val fixedFolder = folder.trimEnd('/') + "/"
        return getSharedPrefs().getAll().keys.filter { it.startsWith(fixedFolder) }
    }

    fun Context.removeKey(folder: String, path: String) = removeKey(getFolderName(folder, path))

    fun Context.containsKey(folder: String, path: String): Boolean = containsKey(getFolderName(folder, path))

    fun Context.containsKey(path: String): Boolean = getSharedPrefs().contains(path)

    fun Context.removeKey(path: String) {
        val prefs = getSharedPrefs()
        if (prefs.contains(path)) prefs.edit().remove(path).apply()
    }

    fun Context.removeKeys(folder: String): Int {
        val keys = getKeys("$folder/")
        val editor = getSharedPrefs().edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
        return keys.size
    }

    fun <T> Context.setKey(path: String, value: T) {
        getSharedPrefs().edit().putString(path, value?.toJsonLiteral()).apply()
    }

    fun <T> Context.setKey(folder: String, path: String, value: T) = setKey(getFolderName(folder, path), value)

    fun <T : Any> Context.getKey(path: String, valueType: Class<T>): T? = try {
        getSharedPrefs().getString(path, null)?.let { parseJson(it, valueType.kotlin) }
    } catch (_: Exception) {
        null
    }

    fun <T : Any> String.toKotlinObject(valueType: Class<T>): T = parseJson(this, valueType.kotlin)

    inline fun <reified T : Any> String.toKotlinObject(): T = parseJson(this)

    inline fun <reified T : Any> Context.getKey(path: String, defVal: T?): T? = try {
        getSharedPrefs().getString(path, null)?.let { parseJson<T>(it) } ?: defVal
    } catch (_: Exception) {
        null
    }

    inline fun <reified T : Any> Context.getKey(path: String): T? = getKey(path, null)

    inline fun <reified T : Any> Context.getKey(folder: String, path: String): T? =
        getKey(getFolderName(folder, path), null)

    inline fun <reified T : Any> Context.getKey(folder: String, path: String, defVal: T?): T? =
        getKey(getFolderName(folder, path), defVal) ?: defVal
}
