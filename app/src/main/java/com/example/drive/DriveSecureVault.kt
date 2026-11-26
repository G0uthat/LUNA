package com.example.drive

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Local encrypted vault for user-supplied Google Drive OAuth Client ID, optional API Key,
 * and OAuth tokens. Uses Android Keystore AES-256-GCM when available on device.
 * Zero credentials are hardcoded or transmitted to any server other than Google APIs.
 */
class DriveSecureVault(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _authSnapshot = MutableStateFlow(readSnapshot())
    val authSnapshot: StateFlow<DriveAuthSnapshot> = _authSnapshot.asStateFlow()

    fun getClientId(): String = readDecrypted(KEY_CLIENT_ID)
    fun getApiKey(): String = readDecrypted(KEY_API_KEY)
    fun getAccessToken(): String = readDecrypted(KEY_ACCESS_TOKEN)
    fun getRefreshToken(): String = readDecrypted(KEY_REFRESH_TOKEN)
    fun getAccountEmail(): String = readDecrypted(KEY_ACCOUNT_EMAIL)
    fun getTokenExpiresAtMs(): Long = prefs.getLong(KEY_EXPIRES_AT, 0L)
    fun getUseMinimalScope(): Boolean = prefs.getBoolean(KEY_MINIMAL_SCOPE, false)
    fun getSyncMode(): DriveSyncMode =
        DriveSyncMode.entries.firstOrNull { it.name == prefs.getString(KEY_SYNC_MODE, null) }
            ?: DriveSyncMode.WIFI_ONLY

    fun saveClientConfig(clientId: String, apiKey: String, useMinimalScope: Boolean) {
        writeEncrypted(KEY_CLIENT_ID, clientId.trim())
        writeEncrypted(KEY_API_KEY, apiKey.trim())
        prefs.edit().putBoolean(KEY_MINIMAL_SCOPE, useMinimalScope).apply()
        refreshSnapshot()
    }

    fun saveSyncMode(mode: DriveSyncMode) {
        prefs.edit().putString(KEY_SYNC_MODE, mode.name).apply()
        refreshSnapshot()
    }

    fun saveOAuthTokens(
        accessToken: String,
        refreshToken: String?,
        expiresInSec: Long,
        accountEmail: String? = null
    ) {
        val expiresAt = System.currentTimeMillis() + (expiresInSec.coerceAtLeast(60L) * 1000L)
        writeEncrypted(KEY_ACCESS_TOKEN, accessToken.trim())
        if (!refreshToken.isNullOrBlank()) {
            writeEncrypted(KEY_REFRESH_TOKEN, refreshToken.trim())
        }
        if (!accountEmail.isNullOrBlank()) {
            writeEncrypted(KEY_ACCOUNT_EMAIL, accountEmail.trim())
        }
        prefs.edit().putLong(KEY_EXPIRES_AT, expiresAt).apply()
        refreshSnapshot()
    }

    fun savePkceSession(state: String, codeVerifier: String) {
        writeEncrypted(KEY_PKCE_STATE, state)
        writeEncrypted(KEY_PKCE_VERIFIER, codeVerifier)
    }

    fun consumePkceSession(): Pair<String, String>? {
        val state = readDecrypted(KEY_PKCE_STATE)
        val verifier = readDecrypted(KEY_PKCE_VERIFIER)
        prefs.edit().remove(KEY_PKCE_STATE).remove(KEY_PKCE_VERIFIER).apply()
        if (state.isBlank() || verifier.isBlank()) return null
        return state to verifier
    }

    fun clearAuthTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_ACCOUNT_EMAIL)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_PKCE_STATE)
            .remove(KEY_PKCE_VERIFIER)
            .apply()
        refreshSnapshot()
    }

    private fun refreshSnapshot() {
        _authSnapshot.value = readSnapshot()
    }

    private fun readSnapshot(): DriveAuthSnapshot {
        val token = getAccessToken()
        val refresh = getRefreshToken()
        return DriveAuthSnapshot(
            clientId = getClientId(),
            apiKey = getApiKey(),
            accountEmail = getAccountEmail(),
            isSignedIn = token.isNotBlank() || refresh.isNotBlank(),
            tokenExpiresAtMs = getTokenExpiresAtMs(),
            hasRefreshToken = refresh.isNotBlank(),
            useMinimalDriveFileScope = getUseMinimalScope(),
            syncMode = getSyncMode()
        )
    }

    private fun writeEncrypted(key: String, plain: String) {
        if (plain.isEmpty()) {
            prefs.edit().remove(key).apply()
            return
        }
        val encoded = encryptValue(plain)
        prefs.edit().putString(key, encoded).apply()
    }

    private fun readDecrypted(key: String): String {
        val stored = prefs.getString(key, null) ?: return ""
        return decryptValue(stored)
    }

    private fun getOrCreateKeystoreSecret(): SecretKey? {
        return runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey
            if (existing != null) return@runCatching existing
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            generator.init(spec)
            generator.generateKey()
        }.getOrNull()
    }

    private fun encryptValue(plain: String): String {
        val secretKey = getOrCreateKeystoreSecret()
        if (secretKey != null) {
            val encrypted = runCatching {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val cipherBytes = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
                val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
                val dataB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
                "$PREFIX_GCM$ivB64:$dataB64"
            }.getOrNull()
            if (encrypted != null) return encrypted
        }
        // Fallback for JVM/Robolectric test environments without AndroidKeyStore
        val rawB64 = Base64.encodeToString(plain.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return "$PREFIX_PLAIN$rawB64"
    }

    private fun decryptValue(stored: String): String {
        return when {
            stored.startsWith(PREFIX_GCM) -> {
                val payload = stored.removePrefix(PREFIX_GCM)
                val parts = payload.split(":", limit = 2)
                if (parts.size != 2) return ""
                val secretKey = getOrCreateKeystoreSecret() ?: return ""
                runCatching {
                    val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                    val cipherBytes = Base64.decode(parts[1], Base64.NO_WRAP)
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
                    String(cipher.doFinal(cipherBytes), Charsets.UTF_8)
                }.getOrDefault("")
            }
            stored.startsWith(PREFIX_PLAIN) -> {
                runCatching {
                    String(Base64.decode(stored.removePrefix(PREFIX_PLAIN), Base64.NO_WRAP), Charsets.UTF_8)
                }.getOrDefault("")
            }
            else -> ""
        }
    }

    companion object {
        private const val PREFS_NAME = "luma_drive_secure_vault"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEYSTORE_ALIAS = "luma_drive_aes_gcm_key_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val PREFIX_GCM = "gcm1:"
        private const val PREFIX_PLAIN = "b64:"

        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_ACCOUNT_EMAIL = "account_email"
        private const val KEY_EXPIRES_AT = "expires_at"
        private const val KEY_MINIMAL_SCOPE = "minimal_scope"
        private const val KEY_SYNC_MODE = "sync_mode"
        private const val KEY_PKCE_STATE = "pkce_state"
        private const val KEY_PKCE_VERIFIER = "pkce_verifier"
    }
}
