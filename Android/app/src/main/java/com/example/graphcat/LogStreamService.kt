package com.example.graphcat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

private const val TAG = "LogStreamService"

private const val CHANNEL_ID = "graphcat_service"
private const val NOTIFICATION_ID = 1001

class LogStreamService : Service() {

    private var client: LogStreamClient? = null

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "Service created")

        createNotificationChannel()

        startForeground(
            NOTIFICATION_ID,
            createNotification("Starting Graphcat...")
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_START -> {
                Log.d(TAG, "Starting stream")

                clientFor(ServerSettings.load(this))
                    .startStreaming()
            }

            ACTION_STOP -> {
                Log.d(TAG, "Stopping stream")

                stopStreaming()
            }
        }

        return START_STICKY
    }

    // Reuses the client unless the server URL changed since it was created.
    private fun clientFor(
        serverUrl: String
    ): LogStreamClient {

        client?.let {
            if (it.serverUrl == serverUrl) {
                return it
            }

            it.disconnect()
        }

        return LogStreamClient(
            serverUrl = serverUrl,
            appResolver = AppResolver(packageManager),
            onStatusChanged = { status ->
                Log.d(TAG, "Status: $status")

                updateNotification(status)

                sendStatus(status)
            }
        ).also { client = it }
    }

    private fun stopStreaming() {
        client?.disconnect()

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")

        client?.disconnect()
        client = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Graphcat",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Graphcat background streaming service"
            }

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(
        status: String
    ): Notification {

        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("Graphcat")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(
        status: String
    ) {
        val manager =
            getSystemService(NotificationManager::class.java)

        manager.notify(
            NOTIFICATION_ID,
            createNotification(status)
        )
    }

    private fun sendStatus(
        status: String
    ) {
        sendBroadcast(
            Intent(ACTION_STATUS).apply {
                setPackage(packageName)
                putExtra(EXTRA_STATUS, status)
            }
        )
    }

    companion object {

        const val ACTION_START =
            "com.example.graphcat.START"

        const val ACTION_STOP =
            "com.example.graphcat.STOP"

        const val ACTION_STATUS =
            "com.example.graphcat.STATUS"

        const val EXTRA_STATUS =
            "com.example.graphcat.STATUS_VALUE"
    }
}