package com.lagradost.cloudstream4.desktop

import com.lagradost.cloudstream3.app
import okhttp3.Cache
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** DNS resolver choices, like the Android app's "DNS over HTTPS" setting. */
enum class DnsChoice(val label: String, val url: String?, val bootstrap: List<String>) {
    Auto("Automatic (Cloudflare DNS over HTTPS, system DNS if unreachable)", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    System("System DNS", null, emptyList()),
    Cloudflare("Cloudflare", "https://cloudflare-dns.com/dns-query", listOf("1.1.1.1", "1.0.0.1")),
    Google("Google", "https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4")),
    AdGuard("AdGuard (non-filtering)", "https://dns-unfiltered.adguard.com/dns-query", listOf("94.140.14.140", "94.140.14.141")),
    Quad9("Quad9", "https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112")),
}

/**
 * Builds the shared HTTP client used by the extensions (`app`), the way the Android app does:
 * - DNS over HTTPS, so sites blocked by the ISP's DNS (common in France) still resolve;
 * - trusts Java's CAs, Windows' CAs and Let's Encrypt's 2025+ roots (missing from older JDKs);
 * - falls back to HTTP/1.1 for hosts whose HTTP/2 breaks with Java's TLS stack;
 * - a 50 MB HTTP cache.
 */
object DesktopNetwork {
    private val trust: X509TrustManager by lazy { CompositeTrustManager(buildTrustManagers()) }
    private val sslContext: SSLContext by lazy { SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) } }
    private val cache by lazy { Cache(File(AppDirs.cache, "http_cache"), 50L * 1024 * 1024) }

    fun install(choice: DnsChoice = SettingsStore.value.dns) {
        runCatching { app.baseClient = buildClient(choice) }
            .onFailure { System.err.println("Network setup failed, using defaults: $it") }
        installImageLoader()
    }

    /**
     * Posters go through the same client as the extensions (DNS over HTTPS, trusted roots), with a browser
     * user agent: sites blocked by the ISP or behind Cloudflare also host their posters there.
     */
    private fun installImageLoader() {
        val userAgent = Interceptor { chain ->
            val request = chain.request()
            chain.proceed(
                if (request.header("User-Agent") != null) request
                else request.newBuilder().header("User-Agent", com.lagradost.cloudstream3.USER_AGENT).build()
            )
        }
        coil3.SingletonImageLoader.setUnsafe { context ->
            coil3.ImageLoader.Builder(context)
                .components {
                    add(coil3.network.okhttp.OkHttpNetworkFetcherFactory(callFactory = {
                        app.baseClient.newBuilder().addInterceptor(userAgent).build()
                    }))
                }
                .build()
        }
    }

    fun buildClient(choice: DnsChoice): OkHttpClient {
        val bootstrap = OkHttpClient.Builder().sslSocketFactory(sslContext.socketFactory, trust).build()
        val builder = bootstrap.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .cache(cache)
            .addInterceptor(Http1Fallback)
        dnsFor(choice, bootstrap)?.let { builder.dns(it) }
        return builder.build()
    }

    private fun dnsFor(choice: DnsChoice, bootstrap: OkHttpClient): Dns? {
        val url = choice.url ?: return null
        val doh = DnsOverHttps.Builder()
            .client(bootstrap)
            .url(url.toHttpUrl())
            .bootstrapDnsHosts(choice.bootstrap.map { InetAddress.getByName(it) })
            .build()
        return if (choice == DnsChoice.Auto) FallbackDns(doh) else doh
    }

    /** DoH first (bypasses ISP blocking), system DNS when DoH itself can't be reached. */
    private class FallbackDns(private val primary: Dns) : Dns {
        override fun lookup(hostname: String): List<InetAddress> = try {
            primary.lookup(hostname)
        } catch (e: UnknownHostException) {
            try {
                Dns.SYSTEM.lookup(hostname)
            } catch (_: UnknownHostException) {
                throw e
            }
        }
    }

    /** Retries over HTTP/1.1 when a server resets HTTP/2 streams with PROTOCOL_ERROR. */
    private object Http1Fallback : Interceptor {
        private val http1Hosts = ConcurrentHashMap.newKeySet<String>()

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val host = request.url.host
            if (host in http1Hosts) return http1(chain).newCall(request).execute()
            return try {
                chain.proceed(request)
            } catch (e: IOException) {
                if (e.message?.contains("PROTOCOL_ERROR") != true) throw e
                http1Hosts += host
                http1(chain).newCall(request).execute()
            }
        }

        private fun http1(chain: Interceptor.Chain): OkHttpClient =
            app.baseClient.newBuilder()
                .protocols(listOf(Protocol.HTTP_1_1))
                .apply { interceptors().remove(Http1Fallback) }
                .connectTimeout(chain.connectTimeoutMillis().toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(chain.readTimeoutMillis().toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
    }

    private fun buildTrustManagers(): List<X509TrustManager> = buildList {
        // Java's own CAs
        add(defaultTrustManager(null))
        // Windows' CAs (includes enterprise / local roots)
        runCatching { add(defaultTrustManager(KeyStore.getInstance("Windows-ROOT").apply { load(null, null) })) }
        // Bundled roots that may be missing from the runtime
        runCatching {
            val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
            val factory = CertificateFactory.getInstance("X.509")
            listOf("isrg-root-ye.pem", "isrg-root-yr.pem").forEach { name ->
                DesktopNetwork::class.java.getResourceAsStream("/certs/$name")?.use { input ->
                    store.setCertificateEntry(name, factory.generateCertificate(input))
                }
            }
            add(defaultTrustManager(store))
        }
    }

    private fun defaultTrustManager(store: KeyStore?): X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(store) }
            .trustManagers.filterIsInstance<X509TrustManager>().first()

    /** Accepts a chain when any of the trust managers accepts it. */
    private class CompositeTrustManager(private val managers: List<X509TrustManager>) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) =
            firstAccepting { it.checkClientTrusted(chain, authType) }

        override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) =
            firstAccepting { it.checkServerTrusted(chain, authType) }

        override fun getAcceptedIssuers(): Array<X509Certificate> =
            managers.flatMap { it.acceptedIssuers.asList() }.toTypedArray()

        private fun firstAccepting(check: (X509TrustManager) -> Unit) {
            var error: CertificateException? = null
            for (manager in managers) {
                try {
                    check(manager); return
                } catch (e: CertificateException) {
                    error = error ?: e
                }
            }
            throw error ?: CertificateException("No trust manager")
        }
    }
}
