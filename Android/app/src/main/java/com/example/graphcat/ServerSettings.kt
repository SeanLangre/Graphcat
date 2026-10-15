package com.example.graphcat

import android.content.Context
import java.net.URI
import java.net.URISyntaxException

private const val PREFS_NAME = "graphcat_settings"
private const val KEY_SERVER_URL = "server_url"

private const val DEFAULT_PORT = 8080
private const val DEVICE_STREAM_PATH = "/v1/device/stream"

/**
 * The server URL the device streams to, persisted in SharedPreferences.
 * Falls back to the build-time `SERVER_URL` from local.properties.
 */
object ServerSettings {

    fun load(context: Context): String =
        prefs(context).getString(KEY_SERVER_URL, null)
            ?: BuildConfig.SERVER_URL

    fun save(context: Context, serverUrl: String) {
        prefs(context)
            .edit()
            .putString(KEY_SERVER_URL, serverUrl)
            .apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
}

/**
 * The editable part of a server URL: `host`, or `host:port` when the port
 * isn't the default 8080. Full URLs with a non-default scheme or path are
 * returned unchanged so editing them doesn't lose information.
 */
fun serverAddress(serverUrl: String): String {

    val uri = try {
        URI(serverUrl)
    } catch (e: URISyntaxException) {
        return serverUrl
    }

    val host = uri.host ?: return serverUrl

    if (uri.scheme != "ws" || uri.path != DEVICE_STREAM_PATH) {
        return serverUrl
    }

    return if (uri.port == -1 || uri.port == DEFAULT_PORT) {
        host
    } else {
        "$host:${uri.port}"
    }
}

/**
 * Turns user input into a device stream WebSocket URL, or null if invalid.
 *
 * Accepts a bare host or IP (`100.101.102.103`, `my-pc.tail1234.ts.net`),
 * `host:port`, or a full `ws://` / `wss://` URL. A bare host gets port 8080;
 * a missing path becomes `/v1/device/stream`.
 */
fun normalizeServerUrl(input: String): String? {

    val trimmed = input.trim()

    if (trimmed.isEmpty()) {
        return null
    }

    val hasScheme = "://" in trimmed

    val uri = try {
        URI(if (hasScheme) trimmed else "ws://$trimmed")
    } catch (e: URISyntaxException) {
        return null
    }

    val scheme = uri.scheme?.lowercase()

    if (scheme != "ws" && scheme != "wss") {
        return null
    }

    val host = uri.host ?: return null

    val port =
        if (uri.port == -1 && !hasScheme) DEFAULT_PORT else uri.port

    val path =
        if (uri.path.isNullOrEmpty() || uri.path == "/") {
            DEVICE_STREAM_PATH
        } else {
            uri.path
        }

    return try {
        URI(scheme, null, host, port, path, null, null).toString()
    } catch (e: URISyntaxException) {
        null
    }
}
