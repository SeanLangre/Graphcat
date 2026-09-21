package com.example.logstream

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "LogcatCollector"

class LogcatCollector(
    private val deviceId: String,
    private val output: Channel<LogEvent>
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job != null) return

        job = scope.launch(Dispatchers.IO) {
            var process: Process? = null

            try {
                process = ProcessBuilder(
                    "logcat",
                    "-v",
                    "threadtime"
                )
                    .redirectErrorStream(true)
                    .start()

                process.inputStream.bufferedReader().use { reader ->

                    var seq = 0L

                    while (isActive) {
                        val line = reader.readLine() ?: break

                        val event = parseLine(
                            line = line,
                            seq = ++seq
                        ) ?: continue

                        output.send(event)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Logcat reader failed", e)
            } finally {
                process?.destroy()
                process = null
                Log.d(TAG, "Logcat collector stopped")
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun parseLine(
        line: String,
        seq: Long
    ): LogEvent? {
        /*
         * Example threadtime line:
         *
         * 09-21 15:42:10.123  1234  1234 I MyTag: Hello
         *
         * We intentionally keep this parser conservative for now.
         */

        val match = Regex(
            """^\d\d-\d\d\s+\d\d:\d\d:\d\d\.\d+\s+(\d+)\s+(\d+)\s+([VDIWEFS])\s+([^:]+):\s?(.*)$"""
        ).matchEntire(line) ?: return null

        val pid = match.groupValues[1].toIntOrNull() ?: return null
        val priority = match.groupValues[3]
        val tag = match.groupValues[4].trim()
        val message = match.groupValues[5]

        return LogEvent(
            deviceId = deviceId,
            seq = seq,
            timestamp = System.currentTimeMillis(),
            priority = priority,
            tag = tag,
            pid = pid,
            uid = -1,
            message = message
        )
    }
}