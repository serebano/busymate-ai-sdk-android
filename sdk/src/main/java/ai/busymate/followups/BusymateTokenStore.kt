package ai.busymate

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Where sealed values live. In the app: a private SharedPreferences file. */
internal interface BusymateKeyValue {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

/** Authenticated encryption bound to a context string (the assistant slug). */
internal interface BusymateSealer {
    fun seal(plain: ByteArray, context: ByteArray): ByteArray
    /** Null when the bytes were not sealed by this key for this context. */
    fun open(sealed: ByteArray, context: ByteArray): ByteArray?
}

/** AES-256-GCM with a fresh IV per value. [key] is the Android Keystore key in the app. */
internal class BusymateAesGcmSealer(private val key: () -> SecretKey) : BusymateSealer {
    override fun seal(plain: ByteArray, context: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(context)
        val iv = cipher.iv
        val body = cipher.doFinal(plain)
        return byteArrayOf(FORMAT, iv.size.toByte()) + iv + body
    }

    override fun open(sealed: ByteArray, context: ByteArray): ByteArray? = try {
        val ivSize = sealed[1].toInt()
        if (sealed[0] != FORMAT || ivSize !in 12..16 || sealed.size < 2 + ivSize + TAG_BITS / 8) null else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 2, ivSize))
            cipher.updateAAD(context)
            cipher.doFinal(sealed, 2 + ivSize, sealed.size - 2 - ivSize)
        }
    } catch (_: Exception) { null }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val FORMAT: Byte = 1

        /** The non-exportable key in the Android Keystore; created on first use. */
        fun androidKeystore(): BusymateAesGcmSealer = BusymateAesGcmSealer {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (store.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build())
                generateKey()
            }
        }

        private const val KEY_ALIAS = "ai.busymate.sdk.visitor"
    }
}

/**
 * The visitor token, one per assistant, encrypted at rest with a Keystore key and bound to its
 * assistant slug. The token is opaque to the SDK, is never logged and never leaves this class in
 * clear except to the caller that sends it to `POST /api/chat/unread`.
 *
 * Fail closed: a value that does not open (restored backup, cleared Keystore, tampering) is deleted
 * and reads as "no token".
 */
internal class BusymateTokenStore(private val values: BusymateKeyValue, private val sealer: BusymateSealer) {
    @Volatile private var memory: Pair<String, String>? = null

    /** False when the device could not seal it; the token then lives for this process only. */
    fun save(assistant: String, token: String): Boolean {
        memory = assistant to token
        return try {
            values.put(key(assistant), hex(sealer.seal(token.toByteArray(Charsets.UTF_8), assistant.toByteArray(Charsets.UTF_8))))
            true
        } catch (_: Exception) { false }
    }

    fun load(assistant: String): String? {
        memory?.let { if (it.first == assistant) return it.second }
        val stored = try { values.get(key(assistant)) } catch (_: Exception) { null } ?: return null
        val token = unhex(stored)?.let { sealer.open(it, assistant.toByteArray(Charsets.UTF_8)) }?.toString(Charsets.UTF_8)
        if (token == null) { try { values.remove(key(assistant)) } catch (_: Exception) { } ; return null }
        memory = assistant to token
        return token
    }

    fun clear(assistant: String) {
        if (memory?.first == assistant) memory = null
        try { values.remove(key(assistant)) } catch (_: Exception) { }
    }

    companion object {
        /** Same key name the web loader uses in the host page's localStorage. */
        fun key(assistant: String) = "bm_v:$assistant"

        fun create(context: Context): BusymateTokenStore {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return BusymateTokenStore(object : BusymateKeyValue {
                override fun get(key: String): String? = prefs.getString(key, null)
                override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
                override fun remove(key: String) { prefs.edit().remove(key).apply() }
            }, BusymateAesGcmSealer.androidKeystore())
        }

        const val PREFS = "ai.busymate.sdk"
        private const val DIGITS = "0123456789abcdef"

        fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
            for (b in bytes) { append(DIGITS[(b.toInt() shr 4) and 15]); append(DIGITS[b.toInt() and 15]) }
        }

        fun unhex(text: String): ByteArray? {
            if (text.length % 2 != 0) return null
            val out = ByteArray(text.length / 2)
            for (i in out.indices) {
                val hi = DIGITS.indexOf(text[i * 2]); val lo = DIGITS.indexOf(text[i * 2 + 1])
                if (hi < 0 || lo < 0) return null
                out[i] = ((hi shl 4) or lo).toByte()
            }
            return out
        }
    }
}
