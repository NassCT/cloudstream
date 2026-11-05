package com.lagradost.cloudstream4.desktop

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.File

/**
 * On Windows every NIO selector (java.net.http, so NewPipe/YouTube and image loading) opens a
 * loopback AF_UNIX socket in a temp folder. On some machines that folder refuses them
 * ("Invalid argument: connect": security software, special file systems, long paths) and all
 * networking based on java.net.http breaks. The JDK reads `jdk.net.unixdomain.tmpdir` only once
 * and probing with Java's own AF_UNIX support would freeze it, so the probe uses Winsock directly.
 */
object UnixSocketDirFix {
    @Suppress("FunctionName")
    private interface Ws2 : Library {
        fun WSAStartup(version: Short, data: Pointer): Int
        fun socket(af: Int, type: Int, protocol: Int): Long
        fun bind(s: Long, addr: Pointer, len: Int): Int
        fun listen(s: Long, backlog: Int): Int
        fun connect(s: Long, addr: Pointer, len: Int): Int
        fun closesocket(s: Long): Int
    }

    private const val AF_UNIX = 1
    private const val SOCK_STREAM = 1
    private const val INVALID_SOCKET = -1L

    private val ws2: Ws2? by lazy {
        runCatching {
            Native.load("ws2_32", Ws2::class.java).also { it.WSAStartup(0x0202, Memory(512)) }
        }.getOrNull()
    }

    /** True when an AF_UNIX listener can be created and connected to in [dir]. */
    private fun works(dir: File): Boolean {
        val api = ws2 ?: return true // can't probe: keep the JDK default
        if (!dir.isDirectory && !dir.mkdirs()) return false
        val file = File(dir, "csp_probe_${ProcessHandle.current().pid()}")
        val path = file.absolutePath.toByteArray(Charsets.UTF_8)
        if (path.size >= 108) return false
        file.delete()
        // struct sockaddr_un { u16 sun_family; char sun_path[108]; }
        val addr = Memory(110).apply {
            clear()
            setShort(0, AF_UNIX.toShort())
            write(2, path, 0, path.size)
        }
        val len = 2 + path.size + 1
        var server = INVALID_SOCKET
        var client = INVALID_SOCKET
        return try {
            server = api.socket(AF_UNIX, SOCK_STREAM, 0)
            if (server == INVALID_SOCKET) return true // AF_UNIX not available: JDK falls back to TCP itself
            if (api.bind(server, addr, len) != 0 || api.listen(server, 1) != 0) return false
            client = api.socket(AF_UNIX, SOCK_STREAM, 0)
            client != INVALID_SOCKET && api.connect(client, addr, len) == 0
        } catch (_: Throwable) {
            true
        } finally {
            if (client != INVALID_SOCKET) runCatching { api.closesocket(client) }
            if (server != INVALID_SOCKET) runCatching { api.closesocket(server) }
            file.delete()
        }
    }

    fun apply() {
        if (System.getProperty("jdk.net.unixdomain.tmpdir") != null) return
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return
        if (works(File(System.getProperty("java.io.tmpdir")))) return

        val drive = System.getenv("SystemDrive") ?: "C:"
        val fallback = listOfNotNull(
            System.getenv("LOCALAPPDATA")?.let { "$it\\CloudStreamPlus\\sock" },
            System.getenv("PUBLIC")?.let { "$it\\CloudStreamPlus" },
            "$drive\\ProgramData\\CloudStreamPlus",
            "$drive\\Temp\\CloudStreamPlus",
        ).map(::File).firstOrNull(::works) ?: return
        println("Using ${fallback.path} for local sockets (the temp folder refuses them)")
        System.setProperty("jdk.net.unixdomain.tmpdir", fallback.path)
    }
}
