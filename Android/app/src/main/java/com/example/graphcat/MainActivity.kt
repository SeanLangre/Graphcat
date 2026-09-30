package com.example.graphcat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.graphcat.ui.theme.GraphcatTheme

class MainActivity : ComponentActivity() {

    private var status by mutableStateOf("Disconnected")

    private val statusReceiver =
        object : BroadcastReceiver() {

            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {

                if (
                    intent?.action ==
                    LogStreamService.ACTION_STATUS
                ) {

                    status =
                        intent.getStringExtra(
                            LogStreamService.EXTRA_STATUS
                        ) ?: "Unknown"
                }
            }
        }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        ContextCompat.registerReceiver(
            this,
            statusReceiver,
            IntentFilter(
                LogStreamService.ACTION_STATUS
            ),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {

            GraphcatTheme {

                GraphcatScreen(
                    status = status,
                    onStart = {
                        startStreaming()
                    },
                    onStop = {
                        stopStreaming()
                    }
                )
            }
        }
    }

    private fun startStreaming() {

        val intent =
            Intent(
                this,
                LogStreamService::class.java
            ).apply {
                action =
                    LogStreamService.ACTION_START
            }

        ContextCompat.startForegroundService(
            this,
            intent
        )

        status = "Starting..."
    }

    private fun stopStreaming() {

        val intent =
            Intent(
                this,
                LogStreamService::class.java
            ).apply {
                action =
                    LogStreamService.ACTION_STOP
            }

        startService(intent)

        status = "Stopping..."
    }

    override fun onDestroy() {

        unregisterReceiver(
            statusReceiver
        )

        super.onDestroy()
    }
}

@Composable
fun GraphcatScreen(
    status: String,
    onStart: () -> Unit,
    onStop: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally,
        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "Graphcat",
            style =
                MaterialTheme.typography.headlineLarge
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        Text(
            text = "Status: $status"
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        Button(
            onClick = {
                if (
                    status == "Streaming" ||
                    status == "Connected" ||
                    status == "Starting..."
                ) {
                    onStop()
                } else {
                    onStart()
                }
            }
        ) {

            Text(
                text =
                    if (
                        status == "Streaming" ||
                        status == "Connected" ||
                        status == "Starting..."
                    ) {
                        "Stop Streaming"
                    } else {
                        "Start Streaming"
                    }
            )
        }
    }
}