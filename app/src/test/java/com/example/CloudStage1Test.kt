package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.cloud.BetaEntitlementService
import com.example.core.cloud.CloudAccountManager
import com.example.core.cloud.CloudDeviceInfo
import com.example.core.cloud.CloudFeatureFlags
import com.example.core.cloud.CloudProviderId
import com.example.core.cloud.CloudPublicConfig
import com.example.core.cloud.CloudSyncButtonState
import com.example.core.cloud.CloudUserProfile
import com.example.core.cloud.EntitlementPlan
import com.example.core.cloud.LumaCloudProfile
import com.example.core.cloud.sanitizePlainText
import com.example.drive.DriveRepository
import com.example.drive.GoogleDriveAuthManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CloudStage1Test {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun featureFlags_onlyEnableGoogleDriveInBeta() {
        assertTrue(CloudFeatureFlags.googleDrive)
        assertFalse(CloudFeatureFlags.microsoft)
        assertFalse(CloudFeatureFlags.terabox)

        val manager = CloudAccountManager.getInstance(context)
        val enabled = manager.availableProviders.map { it.providerId }
        assertEquals(listOf(CloudProviderId.GOOGLE_DRIVE), enabled)
    }

    @Test
    fun googleScopes_matchExactRequiredList() {
        assertEquals(
            listOf(
                "openid",
                "email",
                "profile",
                "https://www.googleapis.com/auth/drive.file",
                "https://www.googleapis.com/auth/drive.appdata"
            ),
            CloudPublicConfig.GOOGLE_MINIMAL_SCOPES
        )
        assertEquals("openid", GoogleDriveAuthManager.SCOPE_OPENID)
        assertEquals("email", GoogleDriveAuthManager.SCOPE_EMAIL)
        assertEquals("profile", GoogleDriveAuthManager.SCOPE_PROFILE)
        assertEquals("https://www.googleapis.com/auth/drive.file", GoogleDriveAuthManager.SCOPE_DRIVE_FILE)
        assertEquals("https://www.googleapis.com/auth/drive.appdata", GoogleDriveAuthManager.SCOPE_DRIVE_APPDATA)
    }

    @Test
    fun entitlementService_returnsOfflineFreeAndSignedInBetaFullAccess() = runBlocking {
        val service = BetaEntitlementService()

        val offline = service.resolveEntitlement(user = null)
        assertEquals(EntitlementPlan.FREE, offline.plan)
        assertEquals("Modo sin conexión", offline.badgeLabel)
        assertFalse(offline.isBetaFullAccess)
        assertEquals(1, offline.maxDevices)

        val user = CloudUserProfile(
            userId = "google:12345",
            provider = CloudProviderId.GOOGLE_DRIVE,
            email = "lector@ejemplo.com",
            displayName = "Lector Beta"
        )
        val beta = service.resolveEntitlement(user = user)
        assertEquals(EntitlementPlan.PREMIUM, beta.plan)
        assertEquals("Beta – acceso completo", beta.badgeLabel)
        assertTrue(beta.isBetaFullAccess)
        assertEquals(CloudPublicConfig.MAX_SYNC_DEVICES, beta.maxDevices)
    }

    @Test
    fun lumaCloudProfile_mergesFieldsByTimestampAndCapsFiveDevices() {
        val local = LumaCloudProfile(
            schemaVersion = 1,
            userId = "sub-1",
            displayName = "Nombre Local Viejo",
            displayNameUpdatedAtMs = 1000L,
            defaultProvider = CloudProviderId.GOOGLE_DRIVE.name,
            defaultProviderUpdatedAtMs = 3000L,
            autoSaveEnabled = true,
            autoSaveUpdatedAtMs = 2500L,
            devices = listOf(
                CloudDeviceInfo("dev-1", "Pixel 9", "Android", lastActiveAtMs = 5000L, isCurrentDevice = true),
                CloudDeviceInfo("dev-2", "Tablet", "Android", lastActiveAtMs = 4000L),
                CloudDeviceInfo("dev-3", "Laptop", "Web", lastActiveAtMs = 3000L)
            )
        )
        val remote = LumaCloudProfile(
            schemaVersion = 1,
            userId = "sub-1",
            displayName = "Nombre Remoto Nuevo",
            displayNameUpdatedAtMs = 2000L,
            defaultProvider = CloudProviderId.ONEDRIVE.name,
            defaultProviderUpdatedAtMs = 1500L,
            autoSaveEnabled = false,
            autoSaveUpdatedAtMs = 1000L,
            devices = listOf(
                CloudDeviceInfo("dev-4", "PC Oficina", "Web", lastActiveAtMs = 4500L),
                CloudDeviceInfo("dev-5", "iPad", "Web", lastActiveAtMs = 3500L),
                CloudDeviceInfo("dev-6", "Celular Viejo", "Android", lastActiveAtMs = 1000L)
            )
        )

        val merged = LumaCloudProfile.merge(local, remote)
        assertEquals("Nombre Remoto Nuevo", merged.displayName)
        assertEquals(CloudProviderId.GOOGLE_DRIVE.name, merged.defaultProvider)
        assertTrue(merged.autoSaveEnabled)
        assertEquals(5, merged.devices.size)
        assertFalse(merged.devices.any { it.deviceId == "dev-6" })

        val roundTrip = LumaCloudProfile.fromJson(merged.toJson())
        assertNotNull(roundTrip)
        assertEquals(merged.displayName, roundTrip!!.displayName)
        assertEquals(5, roundTrip.devices.size)
    }

    @Test
    fun sanitizePlainText_stripsHtmlTagsAndControlCharacters() {
        val malicious = "<script>alert('xss')</script>Mi <b>Cuaderno</b>\u0000\u0007 de Estudio"
        val clean = sanitizePlainText(malicious, maxLen = 80)
        assertFalse(clean.contains("<"))
        assertFalse(clean.contains(">"))
        assertFalse(clean.contains("\u0000"))
        assertEquals("alert('xss')Mi Cuaderno de Estudio", clean)
    }

    @Test
    fun cloudAccountManager_handlesMissingClientIdAndDirectFlowErrorsCleanly() = runBlocking {
        val manager = CloudAccountManager.getInstance(context)
        val repo = DriveRepository.getInstance(context)
        repo.vault.saveClientConfig(clientId = "", apiKey = "", useMinimalScope = true)

        // Wait briefly for async init readiness
        var attempts = 0
        while (!manager.uiState.value.isAuthClientReady && attempts < 25) {
            delay(20)
            attempts++
        }
        assertTrue(manager.uiState.value.isAuthClientReady)

        // When VITE_GOOGLE_CLIENT_ID is missing, user sees only generic unavailable message
        val signInRes = manager.launchDirectGoogleSignIn(context)
        assertTrue(signInRes.isFailure)
        assertEquals(
            "El inicio de sesión no está disponible en este momento",
            signInRes.exceptionOrNull()?.message
        )

        // Ensure offline skip persists cleanly & sync button stays disabled offline
        manager.skipWelcomeUseOffline()
        delay(80)
        val state = manager.uiState.value
        assertTrue(state.hasCompletedWelcome)
        assertTrue(state.skippedWelcomeOffline)
        assertFalse(state.isSignedIn)
        assertEquals(CloudSyncButtonState.DISABLED_OFFLINE_ACCOUNT, state.syncButtonState)

        // Verify all required error categories produce clear Spanish copy
        val testerMsg = manager.mapFriendlyCloudError("Error 403: access_denied")
        assertTrue(testerMsg.contains("modo pruebas"))

        val closedMsg = manager.mapFriendlyCloudError("popup_closed_by_user")
        assertTrue(closedMsg.contains("Cerraste la ventana"))

        val blockedMsg = manager.mapFriendlyCloudError("popup_blocked_by_browser")
        assertTrue(blockedMsg.contains("bloqueada"))

        val offlineMsg = manager.mapFriendlyCloudError("Unable to resolve host")
        assertTrue(offlineMsg.contains("Sin conexión"))
    }
}
