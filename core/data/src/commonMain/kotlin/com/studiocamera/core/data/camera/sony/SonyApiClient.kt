package com.studiocamera.core.data.camera.sony

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Sony Camera Remote API JSON-RPC client.
 * All Sony camera commands are sent as JSON-RPC 1.0 over HTTP POST.
 */
class SonyApiClient(
    private val httpClient: HttpClient
) {
    companion object {
        private const val TAG = "SonyApi"
    }

    private var nextId = 1
    private val idMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun call(
        endpoint: String,
        method: String,
        params: List<JsonElement> = emptyList(),
        version: String = "1.0"
    ): JsonObject {
        val id = idMutex.withLock { nextId++ }
        val body = buildJsonObject {
            put("method", method)
            put("params", JsonArray(params))
            put("id", id)
            put("version", version)
        }

        Logger.d(TAG) { "RPC: $method v$version -> $endpoint" }

        val response = httpClient.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }

        val responseText = response.bodyAsText()
        val result = json.decodeFromString<JsonObject>(responseText)

        // Check for error
        val error = result["error"]
        if (error != null && error is JsonArray && error.jsonArray.isNotEmpty()) {
            val errorCode = error.jsonArray[0].jsonPrimitive.content
            val errorMessage = if (error.jsonArray.size > 1) error.jsonArray[1].jsonPrimitive.content else "Unknown"
            Logger.w(TAG) { "Sony API error: $errorCode - $errorMessage" }
            throw SonyApiException(errorCode.toIntOrNull() ?: -1, errorMessage)
        }

        return result
    }

    /**
     * Calls a method and returns the first element of the "result" array.
     */
    suspend fun callForResult(
        endpoint: String,
        method: String,
        params: List<JsonElement> = emptyList(),
        version: String = "1.0"
    ): JsonElement? {
        val response = call(endpoint, method, params, version)
        return response["result"]?.jsonArray?.firstOrNull()
    }

    /**
     * Calls a method and returns the full "result" array.
     */
    suspend fun callForResults(
        endpoint: String,
        method: String,
        params: List<JsonElement> = emptyList(),
        version: String = "1.0"
    ): JsonArray? {
        val response = call(endpoint, method, params, version)
        return response["result"]?.jsonArray
    }
}

class SonyApiException(val code: Int, override val message: String) : Exception(message)
