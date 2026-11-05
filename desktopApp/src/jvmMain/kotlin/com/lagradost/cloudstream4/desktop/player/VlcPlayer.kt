package com.lagradost.cloudstream4.desktop.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.lagradost.cloudstream4.desktop.SettingsStore
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.factory.discovery.strategy.NativeDiscoveryStrategy
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.io.File
import java.nio.ByteBuffer

/** Finds libVLC: a VLC folder next to the app, the folder of a custom vlc.exe, or the installed VLC. */
object Vlc {
    private fun candidateDirs(): List<String> = buildList {
        // Bundled with the app (optional): <app>/vlc
        System.getProperty("compose.application.resources.dir")?.let { add(File(it, "vlc").absolutePath) }
        System.getProperty("user.dir")?.let { add(File(it, "vlc").absolutePath) }
        SettingsStore.value.vlcPath?.let { File(it).parentFile?.absolutePath?.let(::add) }
    }

    private class DirStrategy(private val dirs: List<String>) : NativeDiscoveryStrategy {
        override fun supported() = true
        override fun discover(): String? = dirs.firstOrNull { File(it, "libvlc.dll").isFile || File(it, "libvlc.so").isFile || File(it, "libvlc.dylib").isFile }
        override fun onFound(path: String?) = true
        override fun onSetPluginPath(path: String?) = true
    }

    /** null when libVLC couldn't be found. */
    val factory: MediaPlayerFactory? by lazy {
        runCatching {
            val bundled = NativeDiscovery(DirStrategy(candidateDirs()))
            val system = NativeDiscovery()
            val found = when {
                bundled.discover() -> bundled.discoveredPath()
                system.discover() -> system.discoveredPath()
                else -> null
            }
            println("libVLC: ${found ?: "not found"}")
            if (found == null) return@runCatching null
            MediaPlayerFactory(
                if (System.getenv("CSP_VLC_VERBOSE") == "1") "--verbose=2" else "--quiet",
                "--no-video-title-show",
                // Frames are copied to the CPU for Compose anyway; GPU (DX11) decoding then needs a
                // GPU->CPU converter that can stall, so decode on the CPU.
                "--avcodec-hw=none",
                "--no-snapshot-preview",
                "--network-caching=2000",
                "--sub-text-scale=80",
            )
        }.onFailure { it.printStackTrace() }.getOrNull()
    }

    val available: Boolean get() = factory != null
}

enum class ResizeMode(val label: String) { Fit("Fit"), Fill("Fill"), Zoom("Zoom") }

/**
 * One libVLC player whose frames are copied into a Skia bitmap so Compose can draw them,
 * which lets us put CloudStream's own controls on top of the video.
 */
@Stable
class VlcPlayer : AutoCloseable {
    private val player: EmbeddedMediaPlayer = Vlc.factory!!.mediaPlayers().newEmbeddedMediaPlayer()

    // ---- Frame state, written on VLC's thread and read by Compose
    private val lock = Any()
    private var bitmap: Bitmap? = null
    private var imageBitmap: ImageBitmap? = null
    private var pixels: ByteArray = ByteArray(0)
    var videoWidth by mutableIntStateOf(0); private set
    var videoHeight by mutableIntStateOf(0); private set
    /** Incremented on every new frame so the video Canvas redraws. */
    var frameCounter by mutableLongStateOf(0L); private set

    // ---- Playback state
    var isPlaying by mutableStateOf(false); private set
    var isBuffering by mutableStateOf(true); private set
    var position by mutableLongStateOf(0L); private set
    var duration by mutableLongStateOf(0L); private set
    var error by mutableStateOf<String?>(null); private set
    var ended by mutableStateOf(false); private set
    var volume by mutableIntStateOf(100); private set
    var muted by mutableStateOf(false); private set
    var speed by mutableFloatStateOf(1f); private set
    var resizeMode by mutableStateOf(ResizeMode.Fit)

    @Volatile private var pendingSeek = 0L

    /** Runs [block] with the current frame while VLC can't write to it (avoids tearing / races). */
    fun <T> withFrame(block: (ImageBitmap?) -> T): T = synchronized(lock) { block(imageBitmap) }

    private val surface = CallbackVideoSurface(
        object : BufferFormatCallback {
            override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                synchronized(lock) {
                    val info = ImageInfo(sourceWidth, sourceHeight, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
                    bitmap?.close()
                    bitmap = Bitmap().apply { allocPixels(info) }
                    imageBitmap = bitmap!!.asComposeImageBitmap()
                    pixels = ByteArray(sourceWidth * sourceHeight * 4)
                }
                videoWidth = sourceWidth
                videoHeight = sourceHeight
                return RV32BufferFormat(sourceWidth, sourceHeight)
            }

            override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) {}
            override fun allocatedBuffers(buffers: Array<out ByteBuffer>) {}
        },
        object : RenderCallback {
            override fun lock(mediaPlayer: MediaPlayer) {}
            override fun unlock(mediaPlayer: MediaPlayer) {}
            override fun display(
                mediaPlayer: MediaPlayer,
                nativeBuffers: Array<out ByteBuffer>,
                bufferFormat: BufferFormat,
                displayWidth: Int,
                displayHeight: Int,
            ) {
                synchronized(lock) {
                    val bmp = bitmap ?: return
                    val buffer = nativeBuffers[0]
                    buffer.rewind()
                    if (buffer.remaining() < pixels.size) return
                    buffer.get(pixels, 0, pixels.size)
                    bmp.installPixels(pixels)
                    bmp.notifyPixelsChanged()
                }
                frameCounter++
            }
        },
        true,
        VideoSurfaceAdapters.getVideoSurfaceAdapter(),
    )

    init {
        player.videoSurface().set(surface)
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                isPlaying = true; isBuffering = false; ended = false

                println("VLC: playing, audio tracks=${mediaPlayer.audio().trackCount()} video=${videoWidth}x$videoHeight")
            }
            override fun paused(mediaPlayer: MediaPlayer) { isPlaying = false }
            override fun stopped(mediaPlayer: MediaPlayer) { isPlaying = false }
            override fun finished(mediaPlayer: MediaPlayer) { isPlaying = false; ended = true }
            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                position = newTime
                // Resume only once the clock runs: seeking on the "playing" event stalls VLC
                // when a separate audio track (input slave) is still buffering
                if (pendingSeek > 0 && newTime > 0) {
                    val target = pendingSeek
                    pendingSeek = 0
                    mediaPlayer.submit { mediaPlayer.controls().setTime(target) }
                }
            }
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) { duration = newLength }
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) { isBuffering = newCache < 100f }
            override fun error(mediaPlayer: MediaPlayer) {
                isPlaying = false
                isBuffering = false
                error = "This source can't be played"
            }
        })
    }

    /** Starts a stream. VLC only supports referer and user agent as HTTP headers. */
    fun play(url: String, referer: String?, userAgent: String?, startAt: Long = 0L, audioUrl: String? = null) {
        error = null
        ended = false
        isBuffering = true
        position = startAt
        duration = 0L
        val options = buildList {
            userAgent?.let { add(":http-user-agent=$it") }
            referer?.let { add(":http-referrer=$it") }
            // Video-only streams (e.g. YouTube DASH) come with a separate audio track
            audioUrl?.let { add(":input-slave=$it") }
        }
        // Resume by seeking once playing: ":start-time" stalls VLC when an audio slave is attached
        pendingSeek = startAt
        player.media().play(url, *options.toTypedArray())
        player.audio().setVolume(volume)
        player.controls().setRate(speed)
    }

    /**
     * Polls time/length: with an audio slave attached VLC may not send timeChanged events.
     * Also applies a pending resume seek once the clock runs.
     */
    fun refresh() {
        if (!isPlaying) return
        val t = runCatching { player.status().time() }.getOrDefault(-1L)
        val len = runCatching { player.status().length() }.getOrDefault(-1L)
        if (len > 0 && len != duration) duration = len
        if (t > 0) {
            position = t
            if (pendingSeek > 0) {
                val target = pendingSeek
                pendingSeek = 0
                player.submit { player.controls().setTime(target) }
            }
        }
    }

    fun togglePause() {
        if (ended) {
            player.controls().setTime(0)
            player.controls().play()
            ended = false
        } else player.controls().pause()
    }

    fun seekTo(ms: Long) {
        val target = ms.coerceIn(0, if (duration > 0) duration - 500 else Long.MAX_VALUE)
        position = target
        player.controls().setTime(target)
    }

    fun seekBy(deltaMs: Long) = seekTo(position + deltaMs)

    fun changeVolume(delta: Int) = applyVolume(volume + delta)

    fun applyVolume(value: Int) {
        volume = value.coerceIn(0, 200)
        muted = false
        player.audio().setMute(false)
        player.audio().setVolume(volume)
    }

    fun toggleMute() {
        muted = !muted
        player.audio().setMute(muted)
    }

    fun applySpeed(value: Float) {
        speed = value
        player.controls().setRate(value)
    }

    /** Loads an external subtitle file (downloaded beforehand) or disables subtitles with null. */
    fun setSubtitle(file: File?) {
        if (file == null) player.subpictures().setTrack(-1)
        else player.subpictures().setSubTitleFile(file)
    }

    fun setSubtitleDelay(ms: Long) = player.subpictures().setDelay(ms * 1000)

    fun stop() {
        runCatching { player.controls().stop() }
    }

    override fun close() {
        runCatching { player.controls().stop() }
        runCatching { player.release() }
        synchronized(lock) {
            bitmap?.close()
            bitmap = null
            imageBitmap = null
        }
    }
}
