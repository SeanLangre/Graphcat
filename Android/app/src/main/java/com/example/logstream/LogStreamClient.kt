package com.example.logstream

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

private const val TAG = "LogStreamClient"

class LogStreamClient(
    private val serverUrl: String,
    private val onStatusChanged: (String) -> Unit
) {

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null

    fun connect() {
        Log.d(TAG, "connect() called, url=$serverUrl")

        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {

                override fun onOpen(
                    webSocket: WebSocket,
                    response: okhttp3.Response
                ) {
                    Log.d(TAG, "onOpen")
                    onStatusChanged("Connected")
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: okhttp3.Response?
                ) {
                    Log.e(TAG, "onFailure", t)
                    onStatusChanged("Connection failed: ${t.message}")
                }

                override fun onClosing(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {
                    onStatusChanged("Closing")
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {
                    onStatusChanged("Disconnected")
                }
            }
        )
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }

    fun sendTestLog() {
        val message = """
            {
                "device_id": "android-01",
                "seq": 1,
                "timestamp": ${System.currentTimeMillis()},
                "priority": "INFO",
                "tag": "LogStream",
                "pid": 1234,
                "uid": 10000,
                "message": "Hello from Android!"
            }
        """.trimIndent()

        webSocket?.send(message)
    }
}