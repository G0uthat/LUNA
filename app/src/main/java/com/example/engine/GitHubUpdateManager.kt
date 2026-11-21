package com.example.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class UpdateEnforcementMode {
    OPTIONAL_SKIPPABLE,
    MANDATORY_BLOCKING
}

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val title: String,
    val changelog: String,
    val apkDownloadUrl: String,
    val releasePageUrl: String,
    val apkSizeBytes: Long = 0L,
    val mode: UpdateEnforcementMode = UpdateEnforcementMode.OPTIONAL_SKIPPABLE,
    val blockMessage: String = ""
)

sealed class UpdateCheckOutcome {
    data object Idle : UpdateCheckOutcome()
    data object Checking : UpdateCheckOutcome()
    data class UpToDate(val checkedVersion: String) : UpdateCheckOutcome()
    data class UpdateFound(val info: AppUpdateInfo) : UpdateCheckOutcome()
    data class Error(val message: String) : UpdateCheckOutcome()
}

/**
 * In-app GitHub Release + update-policy.json updater for Luma:
 * - Only shows in-app modal dialogs while the user is actively inside Luma (no background system notifications).
 * - Supports TWO enforcement modes controlled directly from GitHub:
 *   1) OPTIONAL_SKIPPABLE: user can update now, remind later, or skip that specific version.
 *   2) MANDATORY_BLOCKING: non-skippable modal (Back & outside tap disabled) that also persists locally
 *      so turning off Wi-Fi / Airplane Mode cannot bypass a required update once detected.
 * - Opens verified HTTPS GitHub APK download URLs via ACTION_VIEW (zero REQUEST_INSTALL_PACKAGES).
 */
object GitHubUpdateManager {

    const val GITHUB_REPO = "G0uthat/Luma"

    private const val PREFS_NAME = "luma_github_updates_v1"
    private const val KEY_SKIPPED_VERSION = "skipped_version"
    private const val KEY_LAST_CHECK_MS = "last_check_ms"

    // Persisted mandatory lock cache (prevents offline/airplane-mode bypass)
    private const val KEY_LOCKED_VERSION_NAME = "locked_version_name"
    private const val KEY_LOCKED_VERSION_CODE = "locked_version_code"
    private const val KEY_LOCKED_APK_URL = "locked_apk_url"
    private const val KEY_LOCKED_RELEASE_URL = "locked_release_url"
    private const val KEY_LOCKED_CHANGELOG = "locked_changelog"
    private const val KEY_LOCKED_BLOCK_MESSAGE = "locked_block_message"
    private const val KEY_LOCKED_APK_SIZE = "locked_apk_size"

    private const val AUTO_CHECK_INTERVAL_MS = 10 * 60 * 1000L // 10 minutes between silent auto-checks

    private val allowedDownloadHosts = setOf(
        "github.com",
        "www.github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "raw.githubusercontent.com"
    )

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private val _activeUpdate = MutableStateFlow<AppUpdateInfo?>(null)
    val activeUpdate: StateFlow<AppUpdateInfo?> = _activeUpdate.asStateFlow()

    private val _checkOutcome = MutableStateFlow<UpdateCheckOutcome>(UpdateCheckOutcome.Idle)
    val checkOutcome: StateFlow<UpdateCheckOutcome> = _checkOutcome.asStateFlow()

    private var dismissedOptionalThisSessionVersion: String? = null

    /**
     * Immediately restores any locally cached mandatory update lock if the installed APK is still
     * older than the previously required version (prevents Airplane Mode bypass).
     * Automatically clears the lock once the user installs the newer APK.
     */
    fun enforceCachedMandatoryLockIfNeeded(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lockedVer = prefs.getString(KEY_LOCKED_VERSION_NAME, null)?.trim().orEmpty()
        val lockedCode = prefs.getInt(KEY_LOCKED_VERSION_CODE, 0)
        if (lockedVer.isEmpty() && lockedCode <= 0) return false

        val stillOutdated = isCurrentAppOutdated(
            remoteVersionName = lockedVer.ifEmpty { BuildConfig.VERSION_NAME },
            remoteVersionCode = lockedCode
        )
        if (!stillOutdated) {
            clearMandatoryLock(context)
            return false
        }

        val fallbackRelease = "https://github.com/$GITHUB_REPO/releases/latest"
        val lockedInfo = AppUpdateInfo(
            versionName = lockedVer.ifEmpty { "v$lockedCode" },
            versionCode = lockedCode,
            title = "Actualización obligatoria",
            changelog = prefs.getString(KEY_LOCKED_CHANGELOG, null).orEmpty()
                .ifBlank { "Esta versión de Luma requiere actualizar al APK más reciente publicado en GitHub." },
            apkDownloadUrl = prefs.getString(KEY_LOCKED_APK_URL, null).orEmpty().ifBlank { fallbackRelease },
            releasePageUrl = prefs.getString(KEY_LOCKED_RELEASE_URL, null).orEmpty().ifBlank { fallbackRelease },
            apkSizeBytes = prefs.getLong(KEY_LOCKED_APK_SIZE, 0L),
            mode = UpdateEnforcementMode.MANDATORY_BLOCKING,
            blockMessage = prefs.getString(KEY_LOCKED_BLOCK_MESSAGE, null).orEmpty()
        )
        _activeUpdate.value = lockedInfo
        _checkOutcome.value = UpdateCheckOutcome.UpdateFound(lockedInfo)
        return true
    }

    /**
     * Checks GitHub Releases API + optional `update-policy.json` in `main` branch for [GITHUB_REPO].
     * - [bypassThrottle] = true checks GitHub immediately (used on fresh app launch).
     * - [manualCheck] = true also ignores previously skipped optional versions.
     */
    suspend fun checkForUpdates(
        context: Context,
        manualCheck: Boolean = false,
        bypassThrottle: Boolean = false
    ): UpdateCheckOutcome = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Always re-verify any cached mandatory lock first (blocks offline bypass)
        val hasCachedLock = enforceCachedMandatoryLockIfNeeded(appCtx)

        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_MS, 0L)
        if (!manualCheck && !bypassThrottle && !hasCachedLock && (now - lastCheck) < AUTO_CHECK_INTERVAL_MS) {
            return@withContext _checkOutcome.value
        }

        _checkOutcome.value = UpdateCheckOutcome.Checking
        val repo = GITHUB_REPO

        val releaseData = fetchLatestGitHubRelease(repo)
        val policyData = fetchRemoteUpdatePolicy(repo)

        if (releaseData == null && policyData == null) {
            val outcome = if (hasCachedLock) {
                _activeUpdate.value?.let { UpdateCheckOutcome.UpdateFound(it) }
                    ?: UpdateCheckOutcome.Error("Sin conexión para verificar la actualización obligatoria.")
            } else {
                UpdateCheckOutcome.Error(
                    "No se pudo consultar actualizaciones en este momento."
                )
            }
            _checkOutcome.value = outcome
            return@withContext outcome
        }

        prefs.edit().putLong(KEY_LAST_CHECK_MS, now).apply()

        val resolved = mergeReleaseAndPolicy(repo, releaseData, policyData)
        val isUpdateAvailable = isCurrentAppOutdated(resolved.versionName, resolved.versionCode)
        val isMandatory = resolved.mode == UpdateEnforcementMode.MANDATORY_BLOCKING

        if (!isUpdateAvailable) {
            // Current build satisfies or exceeds remote version -> clear any old lock!
            clearMandatoryLock(appCtx)
            _activeUpdate.value = null
            val ok = UpdateCheckOutcome.UpToDate(BuildConfig.VERSION_NAME)
            _checkOutcome.value = ok
            return@withContext ok
        }

        if (isMandatory) {
            saveMandatoryLock(appCtx, resolved)
            _activeUpdate.value = resolved
            val found = UpdateCheckOutcome.UpdateFound(resolved)
            _checkOutcome.value = found
            return@withContext found
        }

        // Optional / Skippable update: clear stale mandatory lock if remote relaxed policy
        clearMandatoryLock(appCtx)
        val skippedVersion = prefs.getString(KEY_SKIPPED_VERSION, null)
        val alreadySkipped = !manualCheck && (
            skippedVersion.equals(resolved.versionName, ignoreCase = true) ||
                dismissedOptionalThisSessionVersion.equals(resolved.versionName, ignoreCase = true)
            )

        if (!alreadySkipped) {
            _activeUpdate.value = resolved
        }
        val found = UpdateCheckOutcome.UpdateFound(resolved)
        _checkOutcome.value = found
        found
    }

    /** Dismisses an optional update for the current session only ("Más tarde"). Never dismisses mandatory updates. */
    fun dismissOptionalForSession() {
        val current = _activeUpdate.value ?: return
        if (current.mode == UpdateEnforcementMode.MANDATORY_BLOCKING) return
        dismissedOptionalThisSessionVersion = current.versionName
        _activeUpdate.value = null
    }

    /** Persists skip for a specific optional version ("Omitir esta versión"). Never skips mandatory updates. */
    fun skipOptionalVersion(context: Context) {
        val current = _activeUpdate.value ?: return
        if (current.mode == UpdateEnforcementMode.MANDATORY_BLOCKING) return
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SKIPPED_VERSION, current.versionName)
            .apply()
        dismissedOptionalThisSessionVersion = current.versionName
        _activeUpdate.value = null
    }

    /**
     * Opens the verified HTTPS GitHub APK download URL via Android's standard ACTION_VIEW intent
     * (compliant with Google Play Policy — zero REQUEST_INSTALL_PACKAGES required).
     */
    fun openApkDownload(context: Context, updateInfo: AppUpdateInfo): Boolean {
        val targetUrl = sanitizeGitHubUrl(updateInfo.apkDownloadUrl)
            ?: sanitizeGitHubUrl(updateInfo.releasePageUrl)
            ?: return false
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun sanitizeGitHubUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return null
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        val host = uri.host?.lowercase() ?: return null
        val isAllowed = host in allowedDownloadHosts || host.endsWith(".githubusercontent.com")
        return if (isAllowed) trimmed else null
    }

    private data class GitHubReleasePayload(
        val tagName: String,
        val title: String,
        val body: String,
        val htmlUrl: String,
        val apkDownloadUrl: String,
        val apkSizeBytes: Long
    )

    private data class RemotePolicyPayload(
        val latestVersion: String,
        val latestVersionCode: Int,
        val minRequiredVersion: String,
        val minRequiredVersionCode: Int,
        val forceUpdate: Boolean,
        val apkUrl: String,
        val releaseNotes: String,
        val blockMessage: String
    )

    private fun fetchLatestGitHubRelease(repo: String): GitHubReleasePayload? {
        return runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Luma-Android-Updater/${BuildConfig.VERSION_NAME}")
                .build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val bodyStr = response.body?.string() ?: return@runCatching null
                val json = JSONObject(bodyStr)
                val tagName = json.optString("tag_name", "").trim()
                val title = json.optString("name", "").trim().ifEmpty { "Luma $tagName" }
                val body = json.optString("body", "").trim()
                val htmlUrl = json.optString("html_url", "https://github.com/$repo/releases/latest").trim()

                var bestApkUrl = ""
                var bestApkSize = 0L
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val name = asset.optString("name", "")
                        val downloadUrl = asset.optString("browser_download_url", "")
                        if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                            bestApkUrl = downloadUrl
                            bestApkSize = asset.optLong("size", 0L)
                            if (name.contains("luma", ignoreCase = true)) break
                        }
                    }
                }
                GitHubReleasePayload(
                    tagName = tagName,
                    title = title,
                    body = body,
                    htmlUrl = htmlUrl,
                    apkDownloadUrl = bestApkUrl.ifBlank { htmlUrl },
                    apkSizeBytes = bestApkSize
                )
            }
        }.getOrNull()
    }

    private fun fetchRemoteUpdatePolicy(repo: String): RemotePolicyPayload? {
        val branches = listOf("main", "master")
        for (branch in branches) {
            val result = runCatching {
                val request = Request.Builder()
                    .url("https://raw.githubusercontent.com/$repo/$branch/update-policy.json")
                    .header("Cache-Control", "no-cache")
                    .header("User-Agent", "Luma-Android-Updater/${BuildConfig.VERSION_NAME}")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val bodyStr = response.body?.string() ?: return@runCatching null
                    val json = JSONObject(bodyStr)
                    RemotePolicyPayload(
                        latestVersion = json.optString("latestVersion", "").trim(),
                        latestVersionCode = json.optInt("latestVersionCode", 0),
                        minRequiredVersion = json.optString("minRequiredVersion", "").trim(),
                        minRequiredVersionCode = json.optInt("minRequiredVersionCode", 0),
                        forceUpdate = json.optBoolean("forceUpdate", false),
                        apkUrl = json.optString("apkUrl", "").trim(),
                        releaseNotes = json.optString("releaseNotes", "").trim(),
                        blockMessage = json.optString("blockMessage", "").trim()
                    )
                }
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private fun mergeReleaseAndPolicy(
        repo: String,
        release: GitHubReleasePayload?,
        policy: RemotePolicyPayload?
    ): AppUpdateInfo {
        val fallbackPage = "https://github.com/$repo/releases/latest"
        val releaseVersion = release?.tagName?.let { cleanVersionString(it) }.orEmpty()
        val policyVersion = policy?.latestVersion?.let { cleanVersionString(it) }.orEmpty()

        val chosenVersion = when {
            releaseVersion.isNotEmpty() && policyVersion.isNotEmpty() ->
                if (compareSemanticVersions(policyVersion, releaseVersion) > 0) policyVersion else releaseVersion
            releaseVersion.isNotEmpty() -> releaseVersion
            policyVersion.isNotEmpty() -> policyVersion
            else -> BuildConfig.VERSION_NAME
        }

        val bodyText = release?.body.orEmpty()
        val titleText = release?.title.orEmpty()
        val combinedText = "$titleText\n$bodyText"

        // Parse optional markers in GitHub Release body (e.g. version_code: 3, min_version: 1.2, [FORCE])
        val bodyVersionCode = extractIntMarker(combinedText, "version_code")
        val bodyMinVersionCode = extractIntMarker(combinedText, "min_version_code")
        val bodyMinVersion = extractStringMarker(combinedText, "min_version")

        val chosenVersionCode = maxOf(
            policy?.latestVersionCode ?: 0,
            bodyVersionCode
        )

        val hasForceMarkerInRelease = containsForceMarker(combinedText)
        val belowPolicyMinVersion = !policy?.minRequiredVersion.isNullOrBlank() &&
            compareSemanticVersions(BuildConfig.VERSION_NAME, policy!!.minRequiredVersion) < 0
        val belowPolicyMinCode = (policy?.minRequiredVersionCode ?: 0) > BuildConfig.VERSION_CODE
        val belowBodyMinVersion = !bodyMinVersion.isNullOrBlank() &&
            compareSemanticVersions(BuildConfig.VERSION_NAME, bodyMinVersion) < 0
        val belowBodyMinCode = bodyMinVersionCode > BuildConfig.VERSION_CODE

        val isMandatory = (policy?.forceUpdate == true) ||
            hasForceMarkerInRelease ||
            belowPolicyMinVersion ||
            belowPolicyMinCode ||
            belowBodyMinVersion ||
            belowBodyMinCode

        val cleanChangelog = stripControlMarkers(
            when {
                bodyText.isNotBlank() -> bodyText
                !policy?.releaseNotes.isNullOrBlank() -> policy!!.releaseNotes
                else -> "Nueva actualización de Luma lista para descargar desde GitHub."
            }
        )

        val chosenApkUrl = sanitizeGitHubUrl(policy?.apkUrl.orEmpty())
            ?: sanitizeGitHubUrl(release?.apkDownloadUrl.orEmpty())
            ?: sanitizeGitHubUrl(release?.htmlUrl.orEmpty())
            ?: fallbackPage

        val releasePageUrl = sanitizeGitHubUrl(release?.htmlUrl.orEmpty()) ?: fallbackPage

        return AppUpdateInfo(
            versionName = chosenVersion,
            versionCode = chosenVersionCode,
            title = if (isMandatory) {
                "Actualización obligatoria"
            } else {
                titleText.ifBlank { "Nueva versión v$chosenVersion" }
            },
            changelog = cleanChangelog,
            apkDownloadUrl = chosenApkUrl,
            releasePageUrl = releasePageUrl,
            apkSizeBytes = release?.apkSizeBytes ?: 0L,
            mode = if (isMandatory) UpdateEnforcementMode.MANDATORY_BLOCKING else UpdateEnforcementMode.OPTIONAL_SKIPPABLE,
            blockMessage = policy?.blockMessage.orEmpty()
        )
    }

    private fun containsForceMarker(text: String): Boolean {
        val upper = text.uppercase()
        return upper.contains("[FORCE]") ||
            upper.contains("[OBLIGATORIA]") ||
            upper.contains("[OBLIGATORIO]") ||
            upper.contains("[MANDATORY]") ||
            Regex("force_update\\s*[:=]\\s*true", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    private fun extractIntMarker(text: String, key: String): Int {
        val match = Regex("$key\\s*[:=]\\s*(\\d+)", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private fun extractStringMarker(text: String, key: String): String? {
        val match = Regex("$key\\s*[:=]\\s*([vV]?\\d+(?:\\.\\d+)*)", RegexOption.IGNORE_CASE).find(text)
        return match?.groupValues?.getOrNull(1)?.let { cleanVersionString(it) }
    }

    private fun stripControlMarkers(raw: String): String {
        return raw.lines()
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed.startsWith("force_update", ignoreCase = true) ||
                    trimmed.startsWith("min_version", ignoreCase = true) ||
                    trimmed.startsWith("version_code", ignoreCase = true)
            }
            .joinToString("\n")
            .replace(Regex("\\[(FORCE|OBLIGATORIA|OBLIGATORIO|MANDATORY|OPTIONAL|SKIPPABLE)]", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    fun isCurrentAppOutdated(remoteVersionName: String, remoteVersionCode: Int): Boolean {
        if (remoteVersionCode > BuildConfig.VERSION_CODE) return true
        val cleanedRemote = cleanVersionString(remoteVersionName)
        if (cleanedRemote.isEmpty()) return false
        return compareSemanticVersions(cleanedRemote, BuildConfig.VERSION_NAME) > 0
    }

    fun cleanVersionString(raw: String): String {
        val match = Regex("(\\d+(?:\\.\\d+)*)").find(raw.trim())
        return match?.groupValues?.getOrNull(1) ?: ""
    }

    fun compareSemanticVersions(a: String, b: String): Int {
        val partsA = cleanVersionString(a).split('.').mapNotNull { it.toIntOrNull() }
        val partsB = cleanVersionString(b).split('.').mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(partsA.size, partsB.size)
        for (i in 0 until maxLen) {
            val va = partsA.getOrElse(i) { 0 }
            val vb = partsB.getOrElse(i) { 0 }
            if (va != vb) return va.compareTo(vb)
        }
        return 0
    }

    private fun saveMandatoryLock(context: Context, info: AppUpdateInfo) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOCKED_VERSION_NAME, info.versionName)
            .putInt(KEY_LOCKED_VERSION_CODE, info.versionCode)
            .putString(KEY_LOCKED_APK_URL, info.apkDownloadUrl)
            .putString(KEY_LOCKED_RELEASE_URL, info.releasePageUrl)
            .putString(KEY_LOCKED_CHANGELOG, info.changelog)
            .putString(KEY_LOCKED_BLOCK_MESSAGE, info.blockMessage)
            .putLong(KEY_LOCKED_APK_SIZE, info.apkSizeBytes)
            .apply()
    }

    private fun clearMandatoryLock(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LOCKED_VERSION_NAME)
            .remove(KEY_LOCKED_VERSION_CODE)
            .remove(KEY_LOCKED_APK_URL)
            .remove(KEY_LOCKED_RELEASE_URL)
            .remove(KEY_LOCKED_CHANGELOG)
            .remove(KEY_LOCKED_BLOCK_MESSAGE)
            .remove(KEY_LOCKED_APK_SIZE)
            .apply()
    }
}
