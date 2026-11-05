package com.lagradost.cloudstream4.desktop

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * NewPipe needs a Downloader to be registered before use (YouTube extension, trailers).
 * The Android app registers an OkHttp one in CommonActivity; this is the desktop equivalent.
 */
class NewPipeDownloader : Downloader() {
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    // Headers the JDK client refuses to set itself
    private val restricted = setOf("connection", "content-length", "expect", "host", "upgrade")

    override fun execute(request: Request): Response {
        val body = request.dataToSend()
        val builder = HttpRequest.newBuilder(URI(request.url()))
            .timeout(Duration.ofSeconds(30))
            .method(
                request.httpMethod(),
                if (body != null) HttpRequest.BodyPublishers.ofByteArray(body) else HttpRequest.BodyPublishers.noBody()
            )
        if (request.headers().keys.none { it.equals("User-Agent", true) }) builder.header("User-Agent", USER_AGENT)
        for ((name, values) in request.headers()) {
            if (name.lowercase() in restricted) continue
            values.forEach { builder.header(name, it) }
        }

        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 429) throw ReCaptchaException("reCaptcha Challenge requested", request.url())
        return Response(
            response.statusCode(),
            "",
            response.headers().map(),
            response.body(),
            response.uri().toString(),
        )
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"

        @Volatile private var initialized = false

        fun init() {
            if (initialized) return
            synchronized(this) {
                if (initialized) return
                NewPipe.init(NewPipeDownloader())
                initialized = true
            }
        }
    }
}
