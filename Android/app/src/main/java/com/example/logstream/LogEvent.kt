package com.example.logstream

data class LogEvent(
    val deviceId: String,
    val seq: Long,
    val timestamp: Long,
    val priority: String,
    val tag: String,
    val pid: Int,
    val tid: Int,
    val uid: Int,
    val packageName: String,
    val message: String
)