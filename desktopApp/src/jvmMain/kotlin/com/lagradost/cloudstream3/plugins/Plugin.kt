package com.lagradost.cloudstream3.plugins

import android.content.Context
import android.content.res.Resources

/**
 * Desktop version of the Android-only `Plugin` class (app module).
 * Same package, name and members so that extensions compiled against the Android app link here.
 */
abstract class Plugin : BasePlugin() {
    /** Called when the plugin is loaded. Falls back to the cross-platform [BasePlugin.load]. */
    @Throws(Throwable::class)
    open fun load(context: Context) {
        load()
    }

    /** Video click actions (cast to app, etc.) are Android-only; ignored on desktop. */
    fun registerVideoClickAction(element: Any?) {
        android.util.Log.i(PLUGIN_TAG, "Ignoring VideoClickAction on desktop: $element")
    }

    /** Resources are not available on desktop. */
    var resources: Resources? = null

    /** Settings screens are Android views; kept for binary compatibility only. */
    var openSettings: ((context: Context) -> Unit)? = null
}
