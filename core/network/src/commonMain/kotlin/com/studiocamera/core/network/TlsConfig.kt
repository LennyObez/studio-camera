package com.studiocamera.core.network

/**
 * TLS configuration for secure device communication.
 * Enforces TLS 1.2+, pins certificate fingerprints (TOFU), and restricts cipher suites.
 */
data class TlsConfig(
    val trustedFingerprint: String? = null,
    val minTlsVersion: String = "TLSv1.2",
    val allowedCipherSuites: List<String> = DEFAULT_CIPHER_SUITES
) {
    companion object {
        val DEFAULT_CIPHER_SUITES = listOf(
            "TLS_AES_256_GCM_SHA384",
            "TLS_AES_128_GCM_SHA256",
            "TLS_CHACHA20_POLY1305_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256"
        )
    }
}
