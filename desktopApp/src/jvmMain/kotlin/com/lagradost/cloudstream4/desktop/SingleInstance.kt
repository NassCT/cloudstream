package com.lagradost.cloudstream4.desktop

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

/**
 * Only one CloudStream window at a time: two instances share the same settings and history
 * files, and the older one would overwrite what the other just saved.
 */
object SingleInstance {
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    /** true when this process got the lock (it's released by the OS when the process exits). */
    fun acquire(): Boolean = runCatching {
        val file = File(AppDirs.root, "app.lock")
        val ch = RandomAccessFile(file, "rw").channel
        val l = ch.tryLock()
        if (l == null) {
            ch.close()
            false
        } else {
            channel = ch
            lock = l
            true
        }
    }.getOrDefault(true) // if locking itself fails, don't block the app
}
