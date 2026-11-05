package com.lagradost.cloudstream4

import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lagradost.cloudstream4.compose.LocalFocusOutlineDefault
import com.lagradost.cloudstream4.desktop.AppLog
import com.lagradost.cloudstream4.desktop.DesktopNetwork
import com.lagradost.cloudstream4.desktop.PluginLoader
import com.lagradost.cloudstream4.desktop.SelfTest
import com.lagradost.cloudstream4.desktop.UnixSocketDirFix
import com.lagradost.cloudstream4.desktop.res.Res
import com.lagradost.cloudstream4.desktop.res.extension_24px
import com.lagradost.cloudstream4.desktop.res.home_icon_filled_24
import com.lagradost.cloudstream4.desktop.res.home_icon_outline_24
import com.lagradost.cloudstream4.desktop.res.library_icon
import com.lagradost.cloudstream4.desktop.res.library_icon_filled
import com.lagradost.cloudstream4.desktop.res.search_icon
import com.lagradost.cloudstream4.desktop.res.settings_icon_filled
import com.lagradost.cloudstream4.desktop.res.settings_icon_outline
import com.lagradost.cloudstream4.desktop.ui.AppState
import com.lagradost.cloudstream4.desktop.ui.CsIcon
import com.lagradost.cloudstream4.desktop.ui.DetailsScreen
import com.lagradost.cloudstream4.desktop.ui.ExtensionsScreen
import com.lagradost.cloudstream4.desktop.ui.HomeScreen
import com.lagradost.cloudstream4.desktop.ui.LibraryScreen
import com.lagradost.cloudstream4.desktop.ui.LocalWindowController
import com.lagradost.cloudstream4.desktop.ui.PlayerScreen
import com.lagradost.cloudstream4.desktop.ui.Screen
import com.lagradost.cloudstream4.desktop.ui.SearchScreen
import com.lagradost.cloudstream4.desktop.ui.SettingsScreen
import com.lagradost.cloudstream4.desktop.ui.WindowController
import com.lagradost.cloudstream4.generated.resources.default_icon
import com.lagradost.cloudstream4.theme.CloudStreamTheme
import com.lagradost.cloudstream4.theme.CloudStreamThemeMode
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.system.exitProcess
import com.lagradost.cloudstream4.generated.resources.Res as SharedRes

fun main(args: Array<String>) {
    AppLog.install()
    UnixSocketDirFix.apply()
    DesktopNetwork.install()
    if ("--selftest" in args || System.getenv("CSP_SELFTEST") in setOf("1", "installed", "links")) {
        exitProcess(SelfTest.run())
    }
    application {
        val windowState = rememberWindowState(size = DpSize(1360.dp, 860.dp), position = WindowPosition.PlatformDefault)
        val app = remember { AppState() }
        val controller = remember(windowState) {
            object : WindowController {
                private var previous: WindowPlacement = WindowPlacement.Floating
                override val isFullscreen: Boolean get() = windowState.placement == WindowPlacement.Fullscreen
                override fun setFullscreen(value: Boolean) {
                    if (value == isFullscreen) return
                    if (value) {
                        previous = windowState.placement
                        windowState.placement = WindowPlacement.Fullscreen
                    } else windowState.placement = previous
                }
            }
        }
        Window(
            onCloseRequest = ::exitApplication,
            title = "CloudStream",
            icon = painterResource(SharedRes.drawable.default_icon),
            state = windowState,
            onPreviewKeyEvent = {
                // The player handles its own keys
                if (app.current is Screen.Player) return@Window false
                if (it.type == KeyEventType.KeyDown && ((it.key == Key.DirectionLeft && it.isAltPressed) || it.key == Key.Escape) && app.canGoBack) {
                    app.back(); true
                } else false
            }
        ) {
            window.minimumSize = java.awt.Dimension(960, 620)
            CloudStreamTheme(mode = CloudStreamThemeMode.Dark) {
                CompositionLocalProvider(
                    LocalFocusOutlineDefault provides false,
                    LocalWindowController provides controller,
                ) {
                    App(app)
                }
            }
        }
    }
}

private data class RailEntry(val screen: Screen, val icon: DrawableResource, val selectedIcon: DrawableResource, val label: String)

// Same entries as res/menu/bottom_nav_menu.xml (downloads replaced by extensions)
private val rail = listOf(
    RailEntry(Screen.Home, Res.drawable.home_icon_outline_24, Res.drawable.home_icon_filled_24, "Home"),
    RailEntry(Screen.Search, Res.drawable.search_icon, Res.drawable.search_icon, "Search"),
    RailEntry(Screen.Library, Res.drawable.library_icon, Res.drawable.library_icon_filled, "Library"),
    RailEntry(Screen.Extensions, Res.drawable.extension_24px, Res.drawable.extension_24px, "Extensions"),
    RailEntry(Screen.Settings, Res.drawable.settings_icon_outline, Res.drawable.settings_icon_filled, "Settings"),
)

@Composable
private fun App(app: AppState) {
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        PluginLoader.loadAllInstalled()
        // Dev helper: CSP_OPEN="<provider name>|<url>" opens that title at start-up
        System.getenv("CSP_OPEN")?.split("|", limit = 2)?.takeIf { it.size == 2 }?.let { (api, url) ->
            app.open(Screen.Details(api, url, url))
        }
    }
    LaunchedEffect(app.snackbar) {
        app.snackbar?.let {
            app.snackbar = null
            snackbar.showSnackbar(it)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { _ ->
        val current = app.current
        if (current is Screen.Player) {
            PlayerScreen(app, current)
            return@Scaffold
        }
        Row(Modifier.fillMaxSize()) {
            NavRail(app)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (current) {
                    Screen.Home -> HomeScreen(app)
                    Screen.Search -> SearchScreen(app)
                    Screen.Library -> LibraryScreen(app)
                    Screen.Extensions -> ExtensionsScreen(app)
                    Screen.Settings -> SettingsScreen()
                    is Screen.Details -> DetailsScreen(app, current)
                    is Screen.Player -> {}
                }
            }
        }
    }
}

/** NavigationRailView from activity_main.xml: 62dp, unlabeled, centered, primary tint when selected. */
@Composable
private fun NavRail(app: AppState) {
    Column(
        Modifier.width(62.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rail.forEach { entry ->
            val selected = app.rootScreen == entry.screen
            @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
            TooltipArea(
                tooltip = {
                    Text(
                        entry.label,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                },
                delayMillis = 400,
                tooltipPlacement = TooltipPlacement.ComponentRect(anchor = Alignment.CenterEnd, alignment = Alignment.CenterEnd, offset = DpOffset(8.dp, 0.dp)),
            ) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                        .clickable { app.navigateRoot(entry.screen) }
                        .pointerHoverIcon(PointerIcon.Hand),
                    contentAlignment = Alignment.Center
                ) {
                    CsIcon(
                        if (selected) entry.selectedIcon else entry.icon,
                        size = 26.dp,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        contentDescription = entry.label,
                    )
                }
            }
        }
    }
}
