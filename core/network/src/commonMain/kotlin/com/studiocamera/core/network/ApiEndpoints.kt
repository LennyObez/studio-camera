package com.studiocamera.core.network

object ApiEndpoints {
    // Legacy endpoints (kept for backward compatibility with stored device data)
    const val BIND = "/v1/bind"
    const val INFO = "/v1/info"
    const val HEALTH = "/v1/health"
    const val AUTH_REFRESH = "/v1/auth/refresh"
    const val WEBSOCKET = "/ws"

    // Media (brand-agnostic — used when browsing media on the camera's HTTP server)
    const val MEDIA_LIST = "/v1/media"
    fun mediaDetail(id: String) = "/v1/media/$id"
    fun mediaDownload(id: String) = "/v1/media/$id/download"
    fun mediaDelete(id: String) = "/v1/media/$id"
    fun mediaThumbnail(id: String) = "/v1/media/$id/thumbnail"
}
