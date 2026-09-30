package com.example.graphcat

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

private const val TAG = "LogStreamClient"

class LogStreamClient(
    private val serverUrl: String,
    private val appResolver: AppResolver,
    private val onStatusChanged: (String) -> Unit
) {

    private val client = OkHttpClient()

    private var webSocket: WebSocket? = null

    private var sendJob: Job? = null

    private var shouldStream = false

    private val scope =
        CoroutineScope(Dispatchers.IO)

    private val logChannel =
        Channel<LogEvent>(capacity = 500)

    private val collector =
        LogcatCollector(
            deviceId = "android-01",
            output = logChannel,
            appResolver = appResolver
        )

    fun startStreaming() {

        shouldStream = true

        if (webSocket != null) {
            return
        }

        connect()
    }

    private fun connect() {

        Log.d(
            TAG,
            "Connecting to $serverUrl"
        )

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

                    Log.d(TAG, "WebSocket opened")

                    onStatusChanged("Connected")

                    startSender()

                    if (shouldStream) {
                        collector.start(scope)

                        onStatusChanged("Streaming")
                    }
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: okhttp3.Response?
                ) {

                    Log.e(
                        TAG,
                        "WebSocket failure",
                        t
                    )

                    this@LogStreamClient.webSocket = null

                    stopSender()

                    if (shouldStream) {
                        onStatusChanged(
                            "Connection failed: ${t.message}"
                        )
                    }
                }

                override fun onClosing(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {

                    Log.d(
                        TAG,
                        "WebSocket closing: $code $reason"
                    )

                    onStatusChanged("Closing")
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {

                    Log.d(
                        TAG,
                        "WebSocket closed: $code $reason"
                    )

                    this@LogStreamClient.webSocket = null

                    stopSender()

                    if (shouldStream) {
                        onStatusChanged("Disconnected")
                    }
                }
            }
        )
    }

    private fun startSender() {

        if (sendJob?.isActive == true) {
            return
        }

        sendJob = scope.launch {

            for (event in logChannel) {

                if (!isActive) {
                    break
                }

                val socket = webSocket

                if (socket == null) {
                    Log.w(
                        TAG,
                        "No WebSocket; dropping seq=${event.seq}"
                    )

                    continue
                }

                val json = JSONObject().apply {

                    put(
                        "device_id",
                        event.deviceId
                    )

                    put(
                        "seq",
                        event.seq
                    )

                    put(
                        "timestamp",
                        event.timestamp
                    )

                    put(
                        "priority",
                        event.priority
                    )

                    put(
                        "tag",
                        event.tag
                    )

                    put(
                        "pid",
                        event.pid
                    )

                    put(
                        "tid",
                        event.tid
                    )

                    put(
                        "uid",
                        event.uid
                    )

                    put(
                        "package",
                        event.packageName
                    )

                    put(
                        "message",
                        event.message
                    )
                }

                val sent =
                    socket.send(json.toString())

                if (!sent) {

                    Log.w(
                        TAG,
                        "WebSocket rejected seq=${event.seq}"
                    )
                }
            }
        }
    }

    private fun stopSender() {

        sendJob?.cancel()
        sendJob = null
    }

    fun disconnect() {

        shouldStream = false

        collector.stop()

        stopSender()

        webSocket?.close(
            1000,
            "User stopped Graphcat"
        )

        webSocket = null

        onStatusChanged("Disconnected")
    }
}