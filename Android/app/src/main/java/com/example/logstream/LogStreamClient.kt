package com.example.logstream

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
    private val onStatusChanged: (String) -> Unit
) {

    private val client = OkHttpClient()

    private var webSocket: WebSocket? = null

    private var sendJob: Job? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    private val logChannel = Channel<LogEvent>(
        capacity = 500
    )

    private val collector = LogcatCollector(
        deviceId = "android-01",
        output = logChannel
    )

    fun connect() {
        Log.d(TAG, "connect() called, url=$serverUrl")

        if (webSocket != null) {
            Log.d(TAG, "Already connected/connecting")
            return
        }

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

                    startSender()
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: okhttp3.Response?
                ) {
                    Log.e(TAG, "onFailure", t)

                    this@LogStreamClient.webSocket = null

                    stopSender()

                    onStatusChanged(
                        "Connection failed: ${t.message}"
                    )
                }

                override fun onClosing(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {
                    Log.d(
                        TAG,
                        "onClosing code=$code reason=$reason"
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
                        "onClosed code=$code reason=$reason"
                    )

                    this@LogStreamClient.webSocket = null

                    stopSender()

                    onStatusChanged("Disconnected")
                }
            }
        )
    }

    fun startLogcat() {
        Log.d(TAG, "Starting Logcat collector")

        collector.start(scope)

        onStatusChanged("Streaming")
    }

    fun stopLogcat() {
        Log.d(TAG, "Stopping Logcat collector")

        collector.stop()

        onStatusChanged("Connected")
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
                        "No WebSocket connection; dropping log"
                    )
                    continue
                }

                val json = JSONObject().apply {
                    put("device_id", event.deviceId)
                    put("seq", event.seq)
                    put("timestamp", event.timestamp)
                    put("priority", event.priority)
                    put("tag", event.tag)
                    put("pid", event.pid)
                    put("uid", event.uid)
                    put("message", event.message)
                }

                val sent = socket.send(json.toString())

                if (!sent) {
                    Log.w(
                        TAG,
                        "WebSocket rejected log event seq=${event.seq}"
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
        stopLogcat()

        stopSender()

        webSocket?.close(
            1000,
            "User disconnected"
        )

        webSocket = null

        onStatusChanged("Disconnected")
    }
}