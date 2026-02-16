package com.studiocamera.core.common

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

class SanitizingLogWriter(
    private val delegate: LogWriter,
    private val isRelease: Boolean = false
) : LogWriter() {

    private val sensitivePatterns = listOf(
        Regex("(bindToken[\":]\\s*[\"']?)([^\"'\\s,}]+)", RegexOption.IGNORE_CASE),
        Regex("(Authorization[\":]\\s*[\"']?Bearer\\s+)([^\"'\\s,}]+)", RegexOption.IGNORE_CASE),
        Regex("(accessToken[\":]\\s*[\"']?)([^\"'\\s,}]+)", RegexOption.IGNORE_CASE),
        Regex("(refreshToken[\":]\\s*[\"']?)([^\"'\\s,}]+)", RegexOption.IGNORE_CASE),
        Regex("(fingerprint[\":]\\s*[\"']?)([A-Fa-f0-9:]{20,})", RegexOption.IGNORE_CASE),
    )

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val sanitized = if (isRelease) sanitize(message) else message
        delegate.log(severity, sanitized, tag, throwable)
    }

    private fun sanitize(message: String): String {
        var result = message
        for (pattern in sensitivePatterns) {
            result = pattern.replace(result) { match ->
                "${match.groupValues[1]}[REDACTED]"
            }
        }
        return result
    }
}
