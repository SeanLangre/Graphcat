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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.graphcat.ui.theme.GraphcatTheme

class MainActivity : ComponentActivity() {

    private var status by mutableStateOf("Disconnected")

    private var serverUrl by mutableStateOf("")

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

        serverUrl = ServerSettings.load(this)

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
                    serverUrl = serverUrl,
                    onServerUrlChange = { url ->
                        ServerSettings.save(this, url)

                        serverUrl = url
                    },
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

private fun isActive(status: String): Boolean =
    status == "Streaming" ||
        status == "Connected" ||
        status == "Starting..."

@Composable
fun GraphcatScreen(
    status: String,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {

    var showServerDialog by remember {
        mutableStateOf(false)
    }

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
                Modifier.height(8.dp)
        )

        Text(
            text = "Server: ${serverAddress(serverUrl)}",
            style =
                MaterialTheme.typography.bodySmall
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        Button(
            onClick = {
                if (isActive(status)) {
                    onStop()
                } else {
                    onStart()
                }
            }
        ) {

            Text(
                text =
                    if (isActive(status)) {
                        "Stop Streaming"
                    } else {
                        "Start Streaming"
                    }
            )
        }

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        // The service reads the URL on start, so only allow changes while stopped.
        OutlinedButton(
            enabled = !isActive(status),
            onClick = {
                showServerDialog = true
            }
        ) {

            Text(text = "Change Server")
        }
    }

    if (showServerDialog) {

        ServerDialog(
            currentUrl = serverUrl,
            onDismiss = {
                showServerDialog = false
            },
            onSave = { url ->
                onServerUrlChange(url)

                showServerDialog = false
            }
        )
    }
}

@Composable
private fun ServerDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {

    var input by remember {
        mutableStateOf(serverAddress(currentUrl))
    }

    val normalized = normalizeServerUrl(input)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Server")
        },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = {
                    Text(text = "IP or hostname")
                },
                supportingText = {
                    Text(
                        text =
                            "e.g. 100.101.102.103 or my-pc.tailnet.ts.net " +
                                "(port 8080 unless you add :port)"
                    )
                },
                isError =
                    input.isNotBlank() && normalized == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                )
            )
        },
        confirmButton = {
            TextButton(
                enabled = normalized != null,
                onClick = {
                    normalized?.let(onSave)
                }
            ) {
                Text(text = "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}
