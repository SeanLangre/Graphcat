package com.example.logstream

import android.content.pm.PackageManager
import android.os.Process
import java.util.concurrent.ConcurrentHashMap

/**
 * Maps the uid column of `logcat -v uid` to something human readable:
 * the app's package name for app uids, or the system user name
 * (e.g. "system", "bluetooth") for platform uids.
 */
class AppResolver(
    private val packageManager: PackageManager
) {
    private val nameByUid = ConcurrentHashMap<Int, String>()

    fun resolveUid(token: String): Int = uidFromLogcatToken(token)

    fun appName(uid: Int, uidToken: String): String {
        if (uid < 0) return uidToken

        return nameByUid.getOrPut(uid) {
            val appId = uid % PER_USER_RANGE

            if (appId < Process.FIRST_APPLICATION_UID) {
                // Platform uid: the logcat name ("system", "root", ...) is the clearest label
                if (uidToken.toIntOrNull() == null) uidToken else "uid:$uid"
            } else {
                // getNameForUid returns "sharedUserId:uid" for shared uids; drop the suffix
                packageManager.getNameForUid(uid)?.substringBefore(':')
                    ?: uidToken
            }
        }
    }
}

private const val PER_USER_RANGE = 100_000

private val APP_UID_NAME = Regex("""^u(\d+)_a(\d+)$""")

/**
 * logcat prints the uid either as a number or as a passwd name.
 * App uids use the fixed scheme "u<user>_a<appId - 10000>"; platform
 * names ("system", "root", ...) return -1 and are shown as-is.
 */
internal fun uidFromLogcatToken(token: String): Int {
    token.toIntOrNull()?.let { return it }

    val match = APP_UID_NAME.matchEntire(token) ?: return -1
    val user = match.groupValues[1].toInt()
    val appId = match.groupValues[2].toInt()

    return user * PER_USER_RANGE + Process.FIRST_APPLICATION_UID + appId
}
