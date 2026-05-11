package com.lagradost.nicehttp

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// DNS-over-HTTPS

/**
 * Attaches a DNS-over-HTTPS resolver to this [OkHttpClient.Builder].
 *
 * Passes through to Ktor's OkHttp engine via the `engine { preconfigured = … }` block:
 * val client = HttpClient(OkHttp) {
 *     engine {
 *         preconfigured = OkHttpClient.Builder()
 *             .addGenericDns(
 *                 "https://dns.cloudflare.com/dns-query",
 *                 listOf("1.1.1.1", "1.0.0.1")
 *             )
 *             .build()
 *     }
 * }
 * val requests = Requests(baseClient = client)
 */
fun OkHttpClient.Builder.addGenericDns(url: String, ips: List<String>): OkHttpClient.Builder =
    dns(
        DnsOverHttps.Builder()
            .client(build())
            .url(url.toHttpUrl())
            .bootstrapDnsHosts(ips.map { InetAddress.getByName(it) })
            .build()
    )

// SSL bypass

/**
 * Disables all SSL certificate verification on this [OkHttpClient.Builder].
 */
@Suppress("CustomX509TrustManager")
fun OkHttpClient.Builder.ignoreAllSSLErrors(): OkHttpClient.Builder {
    val naiveTrustManager = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
    }
    val insecureSocketFactory = SSLContext.getInstance("SSL").apply {
        init(null, arrayOf<TrustManager>(naiveTrustManager), SecureRandom())
    }.socketFactory
    sslSocketFactory(insecureSocketFactory, naiveTrustManager)
    hostnameVerifier { _, _ -> true }
    return this
}
