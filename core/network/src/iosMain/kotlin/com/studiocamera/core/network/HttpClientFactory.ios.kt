package com.studiocamera.core.network

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFArrayGetCount
import platform.CoreFoundation.CFArrayGetValueAtIndex
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.credentialForTrust
import platform.Foundation.serverTrust
import platform.Security.SecCertificateCopyData
import platform.Security.SecTrustCopyCertificateChain
import platform.Security.SecTrustRef

private const val TAG = "HttpClientFactory"

@OptIn(ExperimentalForeignApi::class)
actual fun createPlatformHttpClient(tlsConfig: TlsConfig): HttpClient {
    val trustedFingerprint = tlsConfig.trustedFingerprint

    return HttpClient(Darwin) {
        engine {
            configureRequest {
                setAllowsCellularAccess(true)
                setTimeoutInterval(30.0)
            }

            if (trustedFingerprint != null) {
                handleChallenge { session, task, challenge, completionHandler ->
                    handleTlsChallenge(challenge, trustedFingerprint, completionHandler)
                }
            }
        }
    }
}

actual fun createStreamingPlatformHttpClient(): HttpClient {
    return HttpClient(Darwin) {
        engine {
            configureRequest {
                setAllowsCellularAccess(true)
                setTimeoutInterval(0.0)
            }
        }
    }
}

/**
 * TLS certificate pinning via SHA-256 fingerprint comparison.
 *
 * Extracts the leaf certificate from the server trust, computes its SHA-256
 * hash, and compares against the stored [trustedFingerprint].
 * Matches the Android [FingerprintTrustManager] behavior.
 */
@OptIn(ExperimentalForeignApi::class)
private fun handleTlsChallenge(
    challenge: NSURLAuthenticationChallenge,
    trustedFingerprint: String,
    completionHandler: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit
) {
    val protectionSpace = challenge.protectionSpace
    if (protectionSpace.authenticationMethod != NSURLAuthenticationMethodServerTrust) {
        completionHandler(NSURLSessionAuthChallengePerformDefaultHandling, null)
        return
    }

    val serverTrust = protectionSpace.serverTrust
    if (serverTrust == null) {
        Logger.w(TAG) { "No server trust available — cancelling" }
        completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
        return
    }

    try {
        val fingerprint = extractLeafFingerprint(serverTrust)
        if (fingerprint != null && fingerprint.equals(trustedFingerprint, ignoreCase = true)) {
            Logger.d(TAG) { "TLS fingerprint verified" }
            val credential = NSURLCredential.credentialForTrust(serverTrust)
            completionHandler(NSURLSessionAuthChallengeUseCredential, credential)
        } else {
            Logger.w(TAG) { "TLS fingerprint mismatch. Expected: $trustedFingerprint, Got: $fingerprint" }
            completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
        }
    } catch (e: Exception) {
        Logger.e(TAG) { "TLS pinning error: ${e.message}" }
        completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
    }
}

/**
 * Extracts the SHA-256 fingerprint of the leaf (first) certificate from a [SecTrustRef].
 * Returns the fingerprint as a colon-separated uppercase hex string (e.g., "AB:CD:EF:...").
 */
@OptIn(ExperimentalForeignApi::class)
private fun extractLeafFingerprint(trust: SecTrustRef): String? {
    // SecTrustCopyCertificateChain returns a CFArrayRef of SecCertificateRef
    val chain = SecTrustCopyCertificateChain(trust) ?: return null
    if (CFArrayGetCount(chain) == 0L) return null

    // Leaf certificate is at index 0
    val leafCertPtr = CFArrayGetValueAtIndex(chain, 0) ?: return null

    @Suppress("UNCHECKED_CAST")
    val certData = SecCertificateCopyData(
        leafCertPtr.reinterpret()
    ) ?: return null

    val length = CFDataGetLength(certData).toInt()
    val bytePtr = CFDataGetBytePtr(certData) ?: return null

    val derBytes = ByteArray(length)
    for (i in 0 until length) {
        derBytes[i] = bytePtr[i].toByte()
    }

    val hash = sha256(derBytes)
    return hash.joinToString(":") { byte ->
        val hex = (byte.toInt() and 0xFF).toString(16).uppercase()
        if (hex.length == 1) "0$hex" else hex
    }
}

/**
 * Computes SHA-256 using CommonCrypto (CC_SHA256).
 */
@OptIn(ExperimentalForeignApi::class)
private fun sha256(data: ByteArray): ByteArray {
    val digest = ByteArray(32)
    data.usePinned { pinnedData ->
        digest.usePinned { pinnedDigest ->
            platform.CoreCrypto.CC_SHA256(
                pinnedData.addressOf(0),
                data.size.toUInt(),
                pinnedDigest.addressOf(0).reinterpret()
            )
        }
    }
    return digest
}
