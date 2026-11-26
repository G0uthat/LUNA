package com.example.core.cloud

enum class EntitlementPlan(val id: String) {
    FREE("free"),
    PREMIUM("premium")
}

data class EntitlementSnapshot(
    val plan: EntitlementPlan,
    val badgeLabel: String,
    val expiresAtMs: Long? = null,
    val minVersion: String = CloudPublicConfig.APP_BETA_VERSION,
    val maxDevices: Int = CloudPublicConfig.MAX_SYNC_DEVICES,
    val isBetaFullAccess: Boolean = true
)

/**
 * Swappable plan/entitlement abstraction.
 *
 * SECURITY NOTE:
 * Client-side entitlement checks are strictly for user experience (UI badges and local gates).
 * When monetized or strictly enforced in production, plug in an implementation that sends the
 * user's signed OIDC ID token to a lightweight verification endpoint returning
 * `{ plan, expira, min_version, max_devices }`.
 */
interface EntitlementService {
    suspend fun resolveEntitlement(
        user: CloudUserProfile?,
        idToken: String? = null
    ): EntitlementSnapshot
}

/**
 * Beta-phase implementation: grants full access ("Beta – acceso completo") to any signed-in user
 * and free offline access when signed out.
 */
class BetaEntitlementService : EntitlementService {
    override suspend fun resolveEntitlement(
        user: CloudUserProfile?,
        idToken: String?
    ): EntitlementSnapshot {
        return if (user != null) {
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
    }
}
