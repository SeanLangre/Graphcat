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
                    "long",
                    "-v",
                    "epoch",
                    "-v",
                    "uid"
                )
                    .redirectErrorStream(true)
                    .start()

                process.inputStream.bufferedReader().use { reader ->

                    val parser = LongFormatParser()
                    var seq = 0L

                    while (isActive) {
                        val line = reader.readLine() ?: break

                        val entry = parser.feed(
                            line = line,
                            moreBuffered = reader.ready()
                        ) ?: continue

                        output.send(toEvent(entry, ++seq))
                    }

                    parser.flush()?.let { output.send(toEvent(it, ++seq)) }
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

    private fun toEvent(
        entry: LogEntry,
        seq: Long
    ): LogEvent {
        val uid = entry.uidToken
            ?.let { appResolver.resolveUid(it) }
            ?: -1

        return LogEvent(
            deviceId = deviceId,
            seq = seq,
            timestamp = entry.timestampMs,
            priority = entry.priority,
            tag = entry.tag,
            pid = entry.pid,
            tid = entry.tid,
            uid = uid,
            packageName = entry.uidToken
                ?.let { appResolver.appName(uid, it) }
                ?: "",
            message = entry.message
        )
    }
}

internal data class LogEntry(
    val timestampMs: Long,
    val uidToken: String?,
    val pid: Int,
    val tid: Int,
    val priority: String,
    val tag: String,
    val message: String
)

/*
 * Example `logcat -v long -v epoch -v uid` entry:
 *
 * [ 1790602726.972 u0_a940 27446:27751 V/LevelPlaySDK: INTERNAL ]
 * UIThread: false Activity: 185699668 af r - configurations(
 * RewardedVideoConfigurations{parallelLoad=2, bidderExclusive=true}
 * null)
 * <blank line>
 *
 * The header brackets the tag, so tags containing ':' or spaces survive,
 * and every line up to the next header belongs to the same entry.
 * The uid is printed as a passwd name ("u0_a123", "system") or a number,
 * and may be followed by ':' instead of a space.
 */
private val LONG_HEADER_REGEX = Regex(
    """^\[\s+(\d+)\.(\d+)\s+(?:([A-Za-z_][\w.]*|\d+)[\s:]\s*)?(\d+):\s*(\d+)\s+([VDIWEFSA])/(.*?)\s*]$"""
)

// "--------- beginning of main", "--------- switch to crash", ...
private val BUFFER_SEPARATOR_REGEX = Regex("""^-{9} (beginning of|switch to) \S+$""")

private data class EntryHeader(
    val timestampMs: Long,
    val uidToken: String?,
    val pid: Int,
    val tid: Int,
    val priority: String,
    val tag: String
)

private fun parseLongHeader(line: String): EntryHeader? {
    val match = LONG_HEADER_REGEX.matchEntire(line) ?: return null
    val g = match.groupValues

    val seconds = g[1].toLongOrNull() ?: return null
    val millis = g[2].padEnd(3, '0').take(3).toLong()

    return EntryHeader(
        timestampMs = seconds * 1000 + millis,
        uidToken = g[3].ifEmpty { null },
        pid = g[4].toIntOrNull() ?: return null,
        tid = g[5].toIntOrNull() ?: return null,
        priority = g[6],
        tag = g[7].trim()
    )
}

/**
 * Groups `logcat -v long` output into whole entries.
 *
 * An entry ends at the next header. Because the next header can be a long
 * time coming, an entry also ends at a blank line when [feed] is told no
 * more input is buffered: logcat writes each entry, including its trailing
 * blank line, in one go. Blank lines inside a message (Unity stack traces
 * have them) are kept as long as more of the entry is already buffered.
 * Lines that arrive after an entry was closed early are sent as a new entry
 * with the same header rather than dropped.
 */
internal class LongFormatParser {
    private var header: EntryHeader? = null
    private val lines = mutableListOf<String>()
    private var open = false

    fun feed(line: String, moreBuffered: Boolean): LogEntry? {
        parseLongHeader(line)?.let { next ->
            val done = flush()
            header = next
            open = true
            return done
        }

        if (BUFFER_SEPARATOR_REGEX.matches(line)) {
            return flush()
        }

        if (!open) {
            if (line.isEmpty() || header == null) return null
            open = true
        }

        lines += line

        return if (line.isEmpty() && !moreBuffered) flush() else null
    }

    fun flush(): LogEntry? {
        val h = header ?: return null
        if (!open) return null

        open = false

        while (lines.lastOrNull()?.isEmpty() == true) {
            lines.removeAt(lines.lastIndex)
        }

        val message = lines.joinToString("\n")
        lines.clear()

        return LogEntry(
            timestampMs = h.timestampMs,
            uidToken = h.uidToken,
            pid = h.pid,
            tid = h.tid,
            priority = h.priority,
            tag = h.tag,
            message = message
        )
    }
}
