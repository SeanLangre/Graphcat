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
                if (uidToken.toIntOrNull() == null) uidToken else platformUidName(uid)
            } else {
                // getNameForUid returns "sharedUserId:uid" for shared uids; drop the suffix
                packageManager.getNameForUid(uid)?.substringBefore(':')
                    ?: isolatedUidName(uid)
                    ?: uidToken
            }
        }
    }
}

private const val PER_USER_RANGE = 100_000

// Isolated processes (renderers, app zygote children) have no package.
// Covers FIRST_APP_ZYGOTE_ISOLATED_UID (hidden API) to LAST_ISOLATED_UID.
private val ISOLATED_APP_IDS = 90_000..99_999

/**
 * Names for the common fixed platform uids, from AOSP's
 * android_filesystem_config.h, for when logcat prints them as numbers.
 */
private val PLATFORM_UID_NAMES = mapOf(
    0 to "root",
    1000 to "system",
    1001 to "radio",
    1002 to "bluetooth",
    1003 to "graphics",
    1004 to "input",
    1005 to "audio",
    1006 to "camera",
    1007 to "log",
    1008 to "compass",
    1009 to "mount",
    1010 to "wifi",
    1011 to "adb",
    1012 to "install",
    1013 to "media",
    1014 to "dhcp",
    1016 to "vpn",
    1017 to "keystore",
    1018 to "usb",
    1019 to "drm",
    1020 to "mdnsr",
    1021 to "gps",
    1023 to "media_rw",
    1024 to "mtp",
    1027 to "nfc",
    1029 to "clat",
    1031 to "mediadrm",
    1036 to "logd",
    1037 to "shared_relro",
    1040 to "mediaex",
    1041 to "audioserver",
    1045 to "debuggerd",
    1046 to "mediacodec",
    1047 to "cameraserver",
    1048 to "firewall",
    1050 to "nvram",
    1051 to "dns",
    1052 to "dns_tether",
    1053 to "webview_zygote",
    1058 to "tombstoned",
    1060 to "ese",
    1061 to "ota_update",
    1066 to "statsd",
    1067 to "incidentd",
    1068 to "secure_element",
    1069 to "lmkd",
    1070 to "llkd",
    1072 to "gpu_service",
    1073 to "network_stack",
    1074 to "gsid",
    1076 to "credstore",
    1080 to "context_hub",
    1081 to "virtualizationservice",
    1082 to "artd",
    1083 to "uwb",
    1084 to "thread_network",
    1085 to "diced",
    1086 to "dmesgd",
    1090 to "sdk_sandbox",
    1092 to "prng_seeder",
    2000 to "shell",
    2001 to "cache",
    2002 to "diag",
    9999 to "nobody",
)

/** Readable name for a platform uid, falling back to "uid:<n>". */
internal fun platformUidName(uid: Int): String =
    PLATFORM_UID_NAMES[uid % PER_USER_RANGE] ?: "uid:$uid"

/** "isolated:<n>" for isolated-process uids, null for anything else. */
internal fun isolatedUidName(uid: Int): String? =
    if (uid % PER_USER_RANGE in ISOLATED_APP_IDS) "isolated:$uid" else null

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
