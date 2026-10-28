package com.epiclibrary.mobile.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureTokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("epic_session", Context.MODE_PRIVATE)

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "epic_refresh_token_key"
        private const val TOKEN = "refresh_token"
        private const val IV = "refresh_token_iv"
        private const val ACCESS_TOKEN = "access_token"
        private const val ACCESS_TOKEN_IV = "access_token_iv"
        private const val ACCESS_EXPIRES_AT = "access_expires_at"
        private const val DISPLAY_NAME = "display_name"
    }

    fun saveSession(token: EpicToken) {
        saveRefreshToken(token.refreshToken)
        if (!token.accessToken.isNullOrBlank()) {
            saveEncrypted(ACCESS_TOKEN, ACCESS_TOKEN_IV, token.accessToken)
            check(prefs.edit()
                .putLong(ACCESS_EXPIRES_AT, System.currentTimeMillis() + (token.expiresInSeconds * 1000L))
                .commit()) { "Could not persist Epic session expiry" }
        }
    }

    fun loadAccessToken(): String? = loadEncrypted(ACCESS_TOKEN, ACCESS_TOKEN_IV)

    fun loadAccessExpiresAt(): Long = prefs.getLong(ACCESS_EXPIRES_AT, 0L)

    fun saveRefreshToken(token: String?) {
        if (token.isNullOrBlank()) return
        saveEncrypted(TOKEN, IV, token)
    }

    fun loadRefreshToken(): String? = loadEncrypted(TOKEN, IV)

    fun saveDisplayName(name: String) = prefs.edit().putString(DISPLAY_NAME, name).apply()
    fun loadDisplayName(): String = prefs.getString(DISPLAY_NAME, "") ?: ""

    fun clear() = prefs.edit().clear().apply()

    private fun saveEncrypted(valueKey: String, ivKey: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        check(prefs.edit()
            .putString(valueKey, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(ivKey, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()) { "Could not persist encrypted Epic session value" }
    }

    private fun loadEncrypted(valueKey: String, ivKey: String): String? = try {
        val encrypted = prefs.getString(valueKey, null) ?: return null
        val iv = prefs.getString(ivKey, null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )
        String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
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
