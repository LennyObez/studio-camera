package com.studiocamera.core.network

import co.touchlab.kermit.Logger
import com.studiocamera.core.domain.model.ApiResult
import com.studiocamera.core.domain.model.SessionError
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

suspend fun <T> safeApiCall(
    timeout: Duration = 30.seconds,
    block: suspend () -> T
): ApiResult<T> {
    return try {
        val result = withTimeout(timeout) { block() }
        ApiResult.Success(result)
    } catch (e: TimeoutCancellationException) {
        Logger.w("Network") { "API call timed out" }
        ApiResult.Error(SessionError.DeviceUnreachable)
    } catch (e: CancellationException) {
        throw e // Don't catch coroutine cancellation
    } catch (e: ClientRequestException) {
        Logger.w("Network") { "Client error: ${e.response.status}" }
        when (e.response.status) {
            HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden ->
                ApiResult.Error(SessionError.WrongCredentials)
            else ->
                ApiResult.Error(SessionError.Unknown(e))
        }
    } catch (e: ServerResponseException) {
        Logger.w("Network") { "Server error: ${e.response.status}" }
        ApiResult.Error(SessionError.Unknown(e))
    } catch (e: Exception) {
        Logger.e("Network", e) { "API call failed" }
        ApiResult.Error(SessionError.DeviceUnreachable)
    }
}

suspend fun <T> withTimeoutAndRetry(
    timeout: Duration = 30.seconds,
    maxRetries: Int = 2,
    block: suspend () -> T
): ApiResult<T> {
    var lastError: SessionError = SessionError.Unknown()
    repeat(maxRetries + 1) { attempt ->
        val result = safeApiCall(timeout) { block() }
        when (result) {
            is ApiResult.Success -> return result
            is ApiResult.Error -> {
                lastError = result.error
                if (!result.error.isRetryable || attempt == maxRetries) {
                    return result
                }
                Logger.d("Network") { "Retry attempt ${attempt + 1}/$maxRetries" }
            }
        }
    }
    return ApiResult.Error(lastError)
}
