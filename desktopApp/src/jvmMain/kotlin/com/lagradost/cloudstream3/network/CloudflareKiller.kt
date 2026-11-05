package com.lagradost.cloudstream3.network

import com.lagradost.cloudstream3.USER_AGENT
import okhttp3.Headers
import okhttp3.Headers.Companion.toHeaders
import okhttp3.Interceptor
import okhttp3.Response
import java.net.URI

/**
 * Desktop version of the app's CloudflareKiller. On Android it solves Cloudflare challenges in a
 * WebView; there is no WebView here, so it replays cookies already saved for a host and otherwise
 * lets the request through unchanged (enough for sites without an active challenge).
 */
class CloudflareKiller : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"

        fun parseCookieMap(cookie: String): Map<String, String> =
            cookie.split(";").associate {
                val split = it.split("=")
                (split.getOrNull(0)?.trim() ?: "") to (split.getOrNull(1)?.trim() ?: "")
            }.filter { it.key.isNotBlank() && it.value.isNotBlank() }
    }

    val savedCookies: MutableMap<String, Map<String, String>> = mutableMapOf()

    fun getCookieHeaders(url: String): Headers =
        getHeaders(emptyMap(), savedCookies[URI(url).host] ?: emptyMap())

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val cookies = savedCookies[request.url.host] ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().headers(getHeaders(request.headers.toMap(), cookies)).build())
    }
}

/** Set headers > set cookies > default headers (same as the app's helper). */
fun getHeaders(headers: Map<String, String>, cookie: Map<String, String>): Headers {
    val cookieMap =
        if (cookie.isNotEmpty()) mapOf("Cookie" to cookie.entries.joinToString(" ") { "${it.key}=${it.value};" })
        else mapOf()
    return (mapOf("user-agent" to USER_AGENT) + headers + cookieMap).toHeaders()
}
