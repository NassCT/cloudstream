package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType

data class PlayItem(val title: String, val data: String)

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Library : Screen
    data object Extensions : Screen
    data object Settings : Screen
    data class Details(val apiName: String, val url: String, val title: String) : Screen
    data class Player(
        val apiName: String,
        val showUrl: String,
        val showName: String,
        val posterUrl: String?,
        val items: List<PlayItem>,
        val index: Int,
    ) : Screen
}

/** Result of one provider row / call, kept while navigating. */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ok<T>(val value: T) : Loadable<T>
    data class Error(val message: String) : Loadable<Nothing>
}

fun Throwable.userMessage(): String {
    val root = generateSequence(this) { it.cause }.last()
    return when (root) {
        is NotImplementedError -> "Not supported by this provider"
        is java.net.UnknownHostException -> "No connection (${root.message})"
        is java.net.SocketTimeoutException -> "The site took too long to answer"
        else -> root.message?.take(300) ?: root::class.simpleName ?: "Error"
    }
}

@Stable
class HomeState {
    var providerName by mutableStateOf<String?>(null)
    /** One entry per MainPageData section, in the provider's order. */
    val sections = mutableStateListOf<Pair<String, Loadable<List<HomePageList>>>>()
}

@Stable
class SearchState {
    var query by mutableStateOf("")
    var lastQuery by mutableStateOf("")
    var typeFilter by mutableStateOf<Set<TvType>>(emptySet())
    val results = mutableStateMapOf<String, Loadable<List<SearchResponse>>>()
}

/** Window-level actions the screens need (fullscreen for the player). */
interface WindowController {
    val isFullscreen: Boolean
    fun setFullscreen(value: Boolean)
}

val LocalWindowController = staticCompositionLocalOf<WindowController> { error("No window") }

@Stable
class AppState {
    private val backStack = mutableStateListOf<Screen>(Screen.Home)
    val current: Screen get() = backStack.last()
    val canGoBack: Boolean get() = backStack.size > 1

    /** The rail destination currently active (details screens keep their parent highlighted). */
    val rootScreen: Screen get() = backStack.first()

    val home = HomeState()
    val search = SearchState()

    var snackbar by mutableStateOf<String?>(null)

    fun navigateRoot(screen: Screen) {
        backStack.clear()
        backStack.add(screen)
    }

    fun open(screen: Screen) {
        backStack.add(screen)
    }

    /** Replaces the top screen (e.g. next episode in the player). */
    fun replace(screen: Screen) {
        backStack[backStack.lastIndex] = screen
    }

    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    fun openDetails(api: MainAPI?, item: SearchResponse) {
        open(Screen.Details(api?.name ?: item.apiName, item.url, item.name))
    }

    fun toast(message: String) {
        snackbar = message
    }
}
