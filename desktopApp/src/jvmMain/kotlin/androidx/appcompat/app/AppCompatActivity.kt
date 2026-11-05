package androidx.appcompat.app

import androidx.fragment.app.FragmentActivity

/**
 * Stand-in for AppCompatActivity. Many extensions do `context as AppCompatActivity` in `load()`
 * to open their settings later; the desktop context is an instance of this class so the cast works.
 */
open class AppCompatActivity : FragmentActivity()
