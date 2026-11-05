package com.lagradost.cloudstream3

import android.app.Activity
import android.content.DesktopContextHolder
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.utils.Event

/** Desktop version of the app's CommonActivity: the "current activity" is the desktop context. */
object CommonActivity {
    val activity: Activity get() = DesktopContextHolder.instance
}

/** Desktop version of MainActivity: extensions only use the events in its companion. */
open class MainActivity : AppCompatActivity() {
    companion object {
        val afterPluginsLoadedEvent = Event<Boolean>()
        val reloadHomeEvent = Event<Boolean>()
    }
}
