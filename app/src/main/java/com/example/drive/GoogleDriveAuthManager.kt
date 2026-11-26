package com.example.drive

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Direct OAuth 2.0 + PKCE (RFC 7636) manager for user-owned Google Drive integration.
 * Communicates strictly with Google's official OAuth endpoints (zero intermediate backend).
 */
class GoogleDriveAuthManager(
    private val appContext: Context,
    private val vault: DriveSecureVault,
    private val httpClient: OkHttpClient = defaultAuthClient()
) {
    private val refreshMutex = Mutex()

    fun getRedirectUri(): String = REDIRECT_URI

    fun buildAuthorizationIntent(): Result<Intent> {
        val clientId = vault.getClientId()
        if (clientId.isBlank()) {
            android.util.Log.e(
                "LumaCloudAuth",
                "Google OAuth unavailable: VITE_GOOGLE_CLIENT_ID is missing or empty in .env / BuildConfig."
            )
            return Result.failure(
                DriveApiException(
                    httpCode = 0,
                    userMessage = "El inicio de sesión no está disponible en este momento",
                    isAuthError = true
                )
            )
        }
        val state = generateRandomUrlSafe(24)
        val verifier = generateRandomUrlSafe(48)
        val challenge = sha256Base64Url(verifier)
        vault.savePkceSession(state, verifier)

        val scope = if (vault.getUseMinimalScope()) {
            "$SCOPE_OPENID $SCOPE_EMAIL $SCOPE_PROFILE $SCOPE_DRIVE_FILE $SCOPE_DRIVE_APPDATA"
        } else {
            "$SCOPE_OPENID $SCOPE_EMAIL $SCOPE_PROFILE $SCOPE_DRIVE_FULL $SCOPE_DRIVE_APPDATA"
        }

        val authUrl = AUTH_ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("redirect_uri", REDIRECT_URI)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", scope)
            .addQueryParameter("access_type", "offline")
            .addQueryParameter("prompt", "consent")
            .addQueryParameter("include_granted_scopes", "true")
            .addQueryParameter("state", state)
            .addQueryParameter("code_challenge", challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .build()

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl.toString())).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return Result.success(intent)
    }

    /**
     * Handles the OAuth redirect callback (`com.lord67.luma:/oauth2redirect?code=...&state=...`).
     */
    suspend fun handleAuthorizationCallback(callbackUri: Uri): Result<String> = withContext(Dispatchers.IO) {
        val error = callbackUri.getQueryParameter("error")
        if (!error.isNullOrBlank()) {
            vault.consumePkceSession()
            val desc = callbackUri.getQueryParameter("error_description").orEmpty().lowercase()
            val friendly = when {
                error == "popup_closed_by_user" || error == "user_cancelled" ->
                    "Cerraste la ventana de inicio de sesión antes de terminar."
                error == "popup_blocked_by_browser" ->
                    "La ventana de Google fue bloqueada. Habilita las ventanas emergentes e intenta de nuevo."
                error == "org_internal" || desc.contains("tester") || desc.contains("unverified") ->
                    "Tu correo no está autorizado en modo pruebas. Pide acceso al desarrollador."
                error == "access_denied" ->
                    "No se completó el acceso (permiso cancelado o correo aún no autorizado en modo pruebas)."
                else ->
                    "Google devolvió un error de acceso ($error)."
            }
            return@withContext Result.failure(DriveApiException(401, friendly, isAuthError = true))
        }

        val code = callbackUri.getQueryParameter("code")
        val returnedState = callbackUri.getQueryParameter("state")
        if (code.isNullOrBlank() || returnedState.isNullOrBlank()) {
            return@withContext Result.failure(
                DriveApiException(400, "Respuesta de autorización incompleta.", isAuthError = true)
            )
        }

        val session = vault.consumePkceSession()
            ?: return@withContext Result.failure(
                DriveApiException(400, "La sesión de inicio expiró. Intenta conectar nuevamente.", isAuthError = true)
            )

        val (expectedState, codeVerifier) = session
        if (returnedState != expectedState) {
            return@withContext Result.failure(
                DriveApiException(403, "Verificación de seguridad (state) inválida. Reintenta iniciar sesión.", isAuthError = true)
            )
        }

        exchangeCodeForTokens(code = code, codeVerifier = codeVerifier)
    }

    /**
     * Accepts either a full redirected callback URL or a direct temporary OAuth 2.0 access token
     * (useful when testing with Google OAuth Playground or restricted WebViews).
     */
    suspend fun connectWithManualTokenOrRedirect(input: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(DriveApiException(400, "Ingresa un token o enlace de retorno válido.", isAuthError = true))
        }
        if (trimmed.startsWith(REDIRECT_SCHEME) || trimmed.contains("code=")) {
            val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
            if (uri != null && !uri.getQueryParameter("code").isNullOrBlank()) {
                return@withContext handleAuthorizationCallback(uri)
            }
        }
        // Validate pasted access token directly against Google tokeninfo
        val email = fetchTokenEmail(trimmed)
        if (email == null) {
            return@withContext Result.failure(
                DriveApiException(401, "El token ingresado no es válido o ya expiró.", isAuthError = true)
            )
        }
        vault.saveOAuthTokens(
            accessToken = trimmed,
            refreshToken = null,
            expiresInSec = 3500L,
            accountEmail = email
        )
        Result.success(email)
    }

    private suspend fun exchangeCodeForTokens(code: String, codeVerifier: String): Result<String> {
        val clientId = vault.getClientId()
        if (clientId.isBlank()) {
            android.util.Log.e("LumaCloudAuth", "Cannot exchange OAuth code: missing VITE_GOOGLE_CLIENT_ID.")
            return Result.failure(DriveApiException(400, "El inicio de sesión no está disponible en este momento", isAuthError = true))
        }
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("code", code)
            .add("code_verifier", codeVerifier)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", REDIRECT_URI)
            .build()

        val request = Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(body)
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val desc = runCatching { JSONObject(raw).optString("error_description") }.getOrDefault("")
                    return Result.failure(
                        DriveApiException(
                            httpCode = response.code,
                            userMessage = desc.ifBlank { "No se pudo completar el inicio de sesión con Google (${response.code})." },
                            isAuthError = true
                        )
                    )
                }
                val json = JSONObject(raw)
                val accessToken = json.optString("access_token")
                val refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }
                val expiresIn = json.optLong("expires_in", 3600L)
                if (accessToken.isBlank()) {
                    return Result.failure(DriveApiException(500, "Google no devolvió token de acceso.", isAuthError = true))
                }
                val email = fetchTokenEmail(accessToken) ?: "Cuenta de Google conectada"
                vault.saveOAuthTokens(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresInSec = expiresIn,
                    accountEmail = email
                )
                Result.success(email)
            }
        } catch (e: IOException) {
            Result.failure(
                DriveApiException(0, "Sin conexión a internet para completar el inicio de sesión.", isOffline = true, cause = e)
            )
        }
    }

    /**
     * Returns a non-expired OAuth access token, transparently renewing via refresh_token when needed.
     */
    suspend fun getValidAccessToken(forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        val currentToken = vault.getAccessToken()
        val expiresAt = vault.getTokenExpiresAtMs()
        val now = System.currentTimeMillis()
        if (!forceRefresh && currentToken.isNotBlank() && expiresAt - now > TOKEN_SAFETY_WINDOW_MS) {
            return@withContext currentToken
        }

        val refreshToken = vault.getRefreshToken()
        val clientId = vault.getClientId()
        if (refreshToken.isBlank() || clientId.isBlank()) {
            return@withContext if (!forceRefresh && currentToken.isNotBlank() && now < expiresAt) currentToken else null
        }

        refreshMutex.withLock {
            // Re-check inside lock in case another coroutine just refreshed
            val rechecked = vault.getAccessToken()
            if (!forceRefresh && rechecked.isNotBlank() && vault.getTokenExpiresAtMs() - System.currentTimeMillis() > TOKEN_SAFETY_WINDOW_MS) {
                return@withLock rechecked
            }
            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .build()
            val req = Request.Builder().url(TOKEN_ENDPOINT).post(body).build()
            try {
                httpClient.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        if (resp.code == 400 || resp.code == 401) {
                            vault.clearAuthTokens()
                        }
                        return@withLock null
                    }
                    val json = JSONObject(raw)
                    val freshToken = json.optString("access_token")
                    val expiresIn = json.optLong("expires_in", 3600L)
                    if (freshToken.isBlank()) return@withLock null
                    vault.saveOAuthTokens(
                        accessToken = freshToken,
                        refreshToken = refreshToken,
                        expiresInSec = expiresIn
                    )
                    freshToken
                }
            } catch (_: IOException) {
                null
            }
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val tokenToRevoke = vault.getRefreshToken().ifBlank { vault.getAccessToken() }
        vault.clearAuthTokens()
        if (tokenToRevoke.isNotBlank()) {
            runCatching {
                val body = FormBody.Builder().add("token", tokenToRevoke).build()
                val req = Request.Builder().url(REVOKE_ENDPOINT).post(body).build()
                httpClient.newCall(req).execute().close()
            }
        }
    }

    private fun fetchTokenEmail(accessToken: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url(USERINFO_ENDPOINT)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val json = JSONObject(resp.body?.string().orEmpty())
                json.optString("email").takeIf { it.isNotBlank() } ?: "Cuenta de Google"
            }
        }.getOrNull()
    }

    private fun generateRandomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun sha256Base64Url(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    companion object {
        const val REDIRECT_SCHEME = "com.lord67.luma"
        const val REDIRECT_HOST = "oauth2redirect"
        const val REDIRECT_URI = "$REDIRECT_SCHEME:/$REDIRECT_HOST"

        const val SCOPE_OPENID = "openid"
        const val SCOPE_EMAIL = "email"
        const val SCOPE_PROFILE = "profile"
        const val SCOPE_DRIVE_FULL = "https://www.googleapis.com/auth/drive"
        const val SCOPE_DRIVE_FILE = "https://www.googleapis.com/auth/drive.file"
        const val SCOPE_DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"

        private const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
        private const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
        private const val REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke"
        private const val USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo"
        private const val TOKEN_SAFETY_WINDOW_MS = 90_000L

        private fun defaultAuthClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }
}
