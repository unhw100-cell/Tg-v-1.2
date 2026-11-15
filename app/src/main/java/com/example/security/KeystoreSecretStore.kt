package com.example.security

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM vault backed by an Android Keystore key. Only authenticated ciphertext
 * and a random IV are stored in SharedPreferences. No plaintext secrets are logged.
 */
class KeystoreSecretStore(private val preferences: SharedPreferences) {
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "rustgram.local-secrets.v1"
        private const val PREFIX_V1 = "v1."
        private const val PREFIX_V2 = "v2."
        private const val GCM_TAG_BITS = 128
    }

    @Synchronized
    fun get(key: String): String? {
        val stored = preferences.getString(key, null) ?: return null
        if (!stored.startsWith(PREFIX_V1) && !stored.startsWith(PREFIX_V2)) {
            // Migrate old plaintext entries in place on first successful read.
            put(key, stored)
            return stored
        }

        val isV2 = stored.startsWith(PREFIX_V2)
        val prefix = if (isV2) PREFIX_V2 else PREFIX_V1
        val parts = stored.removePrefix(prefix).split('.', limit = 2)
        require(parts.size == 2) { "Stored secret has an invalid format" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
        require(iv.size == 12 && ciphertext.size >= 16) { "Stored secret is truncated" }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        if (isV2) cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
        val clearText = String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        // Re-encrypt v1 data with preference-key AAD so ciphertext cannot be swapped
        // between secret slots without authentication failure.
        if (!isV2) put(key, clearText)
        return clearText
    }

    @Synchronized
    fun put(key: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val encoded = PREFIX_V2 +
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        preferences.edit().putString(key, encoded).apply()
    }

    @Synchronized
    fun getOrCreateBase64(key: String, byteCount: Int): String {
        require(byteCount in 16..64)
        get(key)?.let { return it }
        val bytes = ByteArray(byteCount).also { SecureRandom().nextBytes(it) }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        put(key, encoded)
        return encoded
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        // Never replace a lost key while encrypted values remain: that would silently
        // make the existing Telegram database and API secret unrecoverable.
        val hasEncryptedSecrets = preferences.all.values
            .filterIsInstance<String>()
            .any { it.startsWith(PREFIX_V1) || it.startsWith(PREFIX_V2) }
        check(!hasEncryptedSecrets) {
            "RustGram Keystore key is missing; re-enter API credentials and reset the local TDLib database"
        }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
