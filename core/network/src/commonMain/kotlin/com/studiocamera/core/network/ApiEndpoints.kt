package com.studiocamera.core.network

object ApiEndpoints {
    const val BIND = "/v1/bind"
    const val INFO = "/v1/info"
    const val HEALTH = "/v1/health"
    const val WEBSOCKET = "/ws"

    // Camera
    const val CAMERA_CAPTURE = "/v1/camera/capture"
    const val CAMERA_RECORD_START = "/v1/camera/record/start"
    const val CAMERA_RECORD_STOP = "/v1/camera/record/stop"
    const val CAMERA_SETTINGS = "/v1/camera/settings"
    const val CAMERA_FOCUS = "/v1/camera/focus"

    // Media
    const val MEDIA_LIST = "/v1/media"
    fun mediaDetail(id: String) = "/v1/media/$id"
    fun mediaDownload(id: String) = "/v1/media/$id/download"
    fun mediaDelete(id: String) = "/v1/media/$id"
    fun mediaThumbnail(id: String) = "/v1/media/$id/thumbnail"

    // Live view
    const val LIVE_MJPEG = "/v1/live/mjpeg"
    const val LIVE_RTSP = "/v1/live/rtsp"
    const val LIVE_WEBRTC_OFFER = "/v1/live/webrtc/offer"
}
