package com.example.core.cloud

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.FolderShared
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.LumaCalligraphicLogo
import com.example.ui.components.LumaDropdownHeader
import com.example.ui.components.LumaFloatingDropdownDialog
import com.example.ui.components.TactileButton
import com.example.ui.theme.LocalFolioPalette
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val WelcomeCardShape = RoundedCornerShape(24.dp)
private val AccountCardShape = RoundedCornerShape(18.dp)

/**
 * Full-screen first-launch Welcome / Sign-In screen.
 * - Shows ONLY once on first launch; choosing "Omitir este paso (usar sin conexión)" saves the
 *   preference permanently and enters Luma offline without nagging again.
 * - Shows Microsoft (OneDrive) and Terabox buttons ONLY when enabled in [CloudFeatureFlags].
 * - Tapping "Continuar con Google" launches the direct Google OAuth flow (no intermediate consent screen,
 *   no Client ID input field).
 */
@Composable
fun CloudWelcomeScreen(
    accountManager: CloudAccountManager,
    onContinueIntoApp: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val context = LocalContext.current
    val state by accountManager.uiState.collectAsStateWithLifecycle()

    var feedbackBanner by remember { mutableStateOf<String?>(null) }
    val googleButtonEnabled = state.isAuthClientReady && !state.isLaunchingSignIn
    val displayedError = feedbackBanner ?: state.lastErrorMessage

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = if (palette.isDark) 0.22f else 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.14f),
                        radius = size.maxDimension * 0.55f
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = if (palette.isDark) 0.14f else 0.09f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.12f, size.height * 0.86f),
                        radius = size.maxDimension * 0.48f
                    )
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("cloud_welcome_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Authentic calligraphic Luma logo (preserved unchanged)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LumaCalligraphicLogo(
                    horizontalBias = 0.5f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                        .testTag("welcome_luma_logo")
                )
                Text(
                    text = "Tu estudio de lectura y cuadernos digitales",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.accent,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Sincroniza tus libros y cuadernos en todos tus dispositivos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.inkMuted,
                    textAlign = TextAlign.Center
                )
            }

                // Feature Card: Zero-server personal cloud promise
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(WelcomeCardShape)
                        .background(palette.surface.copy(alpha = 0.94f))
                        .border(1.dp, palette.border, WelcomeCardShape)
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WelcomeBenefitRow(
                        icon = Icons.Rounded.CloudDone,
                        title = "Tu propia nube, cero servidores",
                        subtitle = "Conecta directamente con tu Google Drive. Nadie más tiene acceso a tus archivos."
                    )
                    WelcomeBenefitRow(
                        icon = Icons.Rounded.Devices,
                        title = "Hasta 5 dispositivos sincronizados",
                        subtitle = "Retoma tus lecturas, resaltados y cuadernos en teléfono, tablet o PC."
                    )
                    WelcomeBenefitRow(
                        icon = Icons.Rounded.CloudOff,
                        title = "100% funcional sin conexión",
                        subtitle = "Lee y anota sin cuenta o sin internet; conecta tu nube solo cuando quieras."
                    )
                }

                // Primary Cloud Provider Buttons (Microsoft & Terabox strictly behind CloudFeatureFlags)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (CloudFeatureFlags.googleDrive) {
                        TactileButton(
                            onClick = {
                                feedbackBanner = null
                                accountManager.launchDirectGoogleSignIn(context).onFailure { err ->
                                    feedbackBanner = err.message ?: "El inicio de sesión no está disponible en este momento"
                                }
                            },
                            enabled = googleButtonEnabled,
                            cornerRadius = 16.dp,
                            lipDepth = 3.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 54.dp)
                                .testTag("welcome_continue_google_btn")
                        ) {
                            if (!state.isAuthClientReady || state.isLaunchingSignIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.2.dp,
                                    color = palette.onAccent
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = if (!state.isAuthClientReady) "Preparando Google…" else "Abriendo Google…",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Login,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Continuar con Google",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }

                        Text(
                            text = "Luma usa tu Google Drive para guardar y sincronizar tus archivos. No usamos servidores propios.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 6.dp)
                        )
                    }

                    if (!displayedError.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x1AE11D48))
                                .border(1.dp, Color(0x44E11D48), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("welcome_auth_error_banner"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFE11D48),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = displayedError,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.ink,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (CloudFeatureFlags.microsoft) {
                        OutlinedButton(
                            onClick = { feedbackBanner = "Microsoft OneDrive estará disponible pronto." },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("welcome_continue_microsoft_btn")
                        ) {
                            Icon(Icons.Rounded.CloudQueue, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Continuar con Microsoft", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (CloudFeatureFlags.terabox) {
                        OutlinedButton(
                            onClick = { feedbackBanner = "Terabox estará disponible pronto." },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .testTag("welcome_continue_terabox_btn")
                        ) {
                            Icon(Icons.Rounded.FolderShared, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Conectar Terabox (Experimental)", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Secondary offline skip action
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = {
                            accountManager.skipWelcomeUseOffline()
                            onContinueIntoApp()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("welcome_skip_offline_btn")
                    ) {
                        Text(
                            text = "Omitir este paso (usar sin conexión)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink
                        )
                    }
                    Text(
                        text = "Podrás iniciar sesión más tarde desde Mi cuenta",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted,
                        textAlign = TextAlign.Center
                    )
                }
        }
    }
}

@Composable
private fun WelcomeBenefitRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    val palette = LocalFolioPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = palette.accent, modifier = Modifier.size(19.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.ink
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted
            )
        }
    }
}

/**
 * "Mi cuenta" modal accessible from Library menu, Sync Button tooltip, and Settings.
 * Supports seamless real-time transitions between SIN SESIÓN (offline) and CON SESIÓN (signed in)
 * without restarting the app or losing any local files.
 */
@Composable
fun CloudMyAccountDialog(
    accountManager: CloudAccountManager,
    onOpenDriveSettings: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by accountManager.uiState.collectAsStateWithLifecycle()

    var profileNameDraft by remember(state.editableProfileName) { mutableStateOf(state.editableProfileName) }
    var deviceNameDraft by remember(state.currentDeviceName) { mutableStateOf(state.currentDeviceName) }
    var confirmWipeLocal by remember { mutableStateOf(false) }
    var localFeedback by remember { mutableStateOf<String?>(null) }

    val dateFmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        transformOrigin = TransformOrigin(0.5f, 0.12f),
        maxWidth = 460.dp,
        maxHeightFraction = 0.88f,
        testTag = "cloud_my_account_dialog"
    ) { dismissAnim ->
        LumaDropdownHeader(
            title = "Mi cuenta",
            subtitle = if (state.isSignedIn) state.entitlement.badgeLabel else "Modo sin conexión",
            leadingIcon = Icons.Rounded.AccountCircle,
            onDismiss = dismissAnim,
            closeTestTag = "cloud_my_account_close"
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!state.isSignedIn) {
                val googleButtonEnabled = state.isAuthClientReady && !state.isLaunchingSignIn
                val displayedError = localFeedback ?: state.lastErrorMessage

                // SIN SESIÓN card (direct Google Sign-In, exact same flow as Welcome)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AccountCardShape)
                        .background(palette.surfaceAlt.copy(alpha = 0.7f))
                        .border(1.dp, palette.border, AccountCardShape)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(palette.surface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PersonOutline,
                                contentDescription = null,
                                tint = palette.inkMuted,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "No has iniciado sesión",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = palette.ink
                            )
                            Text(
                                text = "Usando Luma en modo 100% local sin conexión",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkMuted
                            )
                        }
                    }

                    Text(
                        text = "Inicia sesión con Google para respaldar tus libros y cuadernos en tu propio Google Drive y sincronizar hasta 5 dispositivos. Todos tus archivos locales actuales se conservan intactos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted
                    )

                    TactileButton(
                        onClick = {
                            localFeedback = null
                            accountManager.launchDirectGoogleSignIn(context).onFailure { err ->
                                localFeedback = err.message ?: "El inicio de sesión no está disponible en este momento"
                            }
                        },
                        enabled = googleButtonEnabled,
                        cornerRadius = 14.dp,
                        lipDepth = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .testTag("my_account_signin_google_btn")
                    ) {
                        if (!state.isAuthClientReady || state.isLaunchingSignIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(17.dp),
                                strokeWidth = 2.dp,
                                color = palette.onAccent
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (!state.isAuthClientReady) "Preparando Google…" else "Abriendo Google…",
                                fontWeight = FontWeight.ExtraBold
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Iniciar sesión con Google", fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    Text(
                        text = "Luma usa tu Google Drive para guardar y sincronizar tus archivos. No usamos servidores propios.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted,
                        textAlign = TextAlign.Center
                    )

                    if (!displayedError.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x1AE11D48))
                                .border(1.dp, Color(0x44E11D48), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFE11D48), modifier = Modifier.size(18.dp))
                            Text(
                                text = displayedError,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.ink,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                CloudSyncSectionCard(
                    accountManager = accountManager,
                    state = state,
                    onOpenDriveSettings = onOpenDriveSettings,
                    onSyncClicked = {
                        scope.launch {
                            accountManager.triggerSyncNow()
                        }
                    }
                )
            } else {
                // CON SESIÓN: Profile, Plan Badge, Devices, Sign Out & Wipe
                val user = state.user
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AccountCardShape)
                        .background(palette.surfaceAlt.copy(alpha = 0.65f))
                        .border(1.dp, palette.accent.copy(alpha = 0.35f), AccountCardShape)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(palette.accent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (user?.displayName?.firstOrNull()?.uppercaseChar() ?: 'L').toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = palette.onAccent
                            )
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = user?.displayName.orEmpty().ifBlank { "Usuario Luma" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = palette.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = user?.email.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(palette.accentSoft)
                                    .padding(horizontal = 9.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Rounded.Verified, contentDescription = null, tint = palette.accent, modifier = Modifier.size(13.dp))
                                Text(
                                    text = state.entitlement.badgeLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = palette.accent
                                )
                            }
                        }
                    }

                    if (state.tokenExpiredNeedsRelogin) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0x1AF59E0B))
                                .border(1.dp, Color(0x55F59E0B), RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                            Text(
                                text = "Tu sesión de prueba caducó (7 días). Inicia sesión de nuevo sin perder archivos locales.",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.ink,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Editable Profile Name (synced via luma-profile.json in appDataFolder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = profileNameDraft,
                            onValueChange = { profileNameDraft = it },
                            label = { Text("Nombre de perfil") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        TactileButton(
                            onClick = {
                                accountManager.updateProfileDisplayName(profileNameDraft)
                                localFeedback = "Nombre de perfil guardado"
                            },
                            cornerRadius = 12.dp,
                            lipDepth = 2.dp,
                            modifier = Modifier.heightIn(min = 50.dp)
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = "Guardar nombre", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                CloudSyncSectionCard(
                    accountManager = accountManager,
                    state = state,
                    onOpenDriveSettings = onOpenDriveSettings,
                    onSyncClicked = {
                        scope.launch {
                            localFeedback = null
                            accountManager.triggerSyncNow()
                        }
                    }
                )

                // Connected Devices (up to 5)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(AccountCardShape)
                        .background(palette.surfaceAlt.copy(alpha = 0.5f))
                        .border(1.dp, palette.border, AccountCardShape)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Devices, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Dispositivos (${state.connectedDevices.size}/${CloudPublicConfig.MAX_SYNC_DEVICES})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = palette.ink
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = deviceNameDraft,
                            onValueChange = { deviceNameDraft = it },
                            label = { Text("Nombre de este dispositivo") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                accountManager.updateCurrentDeviceName(deviceNameDraft)
                                localFeedback = "Nombre de dispositivo actualizado"
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Guardar")
                        }
                    }

                    for (dev in state.connectedDevices) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.surface)
                                .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Rounded.Smartphone, contentDescription = null, tint = palette.accent, modifier = Modifier.size(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = dev.deviceName + if (dev.isCurrentDevice) " (Este dispositivo)" else "",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = palette.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${dev.platform} · Último uso: ${dateFmt.format(Date(dev.lastActiveAtMs))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.inkMuted
                                )
                            }
                            if (!dev.isCurrentDevice) {
                                TextButton(onClick = { accountManager.removeDevice(dev.deviceId) }) {
                                    Text("Quitar", color = Color(0xFFE11D48), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Sign out & Disconnect/Wipe controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                accountManager.signOutKeepLocalFiles()
                                localFeedback = "Sesión cerrada. Tus archivos locales siguen disponibles sin conexión."
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("my_account_signout_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Cerrar sesión", fontWeight = FontWeight.Bold)
                    }
                }

                if (!confirmWipeLocal) {
                    TextButton(
                        onClick = { confirmWipeLocal = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("my_account_disconnect_wipe_btn")
                    ) {
                        Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = Color(0xFFE11D48), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Desconectar y borrar datos locales de nube",
                            color = Color(0xFFE11D48),
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x1AE11D48))
                            .border(1.dp, Color(0x55E11D48), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "¿Desconectar cuenta y borrar metadatos locales de nube? Tus archivos en Google Drive no se eliminarán.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.ink
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { confirmWipeLocal = false },
                                modifier = Modifier.weight(1f)
                            ) { Text("Cancelar") }
                            TactileButton(
                                onClick = {
                                    confirmWipeLocal = false
                                    scope.launch {
                                        accountManager.disconnectAndClearLocalCloudData()
                                        localFeedback = "Cuenta desconectada y datos locales de nube borrados."
                                    }
                                },
                                containerColor = Color(0xFFE11D48),
                                shadowColor = Color(0xFF9F1239),
                                contentColor = Color.White,
                                cornerRadius = 12.dp,
                                lipDepth = 2.dp,
                                modifier = Modifier.weight(1f)
                            ) { Text("Confirmar", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            if (!localFeedback.isNullOrBlank()) {
                Text(
                    text = localFeedback!!,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.accent
                )
            }

            HorizontalDivider(color = palette.border)

            // Beta Footer: Version + Send Feedback + Copy Local Error Logs
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Versión ${CloudPublicConfig.APP_BETA_VERSION}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = palette.inkMuted
                    )
                    Text(
                        text = "Cero servidores propios",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.inkMuted
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(CloudPublicConfig.BETA_FEEDBACK_FORM_URL))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.Feedback, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Comentarios", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            cm?.setPrimaryClip(
                                ClipData.newPlainText("Luma Error Logs", accountManager.exportErrorLogsText())
                            )
                            Toast.makeText(context, "Registro local copiado", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copiar logs", maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudSyncSectionCard(
    accountManager: CloudAccountManager,
    state: CloudAccountUiState,
    onOpenDriveSettings: (() -> Unit)?,
    onSyncClicked: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val statusTitle = when (state.syncButtonState) {
        CloudSyncButtonState.DISABLED_OFFLINE_ACCOUNT -> "Sincronización en pausa"
        CloudSyncButtonState.SYNCED -> "Sincronizado"
        CloudSyncButtonState.SYNCING -> "Sincronizando…"
        CloudSyncButtonState.PENDING -> "Cambios pendientes"
        CloudSyncButtonState.ERROR -> "Atención en la sincronización"
        CloudSyncButtonState.NO_INTERNET -> "Sin conexión a internet"
    }
    val statusSubtitle = when (state.syncButtonState) {
        CloudSyncButtonState.DISABLED_OFFLINE_ACCOUNT -> "Inicia sesión con Google para respaldar y sincronizar entre dispositivos."
        CloudSyncButtonState.SYNCED -> "Tu perfil y respaldo en la nube están al día."
        CloudSyncButtonState.SYNCING -> "Conectando directamente con tu Google Drive…"
        CloudSyncButtonState.PENDING -> "Hay cambios listos para subirse a tu nube."
        CloudSyncButtonState.ERROR -> state.lastErrorMessage ?: "Revisa tu conexión o vuelve a intentarlo."
        CloudSyncButtonState.NO_INTERNET -> "Puedes seguir leyendo y anotando; los cambios se subirán al reconectar."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AccountCardShape)
            .background(palette.surfaceAlt.copy(alpha = 0.55f))
            .border(1.dp, palette.border, AccountCardShape)
            .padding(14.dp)
            .testTag("my_account_sync_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CloudSyncStatusButton(
                accountManager = accountManager,
                onOpenMyAccount = {},
                onSyncClicked = if (state.isSignedIn) onSyncClicked else null,
                compact = false,
                testTag = "my_account_sync_status_button"
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = palette.ink
                )
                Text(
                    text = statusSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted
                )
            }
        }

        if (state.isSignedIn || onOpenDriveSettings != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.isSignedIn) {
                    TactileButton(
                        onClick = onSyncClicked,
                        enabled = state.syncButtonState != CloudSyncButtonState.SYNCING,
                        cornerRadius = 12.dp,
                        lipDepth = 2.dp,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("my_account_sync_now_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (state.syncButtonState == CloudSyncButtonState.SYNCING) "Sincronizando…" else "Sincronizar ahora",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
                if (onOpenDriveSettings != null) {
                    OutlinedButton(
                        onClick = onOpenDriveSettings,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(if (state.isSignedIn) 0.9f else 1f)
                            .heightIn(min = 48.dp)
                            .testTag("my_account_open_drive_settings_btn")
                    ) {
                        Icon(Icons.Rounded.FolderShared, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Fuentes", maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * Sync Status Button housed cleanly inside dedicated sections ("Mi cuenta" and "Ajustes -> Fuentes")
 * so it never clutters or covers the Library header, Reader canvas, or Notebook drawing toolbar.
 *
 * - SIN SESIÓN ([CloudSyncButtonState.DISABLED_OFFLINE_ACCOUNT]):
 *   Clearly attenuated (low opacity, muted greyish tone, NO X icon). Never triggers sync;
 *   tapping displays a discreet tooltip ("Inicia sesión para sincronizar").
 * - CON SESIÓN:
 *   Vibrant active accent styling reflecting live state (Synced check, Syncing spin, Pending dot,
 *   Soft Red error, or No-Internet cloud-off that still queues edits).
 */
@Composable
fun CloudSyncStatusButton(
    accountManager: CloudAccountManager,
    onOpenMyAccount: () -> Unit,
    onSyncClicked: (() -> Unit)? = null,
    compact: Boolean = false,
    testTag: String = "cloud_sync_status_button"
) {
    val palette = LocalFolioPalette.current
    val scope = rememberCoroutineScope()
    val state by accountManager.uiState.collectAsStateWithLifecycle()
    var showTooltip by remember { mutableStateOf(false) }

    val isDisabled = state.syncButtonState == CloudSyncButtonState.DISABLED_OFFLINE_ACCOUNT
    val infiniteTransition = rememberInfiniteTransition(label = "sync_spin_transition")
    val spinDegrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync_spin_deg"
    )

    val containerColor = when (state.syncButtonState) {
        CloudSyncButtonState.DISABLED_OFFLINE_ACCOUNT -> palette.surfaceAlt.copy(alpha = 0.55f)
        CloudSyncButtonState.SYNCED -> palette.accentSoft
        CloudSyncButtonState.SYNCING -> palette.accent
        CloudSyncButtonState.PENDING -> palette.accentSoft
        CloudSyncButtonState.ERROR -> Color(0x1EE11D48)
        CloudSyncButtonState.NO_INTERNET -> palette.surfaceAlt
    }
    val borderColor = when (state.syncButtonState) {
        CloudSyncButtonState.DISABLED_OFFLINE_ACCOUNT -> palette.border.copy(alpha = 0.55f)
        CloudSyncButtonState.SYNCED -> palette.accent.copy(alpha = 0.45f)
        CloudSyncButtonState.SYNCING -> palette.accent
        CloudSyncButtonState.PENDING -> Color(0x88D97706)
        CloudSyncButtonState.ERROR -> Color(0x66E11D48)
        CloudSyncButtonState.NO_INTERNET -> palette.border
    }
    val iconTint = when (state.syncButtonState) {
        CloudSyncButtonState.DISABLED_OFFLINE_ACCOUNT -> palette.inkMuted.copy(alpha = 0.55f)
        CloudSyncButtonState.SYNCED -> palette.accent
        CloudSyncButtonState.SYNCING -> palette.onAccent
        CloudSyncButtonState.PENDING -> palette.accent
        CloudSyncButtonState.ERROR -> Color(0xFFDC2626)
        CloudSyncButtonState.NO_INTERNET -> palette.inkMuted
    }
    val iconVector = when (state.syncButtonState) {
        CloudSyncButtonState.DISABLED_OFFLINE_ACCOUNT -> Icons.Rounded.CloudSync
        CloudSyncButtonState.SYNCED -> Icons.Rounded.CloudDone
        CloudSyncButtonState.SYNCING -> Icons.Rounded.Sync
        CloudSyncButtonState.PENDING -> Icons.Rounded.CloudUpload
        CloudSyncButtonState.ERROR -> Icons.Rounded.CloudQueue
        CloudSyncButtonState.NO_INTERNET -> Icons.Rounded.CloudOff
    }
    val accessibilityDesc = when (state.syncButtonState) {
        CloudSyncButtonState.DISABLED_OFFLINE_ACCOUNT -> "Sincronización deshabilitada. Inicia sesión para sincronizar"
        CloudSyncButtonState.SYNCED -> "Sincronizado con tu nube"
        CloudSyncButtonState.SYNCING -> "Sincronizando con tu nube"
        CloudSyncButtonState.PENDING -> "Cambios pendientes de sincronizar"
        CloudSyncButtonState.ERROR -> "Error de sincronización"
        CloudSyncButtonState.NO_INTERNET -> "Sin internet. Los cambios se guardarán en cola"
    }

    val touchMin = if (compact) 40.dp else 48.dp
    val visualSize = if (compact) 34.dp else 38.dp

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .sizeIn(minWidth = touchMin, minHeight = touchMin)
                .clip(CircleShape)
                .clickable {
                    if (isDisabled) {
                        showTooltip = true
                    } else if (state.syncButtonState == CloudSyncButtonState.ERROR ||
                        state.syncButtonState == CloudSyncButtonState.NO_INTERNET
                    ) {
                        showTooltip = true
                    } else {
                        if (onSyncClicked != null) {
                            onSyncClicked()
                        } else {
                            scope.launch { accountManager.triggerSyncNow() }
                        }
                    }
                }
                .semantics { contentDescription = accessibilityDesc }
                .testTag(testTag),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(visualSize)
                    .alpha(if (isDisabled) 0.52f else 1f)
                    .clip(CircleShape)
                    .background(containerColor)
                    .border(1.dp, borderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier
                        .size(if (compact) 17.dp else 19.dp)
                        .rotate(if (state.syncButtonState == CloudSyncButtonState.SYNCING) spinDegrees else 0f)
                )
            }

            // Warning dot for Pending uploads or Error state
            if (state.syncButtonState == CloudSyncButtonState.PENDING ||
                state.syncButtonState == CloudSyncButtonState.ERROR
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 5.dp, end = 5.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.syncButtonState == CloudSyncButtonState.ERROR) Color(0xFFDC2626)
                            else Color(0xFFD97706)
                        )
                        .border(1.5.dp, palette.surface, CircleShape)
                )
            }
        }

        DropdownMenu(
            expanded = showTooltip,
            onDismissRequest = { showTooltip = false },
            modifier = Modifier
                .background(palette.surface)
                .widthIn(min = 210.dp, max = 265.dp)
                .testTag("${testTag}_tooltip")
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when (state.syncButtonState) {
                    CloudSyncButtonState.DISABLED_OFFLINE_ACCOUNT -> {
                        Text(
                            text = "Inicia sesión para sincronizar",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink
                        )
                        Text(
                            text = "Tu app funciona sin conexión. Conecta Google Drive cuando quieras respaldar en la nube.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted
                        )
                        Text(
                            text = "Ir a Mi cuenta →",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    showTooltip = false
                                    onOpenMyAccount()
                                }
                                .padding(vertical = 4.dp)
                                .testTag("sync_tooltip_open_account_link")
                        )
                    }
                    CloudSyncButtonState.NO_INTERNET -> {
                        Text(
                            text = "Sin conexión a internet",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink
                        )
                        Text(
                            text = "Puedes seguir editando con normalidad. Los cambios se encolan y se subirán al reconectar.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted
                        )
                    }
                    CloudSyncButtonState.ERROR -> {
                        Text(
                            text = "Atención en la sincronización",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626)
                        )
                        Text(
                            text = state.lastErrorMessage ?: "Revisa tu conexión o vuelve a iniciar sesión.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Reintentar",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = palette.accent,
                                modifier = Modifier
                                    .clickable {
                                        showTooltip = false
                                        scope.launch { accountManager.triggerSyncNow() }
                                    }
                                    .padding(vertical = 4.dp)
                            )
                            Text(
                                text = "Mi cuenta",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = palette.ink,
                                modifier = Modifier
                                    .clickable {
                                        showTooltip = false
                                        onOpenMyAccount()
                                    }
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = "Sincronización activa",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = palette.accent
                        )
                    }
                }
            }
        }
    }
}
