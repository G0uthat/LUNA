package com.example.core.cloud

import com.example.BuildConfig

/**
 * Feature flags controlling which cloud providers appear in Welcome, Mi cuenta, and Fuentes.
 * In Beta, only Google Drive is active; Microsoft (OneDrive) and Terabox remain compiled
 * behind their flags and appear automatically with identical UI once enabled.
 */
object CloudFeatureFlags {
    const val googleDrive: Boolean = true
    const val microsoft: Boolean = false
    const val terabox: Boolean = false
}

/**
 * Public-only client configuration (ZERO client secrets are ever stored in app code).
 * Reads the Google OAuth Client ID strictly from build/environment configuration (`VITE_GOOGLE_CLIENT_ID`
 * in `.env` / AI Studio Secrets via `BuildConfig.VITE_GOOGLE_CLIENT_ID`). Users are never asked to enter it.
 */
object CloudPublicConfig {
    const val DEFAULT_MICROSOFT_CLIENT_ID: String = ""

    const val APP_BETA_VERSION: String = "Beta 1.0"
    const val MAX_SYNC_DEVICES: Int = 5
    const val BETA_FEEDBACK_FORM_URL: String = "https://forms.gle/luma-beta-feedback"

    // Standard folder & metadata file names inside the user's own cloud storage
    const val LUMA_ROOT_FOLDER_NAME: String = "Luma"
    const val LUMA_BOOKS_SUBFOLDER: String = "Libros"
    const val LUMA_NOTEBOOKS_SUBFOLDER: String = "Cuadernos"
    const val LUMA_SERIES_SUBFOLDER: String = "Series"
    const val LUMA_INDEX_FILE_NAME: String = "luma-index.json"
    const val LUMA_PROFILE_FILE_NAME: String = "luma-profile.json"

    // Exact required Google Drive Beta scopes (openid, email, profile, drive.file, drive.appdata)
    val GOOGLE_MINIMAL_SCOPES: List<String> = listOf(
        "openid",
        "email",
        "profile",
        "https://www.googleapis.com/auth/drive.file",
        "https://www.googleapis.com/auth/drive.appdata"
    )

    // Optional full Drive scope (used only if user explicitly opts into pasted-folder read access)
    val GOOGLE_FULL_SCOPES: List<String> = listOf(
        "openid",
        "email",
        "profile",
        "https://www.googleapis.com/auth/drive",
        "https://www.googleapis.com/auth/drive.appdata"
    )

    fun resolveGoogleClientId(): String {
        val configured = BuildConfig.VITE_GOOGLE_CLIENT_ID.trim()
        if (configured.isBlank() ||
            configured.startsWith("YOUR_", ignoreCase = true) ||
            configured.startsWith("MY_", ignoreCase = true)
        ) {
            return ""
        }
        return configured
    }
}
