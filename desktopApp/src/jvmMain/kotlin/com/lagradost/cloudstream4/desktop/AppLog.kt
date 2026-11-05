package com.lagradost.cloudstream4.desktop

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream

/**
 * The installed app has no console, so stdout/stderr are also written to
 * %APPDATA%\CloudStreamPlus\logs\app.log (the previous run is kept as app.old.log).
 */
object AppLog {
    val file: File get() = File(AppDirs.root, "logs/app.log")

    fun install() {
        runCatching {
            val log = file.apply { parentFile.mkdirs() }
            val old = File(log.parentFile, "app.old.log")
            if (log.exists()) { old.delete(); log.renameTo(old) }
            val out = FileOutputStream(log, true)
            System.setOut(PrintStream(Tee(System.out, out), true, Charsets.UTF_8))
            System.setErr(PrintStream(Tee(System.err, out), true, Charsets.UTF_8))
            Thread.setDefaultUncaughtExceptionHandler { t, e ->
                System.err.println("Uncaught exception in ${t.name}")
                e.printStackTrace()
            }
            println("CloudStream Plus ${System.getProperty("jpackage.app-version") ?: "dev"}, Java ${System.getProperty("java.version")}")
        }
    }

    private class Tee(private val a: OutputStream, private val b: OutputStream) : OutputStream() {
        override fun write(byte: Int) { a.write(byte); synchronized(b) { b.write(byte) } }
        override fun write(buf: ByteArray, off: Int, len: Int) { a.write(buf, off, len); synchronized(b) { b.write(buf, off, len) } }
        override fun flush() { a.flush(); synchronized(b) { b.flush() } }
    }
}
