package androidx.fragment.app

import android.app.Activity

/** Stand-in so extensions that cast their context to a FragmentActivity still link. */
open class FragmentActivity : Activity()
