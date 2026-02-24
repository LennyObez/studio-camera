package com.studiocamera.core.network

import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.SessionError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class SafeApiCallTest {

    @Test
    fun successfulCallReturnsSuccess() = runTest {
        val result = safeApiCall { "hello" }

        assertIs<ApiResult.Success<String>>(result)
        assertEquals("hello", result.data)
    }

    @Test
    fun timeoutReturnsDeviceUnreachable() = runTest {
        val result = safeApiCall(timeout = 50.milliseconds) {
            kotlinx.coroutines.delay(200)
            "should not reach"
        }

        assertIs<ApiResult.Error>(result)
        assertIs<SessionError.DeviceUnreachable>(result.error)
    }

    @Test
    fun httpError401ReturnsError() = runTest {
        // Test that a 401 response is handled as an error.
        // The exact error type depends on how the Ktor client is configured
        // (with expectSuccess=true it throws ClientRequestException -> WrongCredentials,
        // without it the response is returned normally and the caller checks status).
        // Here we test the fundamental error handling by using expectSuccess=true.
        val engine = MockEngine { _ ->
            respond(
                content = "Unauthorized",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }
        val client = HttpClient(engine) {
            expectSuccess = true
        }

        val result = safeApiCall {
            client.get("http://fake/api").bodyAsText()
        }

        // Should be an error (the exact type depends on the Ktor version's exception handling)
        assertIs<ApiResult.Error>(result)
    }

    @Test
    fun httpError403ReturnsError() = runTest {
        val engine = MockEngine { _ ->
            respond(
                content = "Forbidden",
                status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, "text/plain")
            )
        }
        val client = HttpClient(engine) {
            expectSuccess = true
        }

        val result = safeApiCall {
            client.get("http://fake/api").bodyAsText()
        }

        assertIs<ApiResult.Error>(result)
    }

    @Test
    fun genericExceptionReturnsDeviceUnreachable() = runTest {
        val result = safeApiCall<String> {
            throw RuntimeException("Connection refused")
        }

        assertIs<ApiResult.Error>(result)
        assertIs<SessionError.DeviceUnreachable>(result.error)
    }

    @Test
    fun withTimeoutAndRetryRetriesOnRetryableError() = runTest {
        var attempts = 0
        val result = withTimeoutAndRetry(
            timeout = 5000.milliseconds,
            maxRetries = 2
        ) {
            attempts++
            if (attempts < 3) {
                throw RuntimeException("Transient failure")
            }
            "success"
        }

        assertIs<ApiResult.Success<String>>(result)
        assertEquals("success", result.data)
        assertEquals(3, attempts)
    }

    @Test
    fun withTimeoutAndRetryStopsAtMaxRetries() = runTest {
        var attempts = 0
        val result = withTimeoutAndRetry<String>(
            timeout = 5000.milliseconds,
            maxRetries = 2
        ) {
            attempts++
            throw RuntimeException("Always fails")
        }

        assertIs<ApiResult.Error>(result)
        // maxRetries=2 means initial + 2 retries = 3 attempts total
        assertEquals(3, attempts)
    }

    @Test
    fun safeApiCallWithBreakerFailsFastWhenCircuitIsOpen() = runTest {
        val breaker = CircuitBreaker(failureThreshold = 1, cooldownMs = 60_000L)
        // Trip the breaker open
        breaker.recordFailure()

        var blockCalled = false
        val result = safeApiCallWithBreaker<String>(breaker) {
            blockCalled = true
            "should not reach"
        }

        assertIs<ApiResult.Error>(result)
        assertIs<SessionError.CircuitOpen>(result.error)
        assertTrue(!blockCalled, "Block should not be called when circuit is open")
    }

    @Test
    fun safeApiCallWithBreakerRecordsSuccessOnBreaker() = runTest {
        val breaker = CircuitBreaker(failureThreshold = 3, cooldownMs = 60_000L)

        val result = safeApiCallWithBreaker(breaker) { "ok" }

        assertIs<ApiResult.Success<String>>(result)
        assertEquals(CircuitBreaker.State.Closed, breaker.currentState)
    }

    @Test
    fun safeApiCallWithBreakerRecordsFailureOnBreaker() = runTest {
        val breaker = CircuitBreaker(failureThreshold = 2, cooldownMs = 60_000L)

        safeApiCallWithBreaker<String>(breaker) { throw RuntimeException("fail1") }
        val result = safeApiCallWithBreaker<String>(breaker) { throw RuntimeException("fail2") }

        assertIs<ApiResult.Error>(result)
        assertEquals(CircuitBreaker.State.Open, breaker.currentState)
    }

    @Test
    fun withTimeoutAndRetryExhaustsAllAttemptsForRetryableErrors() = runTest {
        // DeviceUnreachable (from RuntimeException) is retryable, so all attempts run.
        var attempts = 0

        val result = withTimeoutAndRetry<String>(
            timeout = 5000.milliseconds,
            maxRetries = 3
        ) {
            attempts++
            throw RuntimeException("Transient")
        }

        assertIs<ApiResult.Error>(result)
        assertEquals(4, attempts) // initial + 3 retries
    }
}
