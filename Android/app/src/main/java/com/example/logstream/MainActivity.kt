package com.example.logstream

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
import com.example.logstream.ui.theme.LogStreamTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            LogStreamTheme {
                LogStreamScreen()
            }
        }
    }
}

@Composable
fun LogStreamScreen() {

    var connected by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "LogStream",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (connected) {
                "Status: Connected"
            } else {
                "Status: Disconnected"
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                connected = !connected
            }
        ) {
            Text(
                text = if (connected) {
                    "Disconnect"
                } else {
                    "Connect"
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                // We will send a test log here later.
            },
            enabled = connected
        ) {
            Text("Send Test Log")
        }
    }
}