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
    private val output: Channel<LogEvent>,
    private val appResolver: AppResolver
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
                    "threadtime",
                    "-v",
                    "uid"
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
        val parsed = parseThreadtimeLine(line) ?: return null

        val uid = parsed.uidToken
            ?.let { appResolver.resolveUid(it) }
            ?: -1

        return LogEvent(
            deviceId = deviceId,
            seq = seq,
            timestamp = System.currentTimeMillis(),
            priority = parsed.priority,
            tag = parsed.tag,
            pid = parsed.pid,
            uid = uid,
            packageName = parsed.uidToken
                ?.let { appResolver.appName(uid, it) }
                ?: "",
            message = parsed.message
        )
    }
}

internal data class ParsedLine(
    val uidToken: String?,
    val pid: Int,
    val priority: String,
    val tag: String,
    val message: String
)

/*
 * Example `logcat -v threadtime -v uid` line:
 *
 * 09-21 15:42:10.123 u0_a123  1234  1234 I Unity: Hello
 *
 * The uid column is optional so plain threadtime lines still parse.
 * The uid is printed as a passwd name ("u0_a123", "system") or a number.
 */
private val THREADTIME_REGEX = Regex(
    """^\d\d-\d\d\s+\d\d:\d\d:\d\d\.\d+\s+(?:([A-Za-z_][\w.]*|\d+)\s+(?=\d+\s+\d+\s))?(\d+)\s+(\d+)\s+([VDIWEFS])\s+([^:]*?)\s*:\s?(.*)$"""
)

internal fun parseThreadtimeLine(line: String): ParsedLine? {
    val match = THREADTIME_REGEX.matchEntire(line) ?: return null

    return ParsedLine(
        uidToken = match.groupValues[1].ifEmpty { null },
        pid = match.groupValues[2].toIntOrNull() ?: return null,
        priority = match.groupValues[4],
        tag = match.groupValues[5].trim(),
        message = match.groupValues[6]
    )
}
