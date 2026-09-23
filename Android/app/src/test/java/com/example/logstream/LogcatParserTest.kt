package com.example.logstream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogcatParserTest {

    @Test
    fun parsesNamedUid() {
        val parsed = parseThreadtimeLine(
            "09-21 15:42:10.123 u0_a123  1234  1250 I Unity   : Loaded scene Main"
        )!!

        assertEquals("u0_a123", parsed.uidToken)
        assertEquals(1234, parsed.pid)
        assertEquals("I", parsed.priority)
        assertEquals("Unity", parsed.tag)
        assertEquals("Loaded scene Main", parsed.message)
    }

    @Test
    fun parsesNumericUid() {
        val parsed = parseThreadtimeLine(
            "09-21 15:42:10.123 10123  1234  1234 W ActivityManager: Slow operation"
        )!!

        assertEquals("10123", parsed.uidToken)
        assertEquals(1234, parsed.pid)
        assertEquals("ActivityManager", parsed.tag)
    }

    @Test
    fun parsesPlainThreadtimeWithoutUid() {
        val parsed = parseThreadtimeLine(
            "09-21 15:42:10.123  1234  1234 E MyTag: Boom: with colon"
        )!!

        assertNull(parsed.uidToken)
        assertEquals(1234, parsed.pid)
        assertEquals("MyTag", parsed.tag)
        assertEquals("Boom: with colon", parsed.message)
    }

    @Test
    fun rejectsBufferHeaders() {
        assertNull(parseThreadtimeLine("--------- beginning of main"))
    }

    @Test
    fun decodesAppUidNames() {
        assertEquals(10123, uidFromLogcatToken("u0_a123"))
        assertEquals(1010123, uidFromLogcatToken("u10_a123"))
        assertEquals(1000, uidFromLogcatToken("1000"))
        assertEquals(-1, uidFromLogcatToken("system"))
    }
}
