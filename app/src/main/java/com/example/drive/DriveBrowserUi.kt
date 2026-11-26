package com.example.drive

import android.graphics.BitmapFactory
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.LumaCapsuleOptionRow
import com.example.ui.components.LumaDropdownHeader
import com.example.ui.components.LumaFloatingDropdownDialog
import com.example.ui.components.TactileButton
import com.example.ui.theme.LocalFolioPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TileShape = RoundedCornerShape(16.dp)
private val PillShape = RoundedCornerShape(50)

/**
 * Header toolbar & controls for the Google Drive Sources browser inside LibraryScreen.
 */
@Composable
fun DriveBrowserHeaderBlock(
    driveRepo: DriveRepository,
    sources: List<DriveSourceEntity>,
    selectedSourceId: Long?,
    breadcrumbs: List<DriveBreadcrumb>,
    filterKind: DriveFilterKind,
    sortOrder: DriveSortOrder,
    isGridView: Boolean,
    pendingQueueCount: Int,
    onSelectSource: (DriveSourceEntity?) -> Unit,
    onNavigateBreadcrumb: (Int) -> Unit,
    onSelectFilter: (DriveFilterKind) -> Unit,
    onSelectSort: (DriveSortOrder) -> Unit,
    onToggleGridView: () -> Unit,
    onOpenDriveSettings: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val scope = rememberCoroutineScope()
    val isOnline by driveRepo.isOnline.collectAsStateWithLifecycle()
    val isRefreshing by driveRepo.isRefreshing.collectAsStateWithLifecycle()
    val isSyncing by driveRepo.isSyncing.collectAsStateWithLifecycle()
    val statusMessage by driveRepo.statusMessage.collectAsStateWithLifecycle()
    val activeTransfer by driveRepo.activeTransfer.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("drive_browser_header"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Primary Sync + Refresh + View Mode + Settings Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TactileButton(
                onClick = { scope.launch { driveRepo.syncAllNow() } },
                cornerRadius = 14.dp,
                lipDepth = 2.dp,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("drive_sync_now_btn")
            ) {
                if (isSyncing || isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = palette.onAccent)
                } else {
                    Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (pendingQueueCount > 0) "Sincronizar ($pendingQueueCount)" else "Sincronizar",
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.surfaceAlt)
                    .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                    .clickable {
                        scope.launch {
                            val activeSrc = sources.firstOrNull { it.id == selectedSourceId }
                            val currentFolder = breadcrumbs.lastOrNull()?.folderId
                            if (activeSrc != null && currentFolder != null) {
                                driveRepo.refreshFolder(activeSrc.id, currentFolder, loadMore = false)
                            } else {
                                driveRepo.refreshAllEnabledSources()
                            }
                        }
                    }
                    .testTag("drive_refresh_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Actualizar carpeta", tint = palette.ink, modifier = Modifier.size(20.dp))
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.surfaceAlt)
                    .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                    .clickable(onClick = onToggleGridView)
                    .testTag("drive_toggle_view_mode_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                    contentDescription = if (isGridView) "Vista en lista" else "Vista en cuadrícula",
                    tint = palette.ink,
                    modifier = Modifier.size(20.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.accentSoft)
                    .border(1.dp, palette.accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenDriveSettings)
                    .testTag("drive_open_sources_settings_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = "Configurar fuentes de Drive", tint = palette.accent, modifier = Modifier.size(20.dp))
            }
        }

        // Offline / Status banner
        if (!isOnline || !statusMessage.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (!isOnline) Color(0x1AE11D48) else palette.accentSoft)
                    .border(
                        1.dp,
                        if (!isOnline) Color(0x44E11D48) else palette.accent.copy(alpha = 0.3f),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (!isOnline) Icons.Rounded.CloudOff else Icons.Rounded.CloudDone,
                    contentDescription = null,
                    tint = if (!isOnline) Color(0xFFE11D48) else palette.accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = statusMessage ?: "Modo sin conexión: los cambios se guardarán en la cola offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.ink,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!statusMessage.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable { driveRepo.clearStatusMessage() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Cerrar aviso", tint = palette.inkMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Active Download / Upload Transfer Progress Bar (Pause / Resume / Cancel)
        activeTransfer?.let { transfer ->
            DriveTransferProgressCard(
                transfer = transfer,
                onPause = driveRepo::pauseActiveTransfer,
                onResume = driveRepo::resumeActiveTransfer,
                onCancel = driveRepo::cancelActiveTransfer
            )
        }

        // Source Selector Chips (color-coded)
        if (sources.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val allSelected = selectedSourceId == null
                Box(
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .clip(PillShape)
                        .background(if (allSelected) palette.accent else palette.surfaceAlt)
                        .border(1.dp, if (allSelected) palette.accent else palette.border, PillShape)
                        .clickable { onSelectSource(null) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Todas (${sources.size})",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (allSelected) palette.onAccent else palette.ink
                    )
                }
                for (src in sources) {
                    val selected = selectedSourceId == src.id
                    val accentColor = Color(src.accentColorHex.toInt())
                    Row(
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .clip(PillShape)
                            .background(if (selected) accentColor else palette.surfaceAlt)
                            .border(1.dp, if (selected) accentColor else palette.border, PillShape)
                            .clickable { onSelectSource(src) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = if (selected) Color.White else accentColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = src.name,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else palette.ink,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Breadcrumbs when inside subfolders
        if (breadcrumbs.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surfaceAlt.copy(alpha = 0.7f))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onNavigateBreadcrumb(breadcrumbs.lastIndex - 1) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Subir carpeta", tint = palette.accent, modifier = Modifier.size(18.dp))
                }
                breadcrumbs.forEachIndexed { idx, crumb ->
                    val isLast = idx == breadcrumbs.lastIndex
                    Text(
                        text = crumb.title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isLast) FontWeight.ExtraBold else FontWeight.Medium,
                        color = if (isLast) palette.ink else palette.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !isLast) { onNavigateBreadcrumb(idx) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                    if (!isLast) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = palette.inkMuted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Type Filters + Sort Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (kind in DriveFilterKind.entries) {
                val active = filterKind == kind
                Box(
                    modifier = Modifier
                        .heightIn(min = 34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) palette.accent else palette.surfaceAlt)
                        .clickable { onSelectFilter(kind) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = kind.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (active) palette.onAccent else palette.inkMuted
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            for (sort in DriveSortOrder.entries) {
                val active = sortOrder == sort
                Box(
                    modifier = Modifier
                        .heightIn(min = 34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) palette.accentSoft else Color.Transparent)
                        .border(1.dp, if (active) palette.accent else palette.border, RoundedCornerShape(10.dp))
                        .clickable { onSelectSort(sort) }
                        .padding(horizontal = 9.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↕ ${sort.label}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (active) palette.accent else palette.inkMuted
                    )
                }
            }
        }
    }
}

@Composable
fun DriveEmptySetupCard(
    onOpenSettings: () -> Unit
) {
    val palette = LocalFolioPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(22.dp))
            .padding(22.dp)
            .testTag("drive_empty_setup_card"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(palette.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.CloudQueue, contentDescription = null, tint = palette.accent, modifier = Modifier.size(30.dp))
        }
        Text(
            text = "Conecta tu propio Google Drive",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = palette.ink
        )
        Text(
            text = "Agrega carpetas de tu Google Drive para ver miniaturas de tus PDFs, DOCX, imágenes y cuadernos .luma, descargarlos bajo confirmación y sincronizar cambios entre dispositivos.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkMuted
        )
        TactileButton(
            onClick = onOpenSettings,
            cornerRadius = 14.dp,
            lipDepth = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("drive_empty_configure_btn")
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Configurar en Ajustes → Fuentes", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DriveTransferProgressCard(
    transfer: DriveTransferProgress,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val palette = LocalFolioPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .border(1.dp, palette.accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(12.dp)
            .testTag("drive_transfer_progress_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (transfer.isUpload) Icons.Rounded.CloudUpload else Icons.Rounded.CloudDownload,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = (if (transfer.isUpload) "Subiendo: " else "Descargando: ") + transfer.fileName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val speedText = DriveFolderLinkParser.formatSpeed(transfer.speedBytesPerSec)
                val sizeText = "${DriveFolderLinkParser.formatBytes(transfer.bytesTransferred)} / ${DriveFolderLinkParser.formatBytes(transfer.totalBytes)}"
                Text(
                    text = buildString {
                        append("${transfer.percent}% · $sizeText")
                        if (transfer.isPaused) append(" · Pausado")
                        else if (speedText.isNotEmpty()) append(" · $speedText")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.inkMuted
                )
            }
            if (!transfer.isUpload) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(palette.surfaceAlt)
                        .clickable { if (transfer.isPaused) onResume() else onPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (transfer.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        contentDescription = if (transfer.isPaused) "Reanudar" else "Pausar",
                        tint = palette.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AE11D48))
                        .clickable(onClick = onCancel),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancelar descarga", tint = Color(0xFFE11D48), modifier = Modifier.size(18.dp))
                }
            }
        }
        LinearProgressIndicator(
            progress = { transfer.fraction },
            color = palette.accent,
            trackColor = palette.surfaceAlt,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(PillShape)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DriveFileGridTile(
    file: DriveFileEntity,
    driveRepo: DriveRepository,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val thumbBitmap = rememberLazyDriveThumbnail(file, driveRepo)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(palette.surface)
            .border(1.dp, palette.border, TileShape)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(8.dp)
            .testTag("drive_file_tile_${file.driveFileId}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.76f)
                .clip(RoundedCornerShape(12.dp))
                .background(kindTint(file.kind).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            if (thumbBitmap != null) {
                Image(
                    bitmap = thumbBitmap,
                    contentDescription = file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = kindIcon(file.kind),
                    contentDescription = file.name,
                    tint = kindTint(file.kind),
                    modifier = Modifier.size(38.dp)
                )
            }

            // Format badge top-left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = file.kind.label,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }

            // Cloud vs Local Check badge top-right
            if (!file.isFolder) {
                val downloaded = file.isDownloadedLocally
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(
                            if (downloaded) Color(0xFF16A34A) else Color.Black.copy(alpha = 0.62f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (downloaded) Icons.Rounded.CheckCircle else Icons.Rounded.CloudDownload,
                        contentDescription = if (downloaded) "Guardado en el dispositivo" else "En la nube",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Sync status badge bottom-left (Pending / Conflict / Error / Uploading)
            if (file.status != DriveFileSyncStatus.CLOUD_ONLY && file.status != DriveFileSyncStatus.SYNCED) {
                val badgeColor = syncStatusColor(file.status)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = file.status.label,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Text(
            text = file.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = palette.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (file.isFolder) "Carpeta" else DriveFolderLinkParser.formatBytes(file.sizeBytes),
            style = MaterialTheme.typography.labelSmall,
            color = palette.inkMuted,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DriveFileListRow(
    file: DriveFileEntity,
    driveRepo: DriveRepository,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val thumbBitmap = rememberLazyDriveThumbnail(file, driveRepo)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TileShape)
            .background(palette.surface)
            .border(1.dp, palette.border, TileShape)
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(10.dp)
            .testTag("drive_file_row_${file.driveFileId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 58.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(kindTint(file.kind).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            if (thumbBitmap != null) {
                Image(
                    bitmap = thumbBitmap,
                    contentDescription = file.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(kindIcon(file.kind), contentDescription = null, tint = kindTint(file.kind), modifier = Modifier.size(24.dp))
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(file.kind.label)
                    if (!file.isFolder && file.sizeBytes > 0L) {
                        append(" · ${DriveFolderLinkParser.formatBytes(file.sizeBytes)}")
                    }
                    if (!file.isFolder) {
                        append(" · ${file.status.label}")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted,
                maxLines = 1
            )
        }

        if (!file.isFolder) {
            val downloaded = file.isDownloadedLocally
            Icon(
                imageVector = if (downloaded) Icons.Rounded.CheckCircle else Icons.Rounded.CloudDownload,
                contentDescription = if (downloaded) "Descargado" else "En la nube",
                tint = if (downloaded) Color(0xFF16A34A) else palette.accent,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Abrir carpeta", tint = palette.inkMuted, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun rememberLazyDriveThumbnail(
    file: DriveFileEntity,
    driveRepo: DriveRepository
): ImageBitmap? {
    var thumbPath by remember(file.driveFileId, file.localThumbnailPath) {
        mutableStateOf(file.localThumbnailPath)
    }
    var bitmap by remember(file.driveFileId) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(file.driveFileId, file.localThumbnailPath, file.thumbnailLink) {
        if (file.isFolder) return@LaunchedEffect
        val resolvedPath = if (thumbPath.isNotBlank() && File(thumbPath).exists()) {
            thumbPath
        } else {
            driveRepo.ensureThumbnailLoaded(file)?.also { thumbPath = it } ?: ""
        }
        if (resolvedPath.isNotBlank()) {
            bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(resolvedPath, opts)
                    val sample = (maxOf(opts.outWidth, opts.outHeight) / 360).coerceAtLeast(1)
                    BitmapFactory.decodeFile(
                        resolvedPath,
                        BitmapFactory.Options().apply { inSampleSize = sample }
                    )?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return bitmap
}

/**
 * Confirmation modal before downloading any cloud-only Drive file:
 * "¿Desea descargar [NOMBRE]? Tamaño: X MB" with thumbnail + Descargar / Cancelar.
 */
@Composable
fun DriveDownloadConfirmDialog(
    file: DriveFileEntity,
    driveRepo: DriveRepository,
    onConfirmDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val thumb = rememberLazyDriveThumbnail(file, driveRepo)

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        transformOrigin = TransformOrigin(0.5f, 0.5f),
        maxWidth = 380.dp,
        maxHeightFraction = 0.72f,
        testTag = "drive_download_confirm_dialog"
    ) { dismissAnim ->
        LumaDropdownHeader(
            title = "Descargar de Google Drive",
            subtitle = file.kind.label,
            leadingIcon = Icons.Rounded.CloudDownload,
            onDismiss = dismissAnim
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 92.dp, height = 122.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(kindTint(file.kind).copy(alpha = 0.14f))
                    .border(1.dp, palette.border, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (thumb != null) {
                    Image(bitmap = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(kindIcon(file.kind), contentDescription = null, tint = kindTint(file.kind), modifier = Modifier.size(40.dp))
                }
            }

            Text(
                text = "¿Desea descargar ${file.name}?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                color = palette.ink
            )
            Text(
                text = "Tamaño: ${if (file.sizeBytes > 0L) DriveFolderLinkParser.formatBytes(file.sizeBytes) else "Calculado al descargar"}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.accent
            )
            Text(
                text = "Se guardará en este dispositivo para lectura sin internet y se abrirá automáticamente.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = dismissAnim,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("drive_download_cancel_btn")
                ) {
                    Text("Cancelar")
                }
                TactileButton(
                    onClick = {
                        dismissAnim()
                        onConfirmDownload()
                    },
                    cornerRadius = 14.dp,
                    lipDepth = 2.dp,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("drive_download_confirm_btn")
                ) {
                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Descargar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Full long-press context menu for any Drive item:
 * Abrir, Descargar, Sincronizar, Renombrar, Mover a otra fuente, Liberar espacio,
 * Eliminar de Drive (with double confirmation), and Información.
 */
@Composable
fun DriveFileContextMenuDialog(
    file: DriveFileEntity,
    allSources: List<DriveSourceEntity>,
    onOpen: () -> Unit,
    onDownload: () -> Unit,
    onSync: () -> Unit,
    onRename: (String) -> Unit,
    onMoveToSource: (DriveSourceEntity) -> Unit,
    onFreeLocalCopy: () -> Unit,
    onDeleteFromDrive: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    var showRenameMode by remember { mutableStateOf(false) }
    var renameDraft by remember(file.driveFileId) { mutableStateOf(file.name) }
    var showMoveMode by remember { mutableStateOf(false) }
    var showInfoMode by remember { mutableStateOf(false) }
    var confirmDeleteStep by remember { mutableStateOf(0) }

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        transformOrigin = TransformOrigin(0.5f, 0.5f),
        maxWidth = 390.dp,
        maxHeightFraction = 0.84f,
        testTag = "drive_file_context_menu"
    ) { dismissAnim ->
        LumaDropdownHeader(
            title = file.name,
            subtitle = "${file.kind.label} · ${DriveFolderLinkParser.formatBytes(file.sizeBytes)}",
            leadingIcon = kindIcon(file.kind),
            onDismiss = dismissAnim
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when {
                showRenameMode -> {
                    OutlinedTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it },
                        label = { Text("Nuevo nombre en Drive") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showRenameMode = false }, modifier = Modifier.weight(1f)) { Text("Volver") }
                        TactileButton(
                            onClick = {
                                onRename(renameDraft)
                                dismissAnim()
                            },
                            cornerRadius = 14.dp,
                            lipDepth = 2.dp,
                            modifier = Modifier.weight(1f)
                        ) { Text("Renombrar", fontWeight = FontWeight.Bold) }
                    }
                }

                showMoveMode -> {
                    Text("Mover a otra fuente de Drive:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = palette.ink)
                    val targets = allSources.filter { it.id != file.sourceId }
                    if (targets.isEmpty()) {
                        Text("Agrega otra carpeta en Ajustes → Fuentes para mover archivos.", style = MaterialTheme.typography.bodySmall, color = palette.inkMuted)
                    } else {
                        for (src in targets) {
                            LumaCapsuleOptionRow(
                                icon = Icons.Rounded.Folder,
                                title = src.name,
                                subtitle = "Mover a esta carpeta de Drive",
                                onClick = {
                                    onMoveToSource(src)
                                    dismissAnim()
                                },
                                testTag = "drive_ctx_move_target_${src.id}"
                            )
                        }
                    }
                    OutlinedButton(onClick = { showMoveMode = false }, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
                }

                showInfoMode -> {
                    val dateStr = if (file.modifiedTimeMs > 0L) {
                        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(file.modifiedTimeMs))
                    } else "Desconocida"
                    InfoLine("Nombre", file.name)
                    InfoLine("Tipo", "${file.kind.label} (${file.mimeType})")
                    InfoLine("Tamaño", DriveFolderLinkParser.formatBytes(file.sizeBytes))
                    InfoLine("Última modificación", dateStr)
                    InfoLine("Estado", file.status.label)
                    InfoLine("Copia local", if (file.isDownloadedLocally) "Disponible offline" else "Solo en Google Drive")
                    if (file.lastError.isNotBlank()) {
                        InfoLine("Último aviso", file.lastError)
                    }
                    OutlinedButton(onClick = { showInfoMode = false }, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
                }

                confirmDeleteStep == 1 -> {
                    Text(
                        text = "¿Eliminar '${file.name}' permanentemente de Google Drive?",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFE11D48)
                    )
                    Text(
                        text = "Esta acción borrará el archivo de tu cuenta de Google Drive y también su copia local. Confirma una segunda vez para continuar.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { confirmDeleteStep = 0 }, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                        TactileButton(
                            onClick = { confirmDeleteStep = 2 },
                            containerColor = Color(0xFFE11D48),
                            shadowColor = Color(0xFF9F1239),
                            contentColor = Color.White,
                            cornerRadius = 14.dp,
                            lipDepth = 2.dp,
                            modifier = Modifier.weight(1f)
                        ) { Text("Sí, continuar", fontWeight = FontWeight.Bold) }
                    }
                }

                confirmDeleteStep == 2 -> {
                    Text(
                        text = "Confirmación final",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFE11D48)
                    )
                    Text(
                        text = "Pulsa 'Eliminar definitivamente' para borrar '${file.name}' de Google Drive.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkMuted
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { confirmDeleteStep = 0 }, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                        TactileButton(
                            onClick = {
                                onDeleteFromDrive()
                                dismissAnim()
                            },
                            containerColor = Color(0xFFE11D48),
                            shadowColor = Color(0xFF9F1239),
                            contentColor = Color.White,
                            cornerRadius = 14.dp,
                            lipDepth = 2.dp,
                            modifier = Modifier.weight(1f)
                        ) { Text("Eliminar definitivamente", fontWeight = FontWeight.Bold) }
                    }
                }

                else -> {
                    if (!file.isFolder) {
                        LumaCapsuleOptionRow(
                            icon = Icons.Rounded.AutoStories,
                            title = "Abrir",
                            subtitle = if (file.isDownloadedLocally) "Abrir directo sin descargar" else "Descargar y abrir en Luma",
                            onClick = {
                                dismissAnim()
                                onOpen()
                            },
                            testTag = "drive_ctx_open"
                        )
                        if (!file.isDownloadedLocally) {
                            LumaCapsuleOptionRow(
                                icon = Icons.Rounded.CloudDownload,
                                title = "Descargar",
                                subtitle = "Guardar copia en este dispositivo",
                                onClick = {
                                    dismissAnim()
                                    onDownload()
                                },
                                testTag = "drive_ctx_download"
                            )
                        }
                        LumaCapsuleOptionRow(
                            icon = Icons.Rounded.Sync,
                            title = "Sincronizar",
                            subtitle = "Subir cambios o refrescar con Drive",
                            onClick = {
                                dismissAnim()
                                onSync()
                            },
                            testTag = "drive_ctx_sync"
                        )
                    }
                    LumaCapsuleOptionRow(
                        icon = Icons.Rounded.DriveFileRenameOutline,
                        title = "Renombrar",
                        subtitle = "Cambiar nombre en Google Drive",
                        onClick = { showRenameMode = true },
                        testTag = "drive_ctx_rename"
                    )
                    LumaCapsuleOptionRow(
                        icon = Icons.AutoMirrored.Rounded.DriveFileMove,
                        title = "Mover a otra fuente",
                        subtitle = "Trasladar a otra carpeta conectada",
                        onClick = { showMoveMode = true },
                        testTag = "drive_ctx_move"
                    )
                    if (file.isDownloadedLocally) {
                        LumaCapsuleOptionRow(
                            icon = Icons.Rounded.Storage,
                            title = "Liberar espacio (borrar copia local)",
                            subtitle = "Mantiene tu archivo intacto en Google Drive",
                            onClick = {
                                onFreeLocalCopy()
                                dismissAnim()
                            },
                            testTag = "drive_ctx_free_local"
                        )
                    }
                    LumaCapsuleOptionRow(
                        icon = Icons.Rounded.Info,
                        title = "Información",
                        subtitle = "Tamaño, fecha, tipo y estado de sincronización",
                        onClick = { showInfoMode = true },
                        testTag = "drive_ctx_info"
                    )
                    LumaCapsuleOptionRow(
                        icon = Icons.Rounded.DeleteForever,
                        title = "Eliminar de Drive",
                        subtitle = "Requiere doble confirmación",
                        isDestructive = true,
                        onClick = { confirmDeleteStep = 1 },
                        testTag = "drive_ctx_delete_drive"
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    val palette = LocalFolioPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surfaceAlt.copy(alpha = 0.65f))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = palette.inkMuted)
        Text(value, style = MaterialTheme.typography.bodySmall, color = palette.ink)
    }
}

/**
 * Conflict resolution dialog when both local and Google Drive versions changed.
 */
@Composable
fun DriveConflictResolutionDialog(
    conflict: DriveConflictItem,
    onResolve: (DriveConflictResolution) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val fmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        maxWidth = 400.dp,
        maxHeightFraction = 0.75f,
        testTag = "drive_conflict_dialog"
    ) { dismissAnim ->
        LumaDropdownHeader(
            title = "Conflicto de versiones",
            subtitle = conflict.fileName,
            leadingIcon = Icons.Rounded.WarningAmber,
            onDismiss = dismissAnim
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Este archivo cambió tanto en tu dispositivo (${fmt.format(Date(conflict.localModifiedMs))}) como en Google Drive (${fmt.format(Date(conflict.remoteModifiedMs))}). ¿Qué versión deseas conservar?",
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted
            )
            TactileButton(
                onClick = {
                    dismissAnim()
                    onResolve(DriveConflictResolution.KEEP_LOCAL)
                },
                cornerRadius = 14.dp,
                lipDepth = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Conservar versión local (subir a Drive)", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = {
                    dismissAnim()
                    onResolve(DriveConflictResolution.KEEP_REMOTE)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Usar versión de Google Drive")
            }
            OutlinedButton(
                onClick = {
                    dismissAnim()
                    onResolve(DriveConflictResolution.KEEP_BOTH)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text("Conservar ambas (crear copia renombrada)")
            }
        }
    }
}

/**
 * Source destination picker when uploading a local book or creating/syncing a notebook to Drive.
 */
@Composable
fun DriveDestinationPickerDialog(
    title: String,
    sources: List<DriveSourceEntity>,
    allowLocalOnly: Boolean,
    onSelectSource: (DriveSourceEntity?) -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalFolioPalette.current
    val defaultSource = sources.firstOrNull { it.isDefaultSaveTarget } ?: sources.firstOrNull()
    var selectedId by remember(sources) { mutableStateOf(defaultSource?.id) }

    LumaFloatingDropdownDialog(
        onDismiss = onDismiss,
        alignment = Alignment.Center,
        maxWidth = 390.dp,
        maxHeightFraction = 0.72f,
        testTag = "drive_destination_picker_dialog"
    ) { dismissAnim ->
        LumaDropdownHeader(
            title = title,
            subtitle = "Elige la carpeta de Google Drive",
            leadingIcon = Icons.Rounded.CloudUpload,
            onDismiss = dismissAnim
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (src in sources) {
                val chosen = selectedId == src.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (chosen) palette.accentSoft else palette.surfaceAlt)
                        .border(
                            width = if (chosen) 1.5.dp else 1.dp,
                            color = if (chosen) palette.accent else palette.border,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedId = src.id }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, tint = Color(src.accentColorHex.toInt()), modifier = Modifier.size(22.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = src.name + if (src.isDefaultSaveTarget) " (Predeterminada)" else "",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = palette.ink
                        )
                    }
                    if (chosen) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = palette.accent, modifier = Modifier.size(20.dp))
                    }
                }
            }

            if (allowLocalOnly) {
                TextButton(
                    onClick = {
                        dismissAnim()
                        onSelectSource(null)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Guardar solo en este dispositivo (sin subir ahora)")
                }
            }

            TactileButton(
                onClick = {
                    val chosen = sources.firstOrNull { it.id == selectedId } ?: defaultSource
                    dismissAnim()
                    onSelectSource(chosen)
                },
                cornerRadius = 14.dp,
                lipDepth = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Subir a carpeta seleccionada", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun kindIcon(kind: DriveFileKind): ImageVector = when (kind) {
    DriveFileKind.FOLDER -> Icons.Rounded.Folder
    DriveFileKind.PDF -> Icons.Rounded.PictureAsPdf
    DriveFileKind.LUMA_NOTEBOOK -> Icons.Rounded.Draw
    DriveFileKind.IMAGE -> Icons.Rounded.Image
    DriveFileKind.EPUB, DriveFileKind.FB2 -> Icons.Rounded.AutoStories
    else -> Icons.Rounded.Description
}

private fun kindTint(kind: DriveFileKind): Color = when (kind) {
    DriveFileKind.FOLDER -> Color(0xFF2563EB)
    DriveFileKind.PDF -> Color(0xFFE11D48)
    DriveFileKind.DOCX -> Color(0xFF1D4ED8)
    DriveFileKind.LUMA_NOTEBOOK -> Color(0xFF7C3AED)
    DriveFileKind.IMAGE -> Color(0xFF0D9488)
    DriveFileKind.EPUB, DriveFileKind.FB2 -> Color(0xFFD97706)
    DriveFileKind.PPTX -> Color(0xFFEA580C)
    else -> Color(0xFF475569)
}

private fun syncStatusColor(status: DriveFileSyncStatus): Color = when (status) {
    DriveFileSyncStatus.SYNCED -> Color(0xFF16A34A)
    DriveFileSyncStatus.PENDING_UPLOAD -> Color(0xFFD97706)
    DriveFileSyncStatus.UPLOADING, DriveFileSyncStatus.DOWNLOADING -> Color(0xFF2563EB)
    DriveFileSyncStatus.CONFLICT -> Color(0xFFEA580C)
    DriveFileSyncStatus.ERROR -> Color(0xFFE11D48)
    DriveFileSyncStatus.CLOUD_ONLY -> Color(0xFF475569)
}
