package com.example.core.cloud

import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.drive.DriveRepository
import com.example.drive.DriveSyncMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Real-time visual & behavioral state of the global Sync button in Library, Reader, and Notebooks.
 */
enum class CloudSyncButtonState {
    /** User skipped sign-in / signed out: muted, greyed out, low opacity, NO X icon, tap opens tooltip. */
    DISABLED_OFFLINE_ACCOUNT,
    /** Signed in and everything is up to date (check icon). */
    SYNCED,
    /** Signed in and actively uploading/downloading/refreshing (spinning animation). */
    SYNCING,
    /** Signed in with pending queued local edits (warning dot). */
    PENDING,
    /** Signed in but last sync failed or token expired (soft red indicator). */
    ERROR,
    /** Signed in while device has no internet: cloud-off icon, never blocks editing, queues changes. */
    NO_INTERNET
}

data class CloudErrorLogEntry(
    val timestampMs: Long,
    val area: String,
    val message: String
)

data class CloudAccountUiState(
    val hasCompletedWelcome: Boolean = false,
    val skippedWelcomeOffline: Boolean = false,
    val isSignedIn: Boolean = false,
    val activeProvider: CloudProviderId? = null,
    val user: CloudUserProfile? = null,
    val editableProfileName: String = "",
    val entitlement: EntitlementSnapshot = EntitlementSnapshot(
        plan = EntitlementPlan.FREE,
        badgeLabel = "Modo sin conexión",
        isBetaFullAccess = false
    ),
    val currentDeviceId: String = "",
    val currentDeviceName: String = "",
    val connectedDevices: List<CloudDeviceInfo> = emptyList(),
    val isOnline: Boolean = true,
    val pendingQueueCount: Int = 0,
    val lastErrorMessage: String? = null,
    val tokenExpiredNeedsRelogin: Boolean = false,
    val syncButtonState: CloudSyncButtonState = CloudSyncButtonState.DISABLED_OFFLINE_ACCOUNT,
    val hasConfiguredClientId: Boolean = false,
    val isAuthClientReady: Boolean = false,
    val isLaunchingSignIn: Boolean = false,
    val syncMode: DriveSyncMode = DriveSyncMode.MANUAL
)

/**
 * Single source of truth for Stage 1 (Welcome, Mi cuenta, Offline mode, Devices, Beta Entitlements,
 * and real-time Sync Button state) shared by Library, Reader, Notebook Studio, and Settings.
 */
class CloudAccountManager private constructor(
    private val appContext: Context,
    private val driveRepo: DriveRepository,
    private val entitlementService: EntitlementService = BetaEntitlementService()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val googleProvider: StorageProvider = GoogleDriveStorageProvider(driveRepo)
    val oneDriveProvider: StorageProvider = OneDriveStorageProvider()
    val teraboxProvider: StorageProvider = TeraboxStorageProvider()

    val availableProviders: List<StorageProvider>
        get() = listOf(googleProvider, oneDriveProvider, teraboxProvider)
            .filter { it.isEnabledByFeatureFlag }

    private val _hasCompletedWelcome = MutableStateFlow(prefs.getBoolean(KEY_WELCOME_DONE, false))
    private val _skippedOffline = MutableStateFlow(prefs.getBoolean(KEY_SKIPPED_OFFLINE, false))
    private val _customDisplayName = MutableStateFlow(prefs.getString(KEY_PROFILE_NAME, "").orEmpty())
    private val _currentDeviceId: String = getOrCreateDeviceId()
    private val _currentDeviceName = MutableStateFlow(getOrCreateDeviceName())
    private val _devices = MutableStateFlow(loadStoredDevices())
    private val _errorLogs = MutableStateFlow<List<CloudErrorLogEntry>>(emptyList())
    private val _manualError = MutableStateFlow<String?>(null)
    private val _isAuthClientReady = MutableStateFlow(false)
    private val _isLaunchingSignIn = MutableStateFlow(false)
    private val _awaitingOAuthCallback = java.util.concurrent.atomic.AtomicBoolean(false)

    val errorLogs: StateFlow<List<CloudErrorLogEntry>> = _errorLogs.asStateFlow()

    val uiState: StateFlow<CloudAccountUiState> = combine(
        combine(_hasCompletedWelcome, _skippedOffline, _customDisplayName, _currentDeviceName, _devices) {
                welcomeDone, skipped, customName, devName, devs ->
            LocalProfileSlice(welcomeDone, skipped, customName, devName, devs)
        },
        driveRepo.authSnapshot,
        driveRepo.isOnline,
        driveRepo.isSyncing,
        driveRepo.isRefreshing,
        driveRepo.syncQueue,
        combine(driveRepo.statusMessage, _manualError, _isAuthClientReady, _isLaunchingSignIn) {
                repoMsg, manualErr, authReady, launching ->
            AuthUiExtraSlice(repoMsg, manualErr, authReady, launching)
        }
    ) { arr ->
        val local = arr[0] as LocalProfileSlice
        val auth = arr[1] as com.example.drive.DriveAuthSnapshot
        val isOnline = arr[2] as Boolean
        val isSyncing = arr[3] as Boolean
        val isRefreshing = arr[4] as Boolean
        @Suppress("UNCHECKED_CAST")
        val queue = arr[5] as List<com.example.drive.DriveSyncTaskEntity>
        val extra = arr[6] as AuthUiExtraSlice

        val isSignedIn = auth.isSignedIn
        val tokenExpired = isSignedIn &&
            auth.tokenExpiresAtMs > 0L &&
            System.currentTimeMillis() >= auth.tokenExpiresAtMs &&
            !auth.hasRefreshToken

        val email = auth.accountEmail.ifBlank { "" }
        val fallbackName = email.substringBefore('@')
            .replace('.', ' ')
            .replaceFirstChar { it.uppercase() }
            .ifBlank { "Usuario Luma" }
        val resolvedName = local.customDisplayName.ifBlank { fallbackName }

        val user = if (isSignedIn) {
            CloudUserProfile(
                userId = "google:${email.lowercase().ifBlank { _currentDeviceId }}",
                provider = CloudProviderId.GOOGLE_DRIVE,
                email = email.ifBlank { "Cuenta Google conectada" },
                displayName = resolvedName
            )
        } else null

        val entitlement = if (isSignedIn) {
            EntitlementSnapshot(
                plan = EntitlementPlan.PREMIUM,
                badgeLabel = "Beta – acceso completo",
                expiresAtMs = null,
                minVersion = CloudPublicConfig.APP_BETA_VERSION,
                maxDevices = CloudPublicConfig.MAX_SYNC_DEVICES,
                isBetaFullAccess = true
            )
        } else {
            EntitlementSnapshot(
                plan = EntitlementPlan.FREE,
                badgeLabel = "Modo sin conexión",
                expiresAtMs = null,
                minVersion = CloudPublicConfig.APP_BETA_VERSION,
                maxDevices = 1,
                isBetaFullAccess = false
            )
        }

        val combinedError = extra.manualError ?: extra.repoStatus?.takeIf {
            it.contains("error", ignoreCase = true) ||
                it.contains("caduc", ignoreCase = true) ||
                it.contains("tester", ignoreCase = true) ||
                it.contains("permiso", ignoreCase = true) ||
                it.contains("no está disponible", ignoreCase = true) ||
                it.contains("cerraste", ignoreCase = true) ||
                it.contains("bloquead", ignoreCase = true)
        }

        val buttonState = when {
            !isSignedIn -> CloudSyncButtonState.DISABLED_OFFLINE_ACCOUNT
            !isOnline -> CloudSyncButtonState.NO_INTERNET
            isSyncing || isRefreshing -> CloudSyncButtonState.SYNCING
            tokenExpired || !combinedError.isNullOrBlank() -> CloudSyncButtonState.ERROR
            queue.isNotEmpty() -> CloudSyncButtonState.PENDING
            else -> CloudSyncButtonState.SYNCED
        }

        val devicesWithCurrent = ensureCurrentDevicePresent(local.devices, local.currentDeviceName)
        val envClientId = CloudPublicConfig.resolveGoogleClientId()
        val hasClientId = envClientId.isNotBlank() || auth.clientId.isNotBlank()

        CloudAccountUiState(
            hasCompletedWelcome = local.welcomeDone || isSignedIn,
            skippedWelcomeOffline = local.skipped && !isSignedIn,
            isSignedIn = isSignedIn,
            activeProvider = if (isSignedIn) CloudProviderId.GOOGLE_DRIVE else null,
            user = user,
            editableProfileName = resolvedName,
            entitlement = entitlement,
            currentDeviceId = _currentDeviceId,
            currentDeviceName = local.currentDeviceName,
            connectedDevices = devicesWithCurrent,
            isOnline = isOnline,
            pendingQueueCount = queue.size,
            lastErrorMessage = if (tokenExpired) {
                "Tu sesión de prueba caducó (7 días). Inicia sesión de nuevo sin perder tus archivos locales."
            } else {
                combinedError
            },
            tokenExpiredNeedsRelogin = tokenExpired,
            syncButtonState = buttonState,
            hasConfiguredClientId = hasClientId,
            isAuthClientReady = extra.isAuthClientReady,
            isLaunchingSignIn = extra.isLaunchingSignIn,
            syncMode = auth.syncMode
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = CloudAccountUiState(
            hasCompletedWelcome = prefs.getBoolean(KEY_WELCOME_DONE, false),
            skippedWelcomeOffline = prefs.getBoolean(KEY_SKIPPED_OFFLINE, false),
            currentDeviceId = _currentDeviceId,
            currentDeviceName = _currentDeviceName.value,
            connectedDevices = ensureCurrentDevicePresent(_devices.value, _currentDeviceName.value),
            hasConfiguredClientId = CloudPublicConfig.resolveGoogleClientId().isNotBlank() ||
                driveRepo.vault.getClientId().isNotBlank(),
            isAuthClientReady = false,
            isLaunchingSignIn = false
        )
    )

    init {
        // Asynchronous readiness initialization: seeds Client ID from env BuildConfig (never user-entered)
        scope.launch {
            val envClientId = CloudPublicConfig.resolveGoogleClientId()
            if (envClientId.isNotBlank()) {
                driveRepo.saveClientConfigurationQuietly(
                    clientId = envClientId,
                    apiKey = driveRepo.vault.getApiKey(),
                    useMinimalScope = true
                )
            } else if (driveRepo.vault.getClientId().isBlank()) {
                android.util.Log.w(
                    "LumaCloudAuth",
                    "VITE_GOOGLE_CLIENT_ID is not configured in .env / Secrets. Google Sign-In will report unavailable."
                )
            }
            _isAuthClientReady.value = true
        }
        // Automatically mark Welcome complete and clear launch spinner once OAuth finishes
        scope.launch {
            driveRepo.authSnapshot.collect { snap ->
                if (snap.isSignedIn) {
                    _awaitingOAuthCallback.set(false)
                    _isLaunchingSignIn.value = false
                    _manualError.value = null
                    if (!_hasCompletedWelcome.value) {
                        markWelcomeCompleted(skippedOffline = false)
                    }
                }
            }
        }
    }

    /**
     * Records the user's decision to skip cloud sign-in on first launch and enter offline mode.
     * The welcome screen will never be forced again unless explicitly reset.
     */
    fun skipWelcomeUseOffline() {
        markWelcomeCompleted(skippedOffline = true)
    }

    fun markWelcomeCompleted(skippedOffline: Boolean) {
        prefs.edit()
            .putBoolean(KEY_WELCOME_DONE, true)
            .putBoolean(KEY_SKIPPED_OFFLINE, skippedOffline)
            .apply()
        _hasCompletedWelcome.value = true
        _skippedOffline.value = skippedOffline
    }

    fun updateProfileDisplayName(newName: String) {
        val clean = sanitizePlainText(newName, 60)
        if (clean.isBlank()) return
        prefs.edit().putString(KEY_PROFILE_NAME, clean).apply()
        _customDisplayName.value = clean
    }

    fun updateCurrentDeviceName(newName: String) {
        val clean = sanitizePlainText(newName, 50)
        if (clean.isBlank()) return
        prefs.edit().putString(KEY_DEVICE_NAME, clean).apply()
        _currentDeviceName.value = clean
        val updated = ensureCurrentDevicePresent(_devices.value, clean)
        persistDevices(updated)
    }

    fun removeDevice(deviceId: String) {
        if (deviceId == _currentDeviceId) return
        val filtered = _devices.value.filterNot { it.deviceId == deviceId }
        persistDevices(filtered)
    }

    /**
     * Direct Google Sign-In flow (used identically from Welcome, Mi cuenta, and Ajustes -> Fuentes).
     * Never prompts users to enter a Client ID and skips any intermediate consent screen.
     */
    fun launchDirectGoogleSignIn(context: Context): Result<Unit> {
        if (!_isAuthClientReady.value) {
            return Result.failure(IllegalStateException("Google aún se está preparando. Espera un momento."))
        }

        val envClientId = CloudPublicConfig.resolveGoogleClientId()
        if (envClientId.isNotBlank()) {
            driveRepo.saveClientConfigurationQuietly(
                clientId = envClientId,
                apiKey = driveRepo.vault.getApiKey(),
                useMinimalScope = true
            )
        } else if (driveRepo.vault.getClientId().isBlank()) {
            android.util.Log.e(
                "LumaCloudAuth",
                "Google Sign-In unavailable: VITE_GOOGLE_CLIENT_ID is missing in .env / BuildConfig."
            )
            val userMsg = "El inicio de sesión no está disponible en este momento"
            _manualError.value = userMsg
            recordError("Config", "VITE_GOOGLE_CLIENT_ID missing")
            return Result.failure(IllegalStateException(userMsg))
        }

        if (!uiState.value.isOnline) {
            val offlineMsg = "Sin conexión a internet. Conéctate e intenta de nuevo (puedes seguir usando Luma sin conexión)."
            _manualError.value = offlineMsg
            recordError("GoogleSignIn", offlineMsg)
            return Result.failure(IllegalStateException(offlineMsg))
        }

        _manualError.value = null
        _isLaunchingSignIn.value = true
        val intentResult = googleProvider.buildSignInIntent()
        val intent = intentResult.getOrElse { err ->
            _isLaunchingSignIn.value = false
            val friendly = mapFriendlyCloudError(err.message)
            _manualError.value = friendly
            recordError("GoogleSignIn", friendly)
            return Result.failure(IllegalStateException(friendly))
        }

        return runCatching {
            val resolved = intent.resolveActivity(context.packageManager)
            if (resolved == null) {
                throw android.content.ActivityNotFoundException("No browser handler for Google OAuth")
            }
            _awaitingOAuthCallback.set(true)
            context.startActivity(intent)
        }.onFailure { err ->
            _awaitingOAuthCallback.set(false)
            _isLaunchingSignIn.value = false
            android.util.Log.e("LumaCloudAuth", "Unable to open Google OAuth window", err)
            val blockedMsg = "La ventana de Google fue bloqueada o no hay un navegador compatible disponible."
            _manualError.value = blockedMsg
            recordError("GoogleSignIn", blockedMsg)
        }
    }

    /**
     * Called when host Activity resumes so closing the Google OAuth window without completing login
     * resets the loading state and surfaces a clear message.
     */
    fun onHostActivityResumed() {
        if (!_awaitingOAuthCallback.get()) return
        scope.launch {
            kotlinx.coroutines.delay(900)
            if (_awaitingOAuthCallback.compareAndSet(true, false) && !driveRepo.authSnapshot.value.isSignedIn) {
                _isLaunchingSignIn.value = false
                if (_manualError.value.isNullOrBlank()) {
                    _manualError.value = "Cerraste la ventana de inicio de sesión antes de terminar."
                }
            }
        }
    }

    fun onOAuthCallbackHandled() {
        _awaitingOAuthCallback.set(false)
        _isLaunchingSignIn.value = false
    }

    suspend fun connectWithManualTokenOrRedirect(rawInput: String): Result<String> {
        val result = driveRepo.connectManualTokenOrRedirect(rawInput)
        result.onSuccess {
            _manualError.value = null
            markWelcomeCompleted(skippedOffline = false)
        }.onFailure { err ->
            val friendly = mapFriendlyCloudError(err.message)
            _manualError.value = friendly
            recordError("ManualSignIn", friendly)
        }
        return result
    }

    /**
     * Signs out cleanly and returns the app to 100% offline mode WITHOUT deleting local books/notebooks.
     */
    suspend fun signOutKeepLocalFiles() {
        driveRepo.signOut()
        _manualError.value = null
        prefs.edit().putBoolean(KEY_SKIPPED_OFFLINE, true).apply()
        _skippedOffline.value = true
    }

    /**
     * Revokes access, signs out, and removes local cloud profile/device cache metadata (keeps user's own Drive files).
     */
    suspend fun disconnectAndClearLocalCloudData() {
        driveRepo.signOut()
        _manualError.value = null
        _customDisplayName.value = ""
        prefs.edit()
            .remove(KEY_PROFILE_NAME)
            .remove(KEY_DEVICES_JSON)
            .putBoolean(KEY_SKIPPED_OFFLINE, true)
            .apply()
        _skippedOffline.value = true
        _devices.value = ensureCurrentDevicePresent(emptyList(), _currentDeviceName.value)
        driveRepo.clearStatusMessage()
    }

    suspend fun triggerSyncNow() {
        if (!uiState.value.isSignedIn) return
        _manualError.value = null
        driveRepo.syncAllNow()
    }

    fun clearManualError() {
        _manualError.value = null
        driveRepo.clearStatusMessage()
    }

    fun recordError(area: String, message: String) {
        val entry = CloudErrorLogEntry(
            timestampMs = System.currentTimeMillis(),
            area = sanitizePlainText(area, 32),
            message = sanitizePlainText(message, 240)
        )
        _errorLogs.value = (_errorLogs.value + entry).takeLast(40)
    }

    fun exportErrorLogsText(): String {
        val logs = _errorLogs.value
        if (logs.isEmpty()) {
            return "Luma ${CloudPublicConfig.APP_BETA_VERSION} · Sin errores registrados en esta sesión."
        }
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return buildString {
            appendLine("Luma ${CloudPublicConfig.APP_BETA_VERSION} · Dispositivo: ${_currentDeviceName.value}")
            logs.forEach { e ->
                appendLine("[${fmt.format(Date(e.timestampMs))}] (${e.area}) ${e.message}")
            }
        }
    }

    suspend fun resolveCurrentEntitlement(): EntitlementSnapshot =
        entitlementService.resolveEntitlement(uiState.value.user)

    fun mapFriendlyCloudError(raw: String?): String {
        val lower = raw.orEmpty().lowercase()
        return when {
            lower.contains("no está disponible en este momento") ->
                "El inicio de sesión no está disponible en este momento"
            lower.contains("popup_blocked") || lower.contains("bloquead") || lower.contains("activitynotfound") ->
                "La ventana de Google fue bloqueada o no hay un navegador compatible disponible."
            lower.contains("popup_closed") || lower.contains("user_cancelled") || lower.contains("cerraste la ventana") ->
                "Cerraste la ventana de inicio de sesión antes de terminar."
            lower.contains("access_denied") || lower.contains("org_internal") || lower.contains("tester") || lower.contains("unverified") ->
                "Tu correo no está autorizado en modo pruebas. Pide acceso al desarrollador."
            lower.contains("invalid_grant") || (lower.contains("token") && lower.contains("expir")) ->
                "Tu sesión caducó. Inicia sesión de nuevo; tus archivos locales y cambios pendientes siguen a salvo."
            lower.contains("storagequotaexceeded") || lower.contains("quota") ->
                "Sin espacio suficiente en tu Google Drive."
            lower.contains("client id") || lower.contains("vite_google_client_id") -> {
                android.util.Log.e("LumaCloudAuth", "Missing client id configuration: $raw")
                "El inicio de sesión no está disponible en este momento"
            }
            lower.contains("unable to resolve host") || lower.contains("sin internet") || lower.contains("sin conexión") ->
                "Sin conexión a internet. Conéctate e intenta de nuevo (puedes seguir usando Luma sin conexión)."
            else -> raw?.takeIf { it.isNotBlank() } ?: "No se pudo completar la operación en la nube."
        }
    }

    private fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val created = "luma-${UUID.randomUUID().toString().take(12)}"
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    private fun getOrCreateDeviceName(): String {
        val existing = prefs.getString(KEY_DEVICE_NAME, null)
        if (!existing.isNullOrBlank()) return existing
        val model = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { !it.isNullOrBlank() }
            .joinToString(" ")
            .trim()
            .ifBlank { "Dispositivo Android" }
        val clean = sanitizePlainText(model, 50)
        prefs.edit().putString(KEY_DEVICE_NAME, clean).apply()
        return clean
    }

    private fun loadStoredDevices(): List<CloudDeviceInfo> {
        val raw = prefs.getString(KEY_DEVICES_JSON, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length().coerceAtMost(CloudPublicConfig.MAX_SYNC_DEVICES)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = sanitizePlainText(o.optString("deviceId"), 80)
                    if (id.isNotBlank()) {
                        add(
                            CloudDeviceInfo(
                                deviceId = id,
                                deviceName = sanitizePlainText(o.optString("deviceName", "Dispositivo"), 50),
                                platform = sanitizePlainText(o.optString("platform", "Android"), 24),
                                lastActiveAtMs = o.optLong("lastActiveAtMs", System.currentTimeMillis()),
                                isCurrentDevice = id == _currentDeviceId
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun ensureCurrentDevicePresent(
        existing: List<CloudDeviceInfo>,
        currentName: String
    ): List<CloudDeviceInfo> {
        val others = existing.filterNot { it.deviceId == _currentDeviceId }
        val current = CloudDeviceInfo(
            deviceId = _currentDeviceId,
            deviceName = currentName,
            platform = "Android",
            lastActiveAtMs = System.currentTimeMillis(),
            isCurrentDevice = true
        )
        return (listOf(current) + others).take(CloudPublicConfig.MAX_SYNC_DEVICES)
    }

    private fun persistDevices(list: List<CloudDeviceInfo>) {
        val arr = JSONArray()
        list.take(CloudPublicConfig.MAX_SYNC_DEVICES).forEach { d ->
            arr.put(JSONObject().apply {
                put("deviceId", d.deviceId)
                put("deviceName", d.deviceName)
                put("platform", d.platform)
                put("lastActiveAtMs", d.lastActiveAtMs)
            })
        }
        prefs.edit().putString(KEY_DEVICES_JSON, arr.toString()).apply()
        _devices.value = list
    }

    private data class LocalProfileSlice(
        val welcomeDone: Boolean,
        val skipped: Boolean,
        val customDisplayName: String,
        val currentDeviceName: String,
        val devices: List<CloudDeviceInfo>
    )

    private data class AuthUiExtraSlice(
        val repoStatus: String?,
        val manualError: String?,
        val isAuthClientReady: Boolean,
        val isLaunchingSignIn: Boolean
    )

    companion object {
        private const val PREFS_NAME = "luma_cloud_account_v1"
        private const val KEY_WELCOME_DONE = "welcome_completed"
        private const val KEY_SKIPPED_OFFLINE = "welcome_skipped_offline"
        private const val KEY_PROFILE_NAME = "cloud_profile_display_name"
        private const val KEY_DEVICE_ID = "cloud_device_id"
        private const val KEY_DEVICE_NAME = "cloud_device_name"
        private const val KEY_DEVICES_JSON = "cloud_devices_json"

        @Volatile
        private var INSTANCE: CloudAccountManager? = null

        fun getInstance(context: Context): CloudAccountManager {
            return INSTANCE ?: synchronized(this) {
                val appCtx = context.applicationContext
                INSTANCE ?: CloudAccountManager(
                    appContext = appCtx,
                    driveRepo = DriveRepository.getInstance(appCtx)
                ).also { INSTANCE = it }
            }
        }
    }
}
