package com.studiocamera.core.network

import android.net.Network
import com.studiocamera.core.common.CameraNetworkBinding
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.ConnectionPool
import okhttp3.ConnectionSpec
import okhttp3.Dns
import okhttp3.TlsVersion
import java.net.InetAddress
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Shared connection pool across both regular and streaming HTTP clients.
 * Evicted when the camera network changes (see [registerPoolEviction]).
 */
private val sharedConnectionPool = ConnectionPool()

/**
 * DNS resolver that resolves hostnames through the bound camera network.
 * Falls back to [Dns.SYSTEM] when no camera network is bound.
 */
private val boundNetworkDns = object : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val network = CameraNetworkBinding.boundNetwork as? Network
        return if (network != null) {
            try {
                network.getAllByName(hostname).toList()
            } catch (_: Exception) {
                Dns.SYSTEM.lookup(hostname)
            }
        } else {
            Dns.SYSTEM.lookup(hostname)
        }
    }
}

/** Registers the pool eviction callback. Idempotent — safe to call more than once. */
private fun registerPoolEviction() {
    CameraNetworkBinding.onNetworkChanged = { sharedConnectionPool.evictAll() }
}

actual fun createPlatformHttpClient(tlsConfig: TlsConfig): HttpClient {
    registerPoolEviction()

    return HttpClient(OkHttp) {
        engine {
            config {
                socketFactory(BoundNetworkSocketFactory())
                dns(boundNetworkDns)
                connectionPool(sharedConnectionPool)

                val connectionSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
                    .build()
                connectionSpecs(listOf(connectionSpec, ConnectionSpec.CLEARTEXT))

                if (tlsConfig.trustedFingerprint != null) {
                    val trustManager = FingerprintTrustManager(tlsConfig.trustedFingerprint)
                    val sslContext = SSLContext.getInstance("TLS")
                    sslContext.init(null, arrayOf<TrustManager>(trustManager), null)
                    sslSocketFactory(sslContext.socketFactory, trustManager)
                }

                connectTimeout(10, TimeUnit.SECONDS)
                readTimeout(30, TimeUnit.SECONDS)
                writeTimeout(30, TimeUnit.SECONDS)
            }
        }
    }
}

actual fun createStreamingPlatformHttpClient(): HttpClient {
    registerPoolEviction()

    return HttpClient(OkHttp) {
        engine {
            config {
                socketFactory(BoundNetworkSocketFactory())
                dns(boundNetworkDns)
                connectionPool(sharedConnectionPool)

                connectionSpecs(listOf(ConnectionSpec.CLEARTEXT))
                connectTimeout(15, TimeUnit.SECONDS)
                readTimeout(0, TimeUnit.SECONDS) // live view streams continuously
                writeTimeout(30, TimeUnit.SECONDS)
            }
        }
    }
}

private class FingerprintTrustManager(
    private val expectedFingerprint: String
) : X509TrustManager {

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val cert = chain?.firstOrNull()
            ?: throw SecurityException("No server certificate")

        val digest = MessageDigest.getInstance("SHA-256")
        val fingerprint = digest.digest(cert.encoded)
            .joinToString(":") { "%02X".format(it) }

        if (!fingerprint.equals(expectedFingerprint, ignoreCase = true)) {
            throw SecurityException(
                "Certificate fingerprint mismatch. Expected: $expectedFingerprint, Got: $fingerprint"
            )
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
}
