package com.lagradost.cloudstream4.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.scale
import com.lagradost.cloudstream4.desktop.res.netflix_skip_forward
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream4.desktop.AppDirs
import com.lagradost.cloudstream4.desktop.ExternalPlayer
import com.lagradost.cloudstream4.desktop.History
import com.lagradost.cloudstream4.desktop.PluginLoader
import com.lagradost.cloudstream4.desktop.WatchEntry
import com.lagradost.cloudstream4.desktop.player.ResizeMode
import com.lagradost.cloudstream4.desktop.player.VlcPlayer
import com.lagradost.cloudstream4.desktop.player.Vlc
import com.lagradost.cloudstream4.desktop.res.Res
import com.lagradost.cloudstream4.desktop.res.baseline_fullscreen_24
import com.lagradost.cloudstream4.desktop.res.baseline_fullscreen_exit_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_arrow_back_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_aspect_ratio_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_check_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_ondemand_video_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_open_in_new_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_skip_next_rounded_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_speed_24
import com.lagradost.cloudstream4.desktop.res.ic_baseline_volume_up_24
import com.lagradost.cloudstream4.desktop.res.ic_outline_subtitles_24
import com.lagradost.cloudstream4.desktop.res.netflix_pause
import com.lagradost.cloudstream4.desktop.res.netflix_play
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private val blankCursor: PointerIcon by lazy {
    PointerIcon(Toolkit.getDefaultToolkit().createCustomCursor(BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "blank"))
}

private enum class PlayerMenu { Sources, Subtitles, Speed }

private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private fun qualityName(q: Int) = if (q <= 0) "Auto" else "${q}p"

private fun ExtractorLink.playable() = type != ExtractorLinkType.TORRENT && type != ExtractorLinkType.MAGNET

private val itagRegex = Regex("[?&]itag=(\\d+)")
// YouTube audio formats, best first: Opus 160k, AAC 128k, Opus 70k/50k, HE-AAC 48k
private val audioItagPreference = listOf("251", "140", "250", "249", "139")

/** Separate audio track for video-only links, preferring the best known quality. */
private fun ExtractorLink.bestAudioUrl(): String? = audioTracks.minByOrNull { a ->
    itagRegex.find(a.url)?.groupValues?.get(1)?.let { audioItagPreference.indexOf(it) }?.takeIf { it >= 0 } ?: audioItagPreference.size
}?.url

private suspend fun downloadSubtitle(sub: SubtitleFile): File = withContext(Dispatchers.IO) {
    val dir = File(AppDirs.cache, "subtitles").apply { mkdirs() }
    val ext = Regex("\\.(srt|vtt|ass|ssa)(\\?|$)", RegexOption.IGNORE_CASE).find(sub.url)?.groupValues?.get(1)?.lowercase() ?: "vtt"
    val file = File(dir, "${sub.url.hashCode().toUInt().toString(16)}.$ext")
    if (!file.exists() || file.length() == 0L) {
        file.writeBytes(app.get(sub.url, headers = sub.headers ?: emptyMap()).body.bytes())
    }
    file
}

@Composable
fun PlayerScreen(app: AppState, screen: Screen.Player) {
    if (!Vlc.available || !com.lagradost.cloudstream4.desktop.SettingsStore.value.useBuiltInPlayer) {
        ExternalPlayerFallback(app, screen)
        return
    }
    BuiltInPlayer(app, screen)
}

/** Without libVLC: list the links and open them in mpv / VLC as separate apps. */
@Composable
private fun ExternalPlayerFallback(app: AppState, screen: Screen.Player) {
    val api = remember(screen.apiName) { PluginLoader.providers.value.firstOrNull { it.name == screen.apiName } }
    val item = screen.items[screen.index]
    val links = remember(screen) { mutableStateListOf<ExtractorLink>() }
    val subs = remember(screen) { mutableStateListOf<SubtitleFile>() }
    var loading by remember(screen) { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(screen) {
        runCatching {
            withContext(Dispatchers.IO) {
                api?.loadLinks(item.data, false, { s -> if (subs.none { it.url == s.url }) subs.add(s) }) { l ->
                    if (links.none { it.url == l.url }) links.add(l)
                }
            }
        }
        loading = false
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(Res.drawable.ic_baseline_arrow_back_24) { app.back() }
            Spacer(Modifier.width(12.dp))
            Text(item.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            "The built-in player needs VLC (videolan.org). Install it and restart CloudStream, or pick a link to open it in an external player.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (loading) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("Loading links… (${links.size})")
        }
        LazyColumn {
            items(links.sortedByDescending { it.quality }, key = { it.url }) { l ->
                MenuRow("${qualityName(l.quality)}  ·  ${l.name}", l.source + " · " + l.type.name, false) {
                    scope.launch { app.toast(ExternalPlayer.play(screen.showName + " - " + item.title, l, subs.toList()) ?: "Opened in external player") }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BuiltInPlayer(app: AppState, screen: Screen.Player) {
    val api: MainAPI? = remember(screen.apiName) { PluginLoader.providers.value.firstOrNull { it.name == screen.apiName } }
    val item = screen.items[screen.index]
    val window = LocalWindowController.current
    val scope = rememberCoroutineScope()

    val player = remember(screen) { VlcPlayer() }
    val links = remember(screen) { mutableStateListOf<ExtractorLink>() }
    val subs = remember(screen) { mutableStateListOf<SubtitleFile>() }
    val failed = remember(screen) { mutableStateMapOf<String, Boolean>() }
    var linksLoading by remember(screen) { mutableStateOf(true) }
    var currentLink by remember(screen) { mutableStateOf<ExtractorLink?>(null) }
    var currentSub by remember(screen) { mutableStateOf<SubtitleFile?>(null) }
    var fatal by remember(screen) { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf<PlayerMenu?>(null) }
    var lastMove by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    val focus = remember { FocusRequester() }

    val hasNext = screen.index < screen.items.lastIndex
    fun playable() = links.filter { it.playable() }.sortedByDescending { it.quality }

    fun start(link: ExtractorLink, at: Long) {
        currentLink = link
        fatal = null
        val headers = link.getAllHeaders()
        val referer = headers.entries.firstOrNull { it.key.equals("referer", true) }?.value
        val ua = headers.entries.firstOrNull { it.key.equals("user-agent", true) }?.value ?: USER_AGENT
        player.play(link.url, referer, ua, at, link.bestAudioUrl())
        currentSub?.let { sub -> scope.launch { runCatching { player.setSubtitle(downloadSubtitle(sub)) } } }
    }

    fun selectSubtitle(sub: SubtitleFile?) {
        currentSub = sub
        scope.launch {
            runCatching { player.setSubtitle(sub?.let { downloadSubtitle(it) }) }
                .onFailure { app.toast("Couldn't load subtitles: ${it.userMessage()}") }
        }
    }

    fun saveProgress() {
        if (player.duration <= 0) return
        History.savePosition(
            WatchEntry(
                apiName = screen.apiName, showUrl = screen.showUrl, showName = screen.showName,
                posterUrl = screen.posterUrl, episodeTitle = item.title, episodeData = item.data,
                episodeIndex = screen.index, position = player.position, duration = player.duration,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    fun goNext() {
        if (!hasNext) return
        saveProgress()
        app.replace(screen.copy(index = screen.index + 1))
    }

    fun exit() {
        if (window.isFullscreen) window.setFullscreen(false)
        app.back()
    }

    DisposableEffect(player) {
        onDispose {
            saveProgress()
            player.close()
        }
    }

    // Load links, start the best one as soon as possible
    LaunchedEffect(screen) {
        if (api == null) {
            fatal = "Provider ${screen.apiName} is not loaded"; linksLoading = false; return@LaunchedEffect
        }
        val resume = History.positionOf(screen.apiName, item.data)?.takeIf { !it.finished }?.position ?: 0L
        val job = launch(Dispatchers.IO) {
            runCatching {
                api.loadLinks(item.data, false, { s -> if (subs.none { it.url == s.url }) subs.add(s) }) { l ->
                    if (links.none { it.url == l.url }) links.add(l)
                }
            }.onFailure { if (links.isEmpty()) fatal = it.userMessage() }
        }
        // Give extensions a few seconds to return the better links, then start anyway
        var waited = 0
        while (job.isActive && (links.none { it.playable() } || waited < 4000)) {
            delay(200); waited += 200
            if (waited > 60_000) break
        }
        val first = playable().firstOrNull()
        if (first != null) {
            // Auto-select subtitles in the system language, like the Android "preferred language"
            if (com.lagradost.cloudstream4.desktop.SettingsStore.value.autoSelectSubtitles) {
                val lang = Locale.getDefault()
                subs.firstOrNull {
                    it.langTag?.startsWith(lang.language, true) == true ||
                        it.lang.contains(lang.getDisplayLanguage(Locale.ENGLISH), true) ||
                        it.lang.contains(lang.getDisplayLanguage(lang), true)
                }?.let { currentSub = it }
            }
            start(first, resume)
        }
        job.join()
        linksLoading = false
        if (currentLink == null) {
            val late = playable().firstOrNull()
            if (late != null) start(late, resume) else if (fatal == null) fatal = "No links found"
        }
    }

    // A source failed: try the next one, like the Android player
    LaunchedEffect(player.error) {
        val err = player.error ?: return@LaunchedEffect
        val cur = currentLink ?: return@LaunchedEffect
        failed[cur.url] = true
        val next = playable().firstOrNull { it.url !in failed }
        if (next != null) {
            app.toast("${cur.name} failed, trying ${next.name}")
            start(next, player.position)
        } else fatal = err
    }

    // Stall watchdog: playing but the clock doesn't move -> treat the source as failed
    LaunchedEffect(currentLink) {
        val link = currentLink ?: return@LaunchedEffect
        var still = 0
        var last = -1L
        var lastFrame = -1L
        while (currentLink == link) {
            delay(1000)
            if (!player.isPlaying || player.ended) { still = 0; last = player.position; lastFrame = player.frameCounter; continue }
            // Stuck = neither the clock nor the picture moves
            if (player.position == last && player.frameCounter == lastFrame) still++ else still = 0
            last = player.position
            lastFrame = player.frameCounter
            if (still >= 15) {
                failed[link.url] = true
                val next = playable().firstOrNull { it.url !in failed }
                if (next != null) {
                    app.toast("${link.name} is stuck, trying ${next.name}")
                    start(next, player.position)
                } else fatal = "This source stopped responding"
                break
            }
        }
    }

    LaunchedEffect(player.ended) {
        if (player.ended) {
            saveProgress()
            if (hasNext) goNext()
        }
    }

    // Periodic save + clock for hiding the controls
    LaunchedEffect(screen) {
        var tick = 0
        while (true) {
            delay(500)
            now = System.currentTimeMillis()
            player.refresh()
            if (++tick % 10 == 0) saveProgress()
        }
    }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val controlsVisible = menu != null || !player.isPlaying || fatal != null || now - lastMove < 3000

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                lastMove = System.currentTimeMillis()
                when (e.key) {
                    Key.Spacebar, Key.K, Key.MediaPlayPause -> player.togglePause()
                    Key.DirectionLeft, Key.J -> player.seekBy(-10_000)
                    Key.DirectionRight, Key.L -> player.seekBy(10_000)
                    Key.DirectionUp -> player.changeVolume(5)
                    Key.DirectionDown -> player.changeVolume(-5)
                    Key.F, Key.F11 -> window.setFullscreen(!window.isFullscreen)
                    Key.M -> player.toggleMute()
                    Key.N -> goNext()
                    Key.Escape -> if (menu != null) menu = null else if (window.isFullscreen) window.setFullscreen(false) else exit()
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .onPointerEvent(PointerEventType.Move) { lastMove = System.currentTimeMillis() }
            .pointerHoverIcon(if (controlsVisible) PointerIcon.Default else blankCursor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { lastMove = System.currentTimeMillis(); if (menu != null) menu = null else player.togglePause() },
                    onDoubleTap = { window.setFullscreen(!window.isFullscreen) },
                )
            }
    ) {
        VideoCanvas(player)

        if ((player.isBuffering || linksLoading) && fatal == null && currentLink?.let { player.isPlaying } != true) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                if (currentLink == null) Text("Loading links… (${links.size})", color = Color.White)
            }
        }

        AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Controls(
                    player = player,
                    title = screen.showName,
                    subtitle = buildString {
                        if (item.title != screen.showName) append(item.title)
                        currentLink?.let { if (isNotEmpty()) append("  ·  "); append("${it.name} ${qualityName(it.quality)}") }
                    },
                    hasNext = hasNext,
                    isFullscreen = window.isFullscreen,
                    seeking = seeking,
                    onSeeking = { seeking = it },
                    onSeekDone = { seeking?.let { f -> player.seekTo((f * player.duration).toLong()) }; seeking = null },
                    onBack = { exit() },
                    onMenu = { menu = it },
                    onNext = { goNext() },
                    onFullscreen = { window.setFullscreen(!window.isFullscreen) },
                    onExternal = {
                        currentLink?.let { l ->
                            player.stop()
                            scope.launch {
                                val err = ExternalPlayer.play(screen.showName + " - " + item.title, l, listOfNotNull(currentSub))
                                app.toast(err ?: "Opened in external player")
                            }
                        }
                    },
                )
            }
        }

        fatal?.let { msg ->
            Column(
                Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(12.dp)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(msg, color = Color.White, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (links.isNotEmpty()) CsBlackButton("Sources", Res.drawable.ic_baseline_ondemand_video_24) { menu = PlayerMenu.Sources }
                    CsWhiteButton("Back") { exit() }
                }
            }
        }

        when (menu) {
            PlayerMenu.Sources -> MenuPanel("Sources", onDismiss = { menu = null }) {
                items(links.sortedByDescending { it.quality }, key = { it.url }) { l ->
                    MenuRow(
                        text = "${qualityName(l.quality)}  ·  ${l.name}",
                        sub = l.source + " · " + l.type.name + if (l.url in failed) " · failed" else "",
                        selected = l.url == currentLink?.url,
                    ) { menu = null; failed.remove(l.url); start(l, player.position) }
                }
            }
            PlayerMenu.Subtitles -> MenuPanel("Subtitles", onDismiss = { menu = null }) {
                item { MenuRow("Off", null, currentSub == null) { menu = null; selectSubtitle(null) } }
                items(subs.sortedBy { it.lang }, key = { it.url }) { s ->
                    MenuRow(s.lang, null, s.url == currentSub?.url) { menu = null; selectSubtitle(s) }
                }
            }
            PlayerMenu.Speed -> MenuPanel("Playback speed", onDismiss = { menu = null }) {
                items(speeds) { s ->
                    MenuRow("${s}x", null, s == player.speed) { menu = null; player.applySpeed(s) }
                }
            }
            null -> {}
        }
    }
}

/** Draws the latest video frame with the selected resize mode. */
@Composable
private fun VideoCanvas(player: VlcPlayer) {
    Canvas(Modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE") val frame = player.frameCounter // redraw on every new frame
        val vw = player.videoWidth
        val vh = player.videoHeight
        if (vw <= 0 || vh <= 0) return@Canvas
        val scale = when (player.resizeMode) {
            ResizeMode.Fit -> minOf(size.width / vw, size.height / vh)
            ResizeMode.Fill -> maxOf(size.width / vw, size.height / vh)
            ResizeMode.Zoom -> maxOf(size.width / vw, size.height / vh) * 1.15f
        }
        val dst = Size(vw * scale, vh * scale)
        val offset = IntOffset(((size.width - dst.width) / 2).roundToInt(), ((size.height - dst.height) / 2).roundToInt())
        player.withFrame { img ->
            if (img != null) drawImage(
                img,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(vw, vh),
                dstOffset = offset,
                dstSize = IntSize(dst.width.roundToInt(), dst.height.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

@Composable
private fun BoxScope.Controls(
    player: VlcPlayer,
    title: String,
    subtitle: String,
    hasNext: Boolean,
    isFullscreen: Boolean,
    seeking: Float?,
    onSeeking: (Float) -> Unit,
    onSeekDone: () -> Unit,
    onBack: () -> Unit,
    onMenu: (PlayerMenu) -> Unit,
    onNext: () -> Unit,
    onFullscreen: () -> Unit,
    onExternal: () -> Unit,
) {
    // Top: back + title
    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(140.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent))))
    Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundIconButton(Res.drawable.ic_baseline_arrow_back_24, onClick = onBack)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RoundIconButton(Res.drawable.ic_baseline_open_in_new_24, iconSize = 22.dp, onClick = onExternal)
    }

    // Center: -10s, play/pause, +10s
    Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(56.dp)) {
        SkipButton(forward = false) { player.seekBy(-10_000) }
        RoundIconButton(
            if (player.isPlaying) Res.drawable.netflix_pause else Res.drawable.netflix_play,
            size = 84.dp, iconSize = 56.dp, onClick = { player.togglePause() }
        )
        SkipButton(forward = true) { player.seekBy(10_000) }
    }

    // Bottom: progress + buttons
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(170.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))
    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
        val fraction = seeking ?: if (player.duration > 0) player.position.toFloat() / player.duration else 0f
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(if (seeking != null) (seeking * player.duration).toLong() else player.position), color = Color.White, fontSize = 13.sp)
            ThinSeekBar(
                value = fraction,
                onValueChange = onSeeking,
                onValueChangeFinished = onSeekDone,
                enabled = player.duration > 0,
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            )
            Text(formatTime(player.duration), color = Color.White, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            PlayerTextButton(Res.drawable.ic_baseline_aspect_ratio_24, player.resizeMode.label) {
                player.resizeMode = ResizeMode.entries[(player.resizeMode.ordinal + 1) % ResizeMode.entries.size]
            }
            PlayerTextButton(Res.drawable.ic_baseline_speed_24, "Speed (${player.speed}x)") { onMenu(PlayerMenu.Speed) }
            PlayerTextButton(Res.drawable.ic_baseline_ondemand_video_24, "Sources") { onMenu(PlayerMenu.Sources) }
            PlayerTextButton(Res.drawable.ic_outline_subtitles_24, "Subtitles") { onMenu(PlayerMenu.Subtitles) }
            if (hasNext) PlayerTextButton(Res.drawable.ic_baseline_skip_next_rounded_24, "Next episode", onClick = onNext)
            Spacer(Modifier.weight(1f))
            RoundIconButton(Res.drawable.ic_baseline_volume_up_24, size = 38.dp, iconSize = 22.dp, tint = if (player.muted) Color.Gray else Color.White) { player.toggleMute() }
            ThinSeekBar(
                value = player.volume / 200f,
                onValueChange = { player.applyVolume((it * 200).roundToInt()) },
                color = Color.White,
                modifier = Modifier.width(110.dp).padding(horizontal = 6.dp),
            )
            Text("${player.volume}%", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(40.dp))
            RoundIconButton(if (isFullscreen) Res.drawable.baseline_fullscreen_exit_24 else Res.drawable.baseline_fullscreen_24, size = 40.dp, onClick = onFullscreen)
        }
    }
}

@Composable
private fun SkipButton(forward: Boolean, onClick: () -> Unit) {
    // Like player_rew / player_ffwd: netflix_skip_forward (mirrored for rewind) with "10" in the middle
    Box(
        Modifier.size(70.dp).clip(CircleShape).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand),
        contentAlignment = Alignment.Center
    ) {
        CsIcon(Res.drawable.netflix_skip_forward, size = 50.dp, tint = Color.White, modifier = if (forward) Modifier else Modifier.scale(-1f, 1f))
        Text("10", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
    }
}

/** Thin ExoPlayer-like seek bar: 4dp track that thickens on hover, round primary thumb. */
@Composable
fun ThinSeekBar(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit = {},
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val active = hovered || dragging
    BoxWithConstraints(
        modifier.height(24.dp).hoverable(interaction)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { o -> onValueChange((o.x / size.width).coerceIn(0f, 1f)); onValueChangeFinished() }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragging = true; onValueChange((o.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = { dragging = false; onValueChangeFinished() },
                    onDragCancel = { dragging = false; onValueChangeFinished() },
                ) { change, _ -> onValueChange((change.position.x / size.width).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val track = if (active) 6.dp else 4.dp
        val v = value.coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(track).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.3f)))
        Box(Modifier.fillMaxWidth(v).height(track).clip(RoundedCornerShape(3.dp)).background(color))
        val thumb = if (active) 16.dp else 12.dp
        Box(Modifier.offset(x = (maxWidth - thumb) * v).size(thumb).clip(CircleShape).background(color))
    }
}

@Composable
private fun PlayerTextButton(icon: DrawableResource, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CsIcon(icon, size = 22.dp, tint = Color.White)
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MenuPanel(title: String, onDismiss: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            Modifier.padding(24.dp).width(380.dp).heightIn(max = 560.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(vertical = 12.dp)
        ) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyColumn(content = content)
        }
    }
}

@Composable
private fun MenuRow(text: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).pointerHoverIcon(PointerIcon.Hand).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, color = if (selected) MaterialTheme.colorScheme.primary else Color.White, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            if (sub != null) Text(sub, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
        }
        if (selected) CsIcon(Res.drawable.ic_baseline_check_24, size = 20.dp, tint = MaterialTheme.colorScheme.primary)
    }
}
