package ai.busymate.whitelabel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BusymateLaunchTest {
    private val base = "https://busymate.ai/chat/w/demos?channel=android&locale=en"

    @Test fun appendsVersionAndHostAppAndKeepsHostQuery() {
        assertEquals("$base&sdkVersion=1.0.5&hostAppId=ai.busymate.sample",
            BusymateLaunch.launchUrl(base, "1.0.5", "ai.busymate.sample"))
    }

    @Test fun publicEntryPointUsesTheSdkVersionConstant() {
        assertTrue(BusymateLaunch.chatUrl(base, "ai.busymate.sample").contains("sdkVersion=" + BusymateLaunch.SDK_VERSION))
        assertTrue(BusymateLaunch.isValidSdkVersion(BusymateLaunch.SDK_VERSION))
    }

    @Test fun prereleaseKeptAndPlusEncoded() {
        assertEquals("$base&sdkVersion=1.1.0-beta.2%2B7", BusymateLaunch.launchUrl(base, "1.1.0-beta.2+7", null))
    }

    @Test fun hostAppIdLeftOutUnlessItMatches() {
        for (bad in listOf("", "a", "1abc.def", "ai busymate", "ai.busymate&x=1", "a".repeat(129))) {
            assertFalse(bad, BusymateLaunch.launchUrl(base, "1.0.5", bad).contains("hostAppId"))
        }
        assertTrue(BusymateLaunch.launchUrl(base, "1.0.5", null).endsWith("sdkVersion=1.0.5"))
        assertTrue(BusymateLaunch.isValidHostAppId("a".repeat(128)))
    }

    @Test fun invalidVersionLeftOutAndBareUrlGetsAQuery() {
        assertEquals(base, BusymateLaunch.launchUrl(base, "bad version", null))
        assertEquals("https://busymate.ai/chat/w/demos?sdkVersion=1.0.5",
            BusymateLaunch.launchUrl("https://busymate.ai/chat/w/demos", "1.0.5", null))
    }

    @Test fun earlierLaunchParametersReplacedAndFragmentKept() {
        assertEquals("https://busymate.ai/chat/w/demos?channel=android&sdkVersion=1.0.5&hostAppId=ai.busymate.sample#x",
            BusymateLaunch.launchUrl("https://busymate.ai/chat/w/demos?sdkVersion=9&channel=android&hostAppId=evil#x", "1.0.5", "ai.busymate.sample"))
    }
}
