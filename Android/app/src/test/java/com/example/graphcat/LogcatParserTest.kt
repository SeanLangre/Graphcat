package com.example.graphcat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogcatParserTest {

    private fun parseAll(vararg lines: String): List<LogEntry> {
        val parser = LongFormatParser()
        val entries = mutableListOf<LogEntry>()

        lines.forEachIndexed { i, line ->
            parser.feed(line, moreBuffered = i < lines.lastIndex)
                ?.let { entries += it }
        }

        parser.flush()?.let { entries += it }

        return entries
    }

    @Test
    fun parsesNamedUidHeader() {
        val entry = parseAll(
            "[ 1790602726.677 u0_a940 27446:27549 I/Unity    ]",
            "[GPG] Starting Google Play Games initialization...",
            ""
        ).single()

        assertEquals(1790602726677, entry.timestampMs)
        assertEquals("u0_a940", entry.uidToken)
        assertEquals(27446, entry.pid)
        assertEquals(27549, entry.tid)
        assertEquals("I", entry.priority)
        assertEquals("Unity", entry.tag)
        assertEquals("[GPG] Starting Google Play Games initialization...", entry.message)
    }

    @Test
    fun parsesNumericUidHeaders() {
        val spaced = parseAll("[ 1790602726.677 10940 27446:27549 W/ActivityManager ]", "Slow").single()
        assertEquals("10940", spaced.uidToken)
        assertEquals(27446, spaced.pid)

        val colon = parseAll("[ 1790602726.677 10940:27446:27549 W/ActivityManager ]", "Slow").single()
        assertEquals("10940", colon.uidToken)
        assertEquals(27446, colon.pid)
        assertEquals(27549, colon.tid)
    }

    @Test
    fun parsesHeaderWithoutUid() {
        val entry = parseAll("[ 1790602726.5   123:  456 E/MyTag    ]", "Boom: with colon").single()

        assertNull(entry.uidToken)
        assertEquals(1790602726500, entry.timestampMs)
        assertEquals(123, entry.pid)
        assertEquals(456, entry.tid)
        assertEquals("Boom: with colon", entry.message)
    }

    @Test
    fun keepsColonsAndSpacesInTag() {
        val entry = parseAll(
            "[ 1790602726.972 u0_a940 27446:27751 V/LevelPlaySDK: INTERNAL ]",
            "init cache exists"
        ).single()

        assertEquals("LevelPlaySDK: INTERNAL", entry.tag)
        assertEquals("init cache exists", entry.message)
    }

    @Test
    fun groupsMultiLineEntries() {
        val entries = parseAll(
            "--------- beginning of main",
            "[ 1790602726.972 u0_a940 27446:27751 V/LevelPlaySDK: INTERNAL ]",
            "configurations(",
            "RewardedVideoConfigurations{parallelLoad=2}",
            "null)",
            "",
            "[ 1790602727.000 u0_a940 27446:27549 E/Unity    ]",
            "Could not find FishingManager in the scene!",
            "",
            "  at FishingSetup.Start () [0x00000] in <000>:0 ",
            "",
            ""
        )

        assertEquals(2, entries.size)
        assertEquals("configurations(\nRewardedVideoConfigurations{parallelLoad=2}\nnull)", entries[0].message)
        assertEquals(
            "Could not find FishingManager in the scene!\n\n  at FishingSetup.Start () [0x00000] in <000>:0 ",
            entries[1].message
        )
    }

    @Test
    fun closesEntryOnBlankLineWhenInputIsIdle() {
        val parser = LongFormatParser()

        assertNull(parser.feed("[ 1790602726.677 u0_a940 27446:27549 I/Unity    ]", moreBuffered = true))
        assertNull(parser.feed("Hello", moreBuffered = true))
        assertEquals("Hello", parser.feed("", moreBuffered = false)?.message)

        // A late continuation line reuses the previous header instead of being dropped.
        assertNull(parser.feed("late line", moreBuffered = false))
        val late = parser.flush()!!
        assertEquals("late line", late.message)
        assertEquals("Unity", late.tag)
    }

    @Test
    fun ignoresLinesBeforeFirstHeader() {
        assertEquals(emptyList<LogEntry>(), parseAll("--------- beginning of main", "garbage", ""))
    }

    @Test
    fun decodesAppUidNames() {
        assertEquals(10123, uidFromLogcatToken("u0_a123"))
        assertEquals(1010123, uidFromLogcatToken("u10_a123"))
        assertEquals(1000, uidFromLogcatToken("1000"))
        assertEquals(-1, uidFromLogcatToken("system"))
    }

    @Test
    fun decodesIsolatedUidNames() {
        assertEquals(99000, uidFromLogcatToken("u0_i0"))
        assertEquals(99150, uidFromLogcatToken("u0_i150"))
        assertEquals(1099005, uidFromLogcatToken("u10_i5"))
    }

    @Test
    fun namesNumericPlatformUids() {
        assertEquals("root", platformUidName(0))
        assertEquals("system", platformUidName(1000))
        assertEquals("system", platformUidName(1001000))
        assertEquals("uid:1999", platformUidName(1999))
    }

    @Test
    fun namesIsolatedUids() {
        assertEquals("isolated", isolatedUidName(99085))
        assertEquals("isolated", isolatedUidName(90100))
        assertEquals("isolated", isolatedUidName(uidFromLogcatToken("u0_i0")))
        assertNull(isolatedUidName(10123))
    }

    @Test
    fun formatsLogcatEpochTime() {
        assertEquals("1790602726.972", logcatEpochTime(1790602726972))
        assertEquals("1790602726.005", logcatEpochTime(1790602726005))
        assertEquals("1790602726.000", logcatEpochTime(1790602726000))
    }
}
