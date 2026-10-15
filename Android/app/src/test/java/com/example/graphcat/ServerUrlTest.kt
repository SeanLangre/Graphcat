package com.example.graphcat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {

    @Test
    fun bareIpGetsDefaultPortAndPath() {
        assertEquals(
            "ws://100.101.102.103:8080/v1/device/stream",
            normalizeServerUrl(" 100.101.102.103 ")
        )
    }

    @Test
    fun magicDnsHostname() {
        assertEquals(
            "ws://my-pc.tail1234.ts.net:8080/v1/device/stream",
            normalizeServerUrl("my-pc.tail1234.ts.net")
        )
    }

    @Test
    fun hostWithPortKeepsPort() {
        assertEquals(
            "ws://100.64.0.1:9000/v1/device/stream",
            normalizeServerUrl("100.64.0.1:9000")
        )
    }

    @Test
    fun fullUrlIsKept() {
        assertEquals(
            "wss://logs.example.com/custom/path",
            normalizeServerUrl("wss://logs.example.com/custom/path")
        )
    }

    @Test
    fun fullUrlWithoutPathGetsDefaultPath() {
        assertEquals(
            "ws://192.168.1.10:8080/v1/device/stream",
            normalizeServerUrl("ws://192.168.1.10:8080/")
        )
    }

    @Test
    fun bracketedIpv6() {
        assertEquals(
            "ws://[fd7a:115c:a1e0::1]:8080/v1/device/stream",
            normalizeServerUrl("[fd7a:115c:a1e0::1]")
        )
    }

    @Test
    fun addressHidesDefaultSchemePortAndPath() {
        assertEquals(
            "100.64.0.1",
            serverAddress("ws://100.64.0.1:8080/v1/device/stream")
        )
    }

    @Test
    fun addressKeepsNonDefaultPort() {
        assertEquals(
            "100.64.0.1:9000",
            serverAddress("ws://100.64.0.1:9000/v1/device/stream")
        )
    }

    @Test
    fun addressKeepsUnusualUrlsWhole() {
        assertEquals(
            "wss://logs.example.com/custom/path",
            serverAddress("wss://logs.example.com/custom/path")
        )
    }

    @Test
    fun addressRoundTrips() {
        val url = "ws://my-pc.tail1234.ts.net:8080/v1/device/stream"

        assertEquals(url, normalizeServerUrl(serverAddress(url)))
    }

    @Test
    fun rejectsInvalidInput() {
        assertNull(normalizeServerUrl(""))
        assertNull(normalizeServerUrl("   "))
        assertNull(normalizeServerUrl("http://100.64.0.1:8080"))
        assertNull(normalizeServerUrl("not a host"))
    }
}
