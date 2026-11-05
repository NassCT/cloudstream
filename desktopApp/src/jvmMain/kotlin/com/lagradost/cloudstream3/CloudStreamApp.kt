package com.lagradost.cloudstream3

import android.app.Activity
import android.content.Context
import android.content.DesktopContextHolder
import com.lagradost.cloudstream3.utils.DataStore.getKey
import com.lagradost.cloudstream3.utils.DataStore.getKeys
import com.lagradost.cloudstream3.utils.DataStore.removeKey
import com.lagradost.cloudstream3.utils.DataStore.removeKeys
import com.lagradost.cloudstream3.utils.DataStore.setKey

/** Desktop version of the Android Application class: extensions use its companion for settings. */
class CloudStreamApp {
    companion object {
        fun Context.getActivity(): Activity? = this as? Activity

        val context: Context? get() = DesktopContextHolder.instance

        fun <T : Any> getKeyClass(path: String, valueType: Class<T>): T? = context?.getKey(path, valueType)
        fun <T : Any> setKeyClass(path: String, value: T) { context?.setKey(path, value) }
        fun removeKeys(folder: String): Int? = context?.removeKeys(folder)
        fun <T> setKey(path: String, value: T) { context?.setKey(path, value) }
        fun <T> setKey(folder: String, path: String, value: T) { context?.setKey(folder, path, value) }
        inline fun <reified T : Any> getKey(path: String, defVal: T?): T? = context?.getKey(path, defVal)
        inline fun <reified T : Any> getKey(path: String): T? = context?.getKey(path)
        inline fun <reified T : Any> getKey(folder: String, path: String): T? = context?.getKey(folder, path)
        inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? =
            context?.getKey(folder, path, defVal)
        fun getKeys(folder: String): List<String>? = context?.getKeys(folder)
        fun removeKey(folder: String, path: String) { context?.removeKey(folder, path) }
        fun removeKey(path: String) { context?.removeKey(path) }
    }
}

/** Old name of [CloudStreamApp], still used by many extensions. */
class AcraApplication {
    companion object {
        val context: Context? get() = CloudStreamApp.context
        fun removeKeys(folder: String): Int? = CloudStreamApp.removeKeys(folder)
        fun <T> setKey(path: String, value: T) = CloudStreamApp.setKey(path, value)
        fun <T> setKey(folder: String, path: String, value: T) = CloudStreamApp.setKey(folder, path, value)
        inline fun <reified T : Any> getKey(path: String, defVal: T?): T? = CloudStreamApp.getKey(path, defVal)
        inline fun <reified T : Any> getKey(path: String): T? = CloudStreamApp.getKey(path)
        inline fun <reified T : Any> getKey(folder: String, path: String): T? = CloudStreamApp.getKey(folder, path)
        inline fun <reified T : Any> getKey(folder: String, path: String, defVal: T?): T? =
            CloudStreamApp.getKey(folder, path, defVal)
    }
}
