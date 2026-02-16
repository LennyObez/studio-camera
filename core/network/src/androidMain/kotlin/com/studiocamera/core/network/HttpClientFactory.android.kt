package com.studiocamera.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

actual fun createPlatformHttpClient(tlsConfig: TlsConfig): HttpClient {
    return HttpClient(OkHttp) {
        engine {
            config {
                // TLS configuration
                val connectionSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_3)
                    .build()
                connectionSpecs(listOf(connectionSpec))

                // Certificate fingerprint pinning (TOFU)
                if (tlsConfig.trustedFingerprint != null) {
                    val trustManager = FingerprintTrustManager(tlsConfig.trustedFingerprint)
                    val sslContext = SSLContext.getInstance("TLS")
                    sslContext.init(null, arrayOf<TrustManager>(trustManager), null)
                    sslSocketFactory(sslContext.socketFactory, trustManager)
                }

                connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
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
