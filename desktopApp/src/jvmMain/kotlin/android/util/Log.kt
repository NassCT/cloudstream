package android.util

/** android.util.Log replacement printing to stdout/stderr. Static methods, like the original. */
object Log {
    const val VERBOSE = 2
    const val DEBUG = 3
    const val INFO = 4
    const val WARN = 5
    const val ERROR = 6

    private fun print(level: String, tag: String?, msg: String?, tr: Throwable? = null): Int {
        val line = "$level/$tag: $msg"
        if (level == "E" || level == "W") System.err.println(line) else println(line)
        tr?.printStackTrace()
        return line.length
    }

    @JvmStatic fun v(tag: String?, msg: String?): Int = print("V", tag, msg)
    @JvmStatic fun v(tag: String?, msg: String?, tr: Throwable?): Int = print("V", tag, msg, tr)
    @JvmStatic fun d(tag: String?, msg: String?): Int = print("D", tag, msg)
    @JvmStatic fun d(tag: String?, msg: String?, tr: Throwable?): Int = print("D", tag, msg, tr)
    @JvmStatic fun i(tag: String?, msg: String?): Int = print("I", tag, msg)
    @JvmStatic fun i(tag: String?, msg: String?, tr: Throwable?): Int = print("I", tag, msg, tr)
    @JvmStatic fun w(tag: String?, msg: String?): Int = print("W", tag, msg)
    @JvmStatic fun w(tag: String?, msg: String?, tr: Throwable?): Int = print("W", tag, msg, tr)
    @JvmStatic fun w(tag: String?, tr: Throwable?): Int = print("W", tag, tr?.message, tr)
    @JvmStatic fun e(tag: String?, msg: String?): Int = print("E", tag, msg)
    @JvmStatic fun e(tag: String?, msg: String?, tr: Throwable?): Int = print("E", tag, msg, tr)
    @JvmStatic fun getStackTraceString(tr: Throwable?): String = tr?.stackTraceToString() ?: ""
    @JvmStatic fun isLoggable(tag: String?, level: Int): Boolean = true
}
