package com.lagradost.cloudstream4

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lagradost.cloudstream4.compose.LocalFocusOutlineDefault
import com.lagradost.cloudstream4.desktop.PluginLoader
import com.lagradost.cloudstream4.desktop.SelfTest
import com.lagradost.cloudstream4.desktop.ui.AppState
import com.lagradost.cloudstream4.desktop.ui.DetailsScreen
import com.lagradost.cloudstream4.desktop.ui.ExtensionsScreen
import com.lagradost.cloudstream4.desktop.ui.HomeScreen
import com.lagradost.cloudstream4.desktop.ui.Screen
import com.lagradost.cloudstream4.desktop.ui.SearchScreen
import com.lagradost.cloudstream4.desktop.ui.SettingsScreen
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.default_icon
import com.lagradost.cloudstream4.theme.CloudStreamTheme
import com.lagradost.cloudstream4.theme.CloudStreamThemeMode
import org.jetbrains.compose.resources.painterResource
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    if ("--selftest" in args || System.getenv("CSP_SELFTEST") == "1") {
        exitProcess(SelfTest.run())
    }
    application {
        val windowState = rememberWindowState(size = DpSize(1280.dp, 820.dp), position = WindowPosition.PlatformDefault)
        val app = remember { AppState() }
        Window(
            onCloseRequest = ::exitApplication,
            title = "CloudStream Plus",
            icon = painterResource(Res.drawable.default_icon),
            state = windowState,
            onPreviewKeyEvent = {
                // Alt+Left / Escape / mouse "back" behave like Android's back button
                if (it.type == KeyEventType.KeyDown && ((it.key == Key.DirectionLeft && it.isAltPressed) || it.key == Key.Escape) && app.canGoBack) {
                    app.back(); true
                } else false
            }
        ) {
            window.minimumSize = java.awt.Dimension(900, 600)
            CloudStreamTheme(mode = CloudStreamThemeMode.Dark) {
                CompositionLocalProvider(LocalFocusOutlineDefault provides false) {
                    App(app)
                }
            }
        }
    }
}

private data class RailEntry(val screen: Screen, val icon: String, val label: String)

private val rail = listOf(
    RailEntry(Screen.Home, "⌂", "Home"),
    RailEntry(Screen.Search, "⌕", "Search"),
    RailEntry(Screen.Extensions, "⧉", "Extensions"),
    RailEntry(Screen.Settings, "⚙", "Settings"),
)

@Composable
private fun App(app: AppState) {
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { PluginLoader.loadAllInstalled() }
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
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                Spacer(Modifier.height(12.dp))
                rail.forEach { entry ->
                    NavigationRailItem(
                        selected = app.rootScreen == entry.screen,
                        onClick = { app.navigateRoot(entry.screen) },
                        icon = { Text(entry.icon, fontSize = 22.sp, modifier = Modifier.size(28.dp)) },
                        label = { Text(entry.label) },
                    )
                }
            }
            VerticalDivider(Modifier.fillMaxHeight())
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (val s = app.current) {
                    Screen.Home -> HomeScreen(app)
                    Screen.Search -> SearchScreen(app)
                    Screen.Extensions -> ExtensionsScreen(app)
                    Screen.Settings -> SettingsScreen()
                    is Screen.Details -> DetailsScreen(app, s)
                }
            }
        }
    }
}
