package com.example.drive

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.cloud.CloudSyncButtonState
import com.example.core.cloud.CloudSyncStatusButton
import com.example.ui.components.LumaDropdownHeader
import com.example.ui.components.LumaFloatingDropdownDialog
import com.example.ui.components.TactileButton
import com.example.ui.theme.LocalFolioPalette
import kotlinx.coroutines.launch

private val CardShape18 = RoundedCornerShape(18.dp)
private val ChipShape12 = RoundedCornerShape(12.dp)

/**
 * Full "Ajustes → Fuentes" Google Drive BYOC configuration panel.
 * Lets each user connect their own Google Drive directly (zero intermediate server):
 * - Direct OAuth 2.0 + PKCE sign-in / sign-out (Client ID configured from app environment)
 * - Multiple Drive folders with custom names, colors, enable/disable, and default save folder
 * - Auto-sync policy (Manual / Solo con Wi-Fi / Siempre) gated by signed-in state
 */
@Composable
fun DriveSourcesSettingsContent(
    driveRepo: DriveRepository
) {
    val palette = LocalFolioPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val auth by driveRepo.authSnapshot.collectAsStateWithLifecycle()
    val sources by driveRepo.sources.collectAsStateWithLifecycle(initialValue = emptyList())
    val statusMsg by driveRepo.statusMessage.collectAsStateWithLifecycle()
    val isOnline by driveRepo.isOnline.collectAsStateWithLifecycle()
    val cloudAccountManager = remember(context) { com.example.core.cloud.CloudAccountManager.getInstance(context) }
    val cloudState by cloudAccountManager.uiState.collectAsStateWithLifecycle()

    var folderLinkInput by remember { mutableStateOf("") }
    var folderNameInput by remember { mutableStateOf("") }
    var selectedColorHex by remember { mutableLongStateOf(DriveSourcePresetColors.first()) }
    var makeDefaultChecked by remember { mutableStateOf(sources.isEmpty()) }
    var isValidatingFolder by remember { mutableStateOf(false) }
    var folderError by remember { mutableStateOf<String?>(null) }

    var showPrivacyDialog by remember { mutableStateOf(false) }
    var authFeedback by remember { mutableStateOf<String?>(null) }

    val extractedPreviewId = remember(folderLinkInput) {
        DriveFolderLinkParser.extractFolderId(folderLinkInput)
    }

    if (showPrivacyDialog) {
        DrivePrivacyDialog(onDismiss = { showPrivacyDialog = false })
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("drive_sources_settings_root"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Connection & Status Hero Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape18)
                .background(palette.surfaceAlt.copy(alpha = 0.65f))
                .border(1.dp, palette.border, CardShape18)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (auth.isSignedIn) Color(0x2216A34A) else palette.accentSoft
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (auth.isSignedIn) Icons.Rounded.CloudDone else Icons.Rounded.CloudQueue,
                        contentDescription = null,
                        tint = if (auth.isSignedIn) Color(0xFF16A34A) else palette.accent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (auth.isSignedIn) "Google Drive conectado" else "Tu propio Google Drive",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.ink
                    )
                    Text(
                        text = if (auth.isSignedIn) {
                            auth.accountEmail.ifBlank { "Sesión activa · cifrada localmente" }
                        } else {
                            "Conexión directa a Drive API v3 sin servidores externos"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isOnline) Color(0x2016A34A) else Color(0x20E11D48))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isOnline) "En línea" else "Offline",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isOnline) Color(0xFF16A34A) else Color(0xFFE11D48)
                    )
                }
            }

            val displayedAuthBanner = authFeedback ?: cloudState.lastErrorMessage ?: statusMsg
            if (!displayedAuthBanner.isNullOrBlank()) {
                Text(
                    text = displayedAuthBanner,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ChipShape12)
                        .background(palette.accentSoft)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (auth.isSignedIn) {
                    OutlinedButton(
                        onClick = { scope.launch { cloudAccountManager.signOutKeepLocalFiles() } },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("drive_sign_out_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            tint = Color(0xFFE11D48),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("Desconectar", color = Color(0xFFE11D48))
                    }
                } else {
                    TactileButton(
                        onClick = {
                            authFeedback = null
                            cloudAccountManager.launchDirectGoogleSignIn(context).onFailure { err ->
                                authFeedback = err.message ?: "El inicio de sesión no está disponible en este momento"
                            }
                        },
                        enabled = cloudState.isAuthClientReady && !cloudState.isLaunchingSignIn,
                        cornerRadius = 14.dp,
                        lipDepth = 2.dp,
                        modifier = Modifier
                            .weight(1.25f)
                            .testTag("drive_sign_in_btn")
                    ) {
                        if (!cloudState.isAuthClientReady || cloudState.isLaunchingSignIn) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = palette.onAccent
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (!cloudState.isAuthClientReady) "Preparando…" else "Abriendo…",
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text("Conectar Google", fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showPrivacyDialog = true },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("drive_privacy_button")
                ) {
                    Icon(Icons.Rounded.Shield, contentDescription = "Privacidad", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Privacidad")
                }
            }

            if (!auth.isSignedIn) {
                Text(
                    text = "Luma usa tu Google Drive para guardar y sincronizar tus archivos. No usamos servidores propios.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted
                )
            }
        }

        // 3. Auto-Sync Mode & Dedicated Sync Action (Manual / Solo con Wi-Fi / Siempre)
        val syncControlsEnabled = auth.isSignedIn
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape18)
                .background(palette.surfaceAlt.copy(alpha = 0.65f))
                .border(1.dp, palette.border, CardShape18)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CloudSyncStatusButton(
                    accountManager = cloudAccountManager,
                    onOpenMyAccount = {},
                    onSyncClicked = if (syncControlsEnabled) {
                        {
                            scope.launch {
                                authFeedback = null
                                cloudAccountManager.triggerSyncNow()
                            }
                        }
                    } else null,
                    compact = false,
                    testTag = "drive_settings_sync_status_button"
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sincronización automática",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = palette.ink
                    )
                    Text(
                        text = when (cloudState.syncButtonState) {
                            CloudSyncButtonState.DISABLED_OFFLINE_ACCOUNT -> "Requiere iniciar sesión"
                            CloudSyncButtonState.SYNCING -> "Sincronizando con Google Drive…"
                            CloudSyncButtonState.PENDING -> "Cambios pendientes de respaldo"
                            CloudSyncButtonState.NO_INTERNET -> "Sin internet · En cola local"
                            CloudSyncButtonState.ERROR -> "Error en la última sincronización"
                            CloudSyncButtonState.SYNCED -> "Sincronizado con tu nube"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.inkMuted
                    )
                }
                if (syncControlsEnabled) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                authFeedback = null
                                cloudAccountManager.triggerSyncNow()
                            }
                        },
                        enabled = cloudState.syncButtonState != CloudSyncButtonState.SYNCING,
                        shape = ChipShape12,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("drive_settings_sync_now_btn")
                    ) {
                        Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (cloudState.syncButtonState == CloudSyncButtonState.SYNCING) "Sync…" else "Sincronizar",
                            maxLines = 1
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (syncControlsEnabled) 1f else 0.55f),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (mode in DriveSyncMode.entries) {
                    val selected = auth.syncMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(ChipShape12)
                            .background(if (selected && syncControlsEnabled) palette.accent else palette.surface)
                            .border(
                                width = if (selected && syncControlsEnabled) 0.dp else 1.dp,
                                color = if (selected && syncControlsEnabled) palette.accent else palette.border,
                                shape = ChipShape12
                            )
                            .clickable(enabled = syncControlsEnabled) { driveRepo.setSyncMode(mode) }
                            .padding(horizontal = 6.dp, vertical = 8.dp)
                            .testTag("drive_sync_mode_${mode.name}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (selected && syncControlsEnabled) palette.onAccent else palette.ink,
                            maxLines = 1
                        )
                    }
                }
            }
            Text(
                text = if (syncControlsEnabled) {
                    auth.syncMode.subtitle
                } else {
                    "Inicia sesión con Google para activar la sincronización automática entre tus dispositivos."
                },
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted
            )
        }

        // 4. Add a New Google Drive Folder Source by Link
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape18)
                .background(palette.surfaceAlt.copy(alpha = 0.65f))
                .border(1.dp, palette.border, CardShape18)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Agregar carpeta de Google Drive",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.ink
            )
            Text(
                text = "Pega cualquier enlace de carpeta de Drive (drive.google.com/drive/folders/… o ?usp=sharing). Luma extraerá el ID y validará el acceso.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted
            )

            OutlinedTextField(
                value = folderLinkInput,
                onValueChange = {
                    folderLinkInput = it
                    folderError = null
                },
                label = { Text("Enlace o ID de carpeta de Drive") },
                placeholder = { Text("https://drive.google.com/drive/folders/...") },
                singleLine = true,
                shape = ChipShape12,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.border
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drive_folder_link_input")
            )

            if (extractedPreviewId != null) {
                Text(
                    text = "ID detectado: $extractedPreviewId",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF16A34A)
                )
            }

            OutlinedTextField(
                value = folderNameInput,
                onValueChange = { folderNameInput = it },
                label = { Text("Nombre personalizado (opcional)") },
                placeholder = { Text("Ej. Medicina, Libros, Cuadernos") },
                singleLine = true,
                shape = ChipShape12,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = palette.accent,
                    unfocusedBorderColor = palette.border
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drive_folder_name_input")
            )

            // Source Accent Color picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Color:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.ink)
                for (colorHex in DriveSourcePresetColors) {
                    val chosen = selectedColorHex == colorHex
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(colorHex.toInt()))
                            .border(
                                width = if (chosen) 3.dp else 1.dp,
                                color = if (chosen) palette.ink else Color.White.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                            .clickable { selectedColorHex = colorHex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (chosen) {
                            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Usar como carpeta de guardado por defecto",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.ink,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = makeDefaultChecked,
                    onCheckedChange = { makeDefaultChecked = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = palette.accent)
                )
            }

            if (!folderError.isNullOrBlank()) {
                Text(
                    text = folderError!!,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE11D48)
                )
            }

            TactileButton(
                onClick = {
                    if (isValidatingFolder) return@TactileButton
                    isValidatingFolder = true
                    folderError = null
                    scope.launch {
                        val res = driveRepo.addSourceFromLink(
                            rawLinkOrId = folderLinkInput,
                            customName = folderNameInput,
                            accentColorHex = selectedColorHex,
                            makeDefault = makeDefaultChecked
                        )
                        isValidatingFolder = false
                        res.onSuccess {
                            folderLinkInput = ""
                            folderNameInput = ""
                            makeDefaultChecked = false
                        }.onFailure { err ->
                            folderError = (err as? DriveApiException)?.userMessage ?: err.localizedMessage
                        }
                    }
                },
                cornerRadius = 14.dp,
                lipDepth = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drive_add_source_btn")
            ) {
                if (isValidatingFolder) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = palette.onAccent)
                    Spacer(Modifier.width(8.dp))
                    Text("Validando carpeta…", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Validar y agregar fuente", fontWeight = FontWeight.Bold)
                }
            }
        }

        // 5. Configured Sources List
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape18)
                .background(palette.surfaceAlt.copy(alpha = 0.65f))
                .border(1.dp, palette.border, CardShape18)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Mis fuentes conectadas (${sources.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.ink
            )
            if (sources.isEmpty()) {
                Text(
                    text = "Aún no has agregado carpetas de Google Drive. Pega un enlace arriba para leer libros y sincronizar cuadernos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.inkMuted
                )
            } else {
                for (src in sources) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ChipShape12)
                            .background(palette.surface)
                            .border(1.dp, palette.border, ChipShape12)
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .testTag("drive_source_row_${src.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(src.accentColorHex.toInt()).copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = Color(src.accentColorHex.toInt()),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = src.name + if (src.isDefaultSaveTarget) " ★" else "",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = palette.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "ID: ${src.folderId.take(16)}…",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = palette.inkMuted,
                                maxLines = 1
                            )
                        }
                        // Default target toggle
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { scope.launch { driveRepo.setDefaultSource(src.id) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (src.isDefaultSaveTarget) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = "Marcar como carpeta predeterminada",
                                tint = if (src.isDefaultSaveTarget) Color(0xFFD97706) else palette.inkMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Switch(
                            checked = src.enabled,
                            onCheckedChange = { enabled ->
                                scope.launch { driveRepo.setSourceEnabled(src.id, enabled) }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(src.accentColorHex.toInt()))
                        )
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable { scope.launch { driveRepo.deleteSource(src.id) } },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Desconectar fuente",
                                tint = Color(0xFFE11D48),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DrivePrivacyDialog(onDismiss: () -> Unit) {
    val palette = LocalFolioPalette.current
    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        transformOrigin = TransformOrigin(0.5f, 0.5f),
        maxWidth = 460.dp,
        maxHeightFraction = 0.8f,
        testTag = "drive_privacy_dialog"
    ) { dismissAnim ->
        LumaDropdownHeader(
            title = "Privacidad y seguridad",
            subtitle = "Tu nube, tus archivos, cero servidores propios",
            leadingIcon = Icons.Rounded.Shield,
            onDismiss = dismissAnim
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrivacyPoint(
                title = "Sin servidores intermedios",
                detail = "Luma se comunica directamente desde tu teléfono con la API oficial de Google Drive v3 (googleapis.com). Ningún archivo, enlace ni credencial pasa por servidores propios ni de terceros."
            )
            PrivacyPoint(
                title = "Tokens cifrados en tu dispositivo",
                detail = "Tus tokens de acceso y sesión OAuth se guardan únicamente en este dispositivo cifrados con AES-256-GCM mediante Android Keystore."
            )
            PrivacyPoint(
                title = "Descarga bajo tu control",
                detail = "Luma solo descarga un libro o archivo cuando confirmas explícitamente. Puedes liberar espacio borrando la copia local en cualquier momento sin tocar tu archivo en Google Drive."
            )
            PrivacyPoint(
                title = "Revocación inmediata",
                detail = "Al pulsar 'Desconectar', Luma revoca el token ante Google y elimina todas las credenciales locales al instante."
            )
        }
    }
}

@Composable
private fun PrivacyPoint(title: String, detail: String) {
    val palette = LocalFolioPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ChipShape12)
            .background(palette.surfaceAlt.copy(alpha = 0.65f))
            .border(1.dp, palette.border, ChipShape12)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = palette.ink)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
    }
}
