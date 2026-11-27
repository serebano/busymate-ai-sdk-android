package ai.busymate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** The visitor token is encrypted at rest, bound to its assistant, and fails closed. Real AES-GCM, in-memory storage. */
class BusymateTokenStoreTest {
    private val token = "eyJ0IjoiYWNtZSJ9.c2lnbmF0dXJl-_~+/=:0123"

    private class Memory : BusymateKeyValue {
        val map = mutableMapOf<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private fun store(values: Memory, key: SecretKey) = BusymateTokenStore(values, BusymateAesGcmSealer { key })

    @Test fun savedTokenIsReadBackByANewStoreWithTheSameKey() {
        val values = Memory(); val key = newKey()
        assertTrue(store(values, key).save("acme", token))
        assertEquals(token, store(values, key).load("acme"))
    }

    @Test fun whatIsStoredIsNotTheToken() {
        val values = Memory()
        store(values, newKey()).save("acme", token)
        val stored = values.map.getValue("bm_v:acme")
        assertFalse(stored.contains(token))
        assertFalse(String(BusymateTokenStore.unhex(stored)!!, Charsets.ISO_8859_1).contains(token))
        assertEquals(setOf("bm_v:acme"), values.map.keys)
    }

    @Test fun everySaveUsesAFreshIv() {
        val values = Memory(); val key = newKey()
        store(values, key).save("acme", token)
        val first = values.map.getValue("bm_v:acme")
        store(values, key).save("acme", token)
        assertNotEquals(first, values.map.getValue("bm_v:acme"))
    }

    @Test fun aTokenIsBoundToItsAssistant() {
        val values = Memory(); val key = newKey()
        store(values, key).save("acme", token)
        // Moving the sealed value under another assistant's name does not open, and is deleted.
        values.map["bm_v:other"] = values.map.getValue("bm_v:acme")
        assertNull(store(values, key).load("other"))
        assertFalse(values.map.containsKey("bm_v:other"))
        assertEquals(token, store(values, key).load("acme"))
    }

    @Test fun tamperedOrForeignValuesFailClosedAndAreRemoved() {
        val key = newKey()
        for (corrupt in listOf<(String) -> String>(
            { it.dropLast(2) + (if (it.endsWith("00")) "01" else "00") },
            { it.take(10) },
            { "zz$it" },
            { "" },
            { "0102" },
        )) {
            val values = Memory()
            store(values, key).save("acme", token)
            values.map["bm_v:acme"] = corrupt(values.map.getValue("bm_v:acme"))
            assertNull(store(values, key).load("acme"))
            assertFalse(values.map.containsKey("bm_v:acme"))
        }
    }

    @Test fun anotherDevicesKeyCannotOpenIt() {
        val values = Memory()
        store(values, newKey()).save("acme", token)
        assertNull(store(values, newKey()).load("acme"))
        assertTrue(values.map.isEmpty())
    }

    @Test fun clearForgetsTheTokenInMemoryAndAtRest() {
        val values = Memory(); val key = newKey()
        val store = store(values, key)
        store.save("acme", token)
        store.clear("acme")
        assertNull(store.load("acme"))
        assertTrue(values.map.isEmpty())
    }

    @Test fun aDeviceThatCannotSealKeepsTheTokenForThisProcessOnly() {
        val values = Memory()
        val broken = BusymateTokenStore(values, BusymateAesGcmSealer { throw IllegalStateException("no keystore") })
        assertFalse(broken.save("acme", token))
        assertTrue(values.map.isEmpty())
        assertEquals(token, broken.load("acme"))
        assertNull(BusymateTokenStore(values, BusymateAesGcmSealer { throw IllegalStateException("no keystore") }).load("acme"))
    }

    @Test fun hexRoundTrips() {
        val bytes = ByteArray(256) { it.toByte() }
        assertTrue(bytes.contentEquals(BusymateTokenStore.unhex(BusymateTokenStore.hex(bytes))))
        assertNull(BusymateTokenStore.unhex("abc"))
        assertNull(BusymateTokenStore.unhex("zz"))
    }
}
