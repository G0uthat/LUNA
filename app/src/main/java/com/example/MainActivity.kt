package com.example

import android.os.Bundle
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.FolioDatabase
import com.example.data.local.FolioRepository
import com.example.data.local.PreferencesRepository
import com.example.data.model.ReaderPreferences
import com.example.data.model.TargetFpsOption
import com.example.engine.DisplayRefreshRateController
import com.example.engine.SocialRepository
import com.example.engine.SystemMediaController
import com.example.ui.components.DynamicIslandNotificationBanner
import com.example.ui.library.LibraryScreen
import com.example.ui.library.LibraryViewModel
import com.example.ui.reader.ReaderScreen
import com.example.ui.reader.ReaderViewModel
import com.example.ui.theme.FolioTheme
import com.example.ui.theme.LocalFolioPalette
import com.example.ui.theme.paletteFor
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private var currentTargetFps: TargetFpsOption = TargetFpsOption.AUTO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DisplayRefreshRateController.applyRefreshRate(this, currentTargetFps)

        val db = FolioDatabase.getInstance(applicationContext)
        val repository = FolioRepository(db.folioDao())
        val preferencesRepository = PreferencesRepository(applicationContext)
        SystemMediaController.start(applicationContext)
        SocialRepository.getInstance(applicationContext)

        setContent {
            val prefs by preferencesRepository.preferencesFlow.collectAsStateWithLifecycle(
                initialValue = ReaderPreferences()
            )
            val palette = paletteFor(prefs.theme)

            LaunchedEffect(prefs.targetFps) {
                currentTargetFps = prefs.targetFps
                DisplayRefreshRateController.applyRefreshRate(this@MainActivity, prefs.targetFps)
            }

            LaunchedEffect(palette.isDark) {
                if (palette.isDark) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        ),
                        navigationBarStyle = SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    )
                }
            }

            FolioTheme(readingTheme = prefs.theme, useSystemFont = prefs.useSystemFont) {
                var activeDocumentId by rememberSaveable { mutableStateOf<Long?>(null) }
                val incomingNotification by SystemMediaController.incomingNotification.collectAsStateWithLifecycle()

                val libraryViewModel: LibraryViewModel = viewModel(
                    factory = remember {
                        LibraryViewModel.Factory(application, repository, preferencesRepository)
                    }
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    val docId = activeDocumentId
                    if (docId == null) {
                        LibraryScreen(
                            viewModel = libraryViewModel,
                            onOpenDocument = { id -> activeDocumentId = id }
                        )
                        // Allow testing the Dynamic Island notification banner directly from Library → Ajustes
                        DynamicIslandNotificationBanner(
                            notification = incomingNotification?.takeIf { it.isTestPreview },
                            onTapNotification = { notif ->
                                SystemMediaController.openIncomingNotification(this@MainActivity, notif)
                            },
                            onDismiss = { SystemMediaController.dismissIncomingNotification() },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .displayCutoutPadding()
                                .statusBarsPadding()
                                .padding(top = 6.dp, start = 12.dp, end = 12.dp)
                        )
                    } else {
                        val readerViewModel: ReaderViewModel = viewModel(
                            key = "reader_$docId",
                            factory = remember(docId) {
                                ReaderViewModel.Factory(application, docId, repository, preferencesRepository)
                            }
                        )
                        ReaderScreen(
                            viewModel = readerViewModel,
                            onBackToLibrary = { activeDocumentId = null }
                        )
                    }

                    if (prefs.showFpsCounter) {
                        LiveFpsBadge(
                            targetFps = prefs.targetFps,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(top = 6.dp, end = 10.dp)
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        DisplayRefreshRateController.applyRefreshRate(this, currentTargetFps)
        SystemMediaController.refreshPermissionStatus(applicationContext)
    }
}

@Composable
private fun LiveFpsBadge(
    targetFps: TargetFpsOption,
    modifier: Modifier = Modifier
) {
    val palette = LocalFolioPalette.current
    var measuredFps by remember { mutableIntStateOf(60) }

    DisposableEffect(Unit) {
        val choreographer = Choreographer.getInstance()
        var frameCount = 0
        var windowStartNs = 0L
        var active = true

        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!active) return
                if (windowStartNs == 0L) {
                    windowStartNs = frameTimeNanos
                }
                frameCount++
                val elapsedNs = frameTimeNanos - windowStartNs
                if (elapsedNs >= 500_000_000L) {
                    val fps = (frameCount * 1_000_000_000.0 / elapsedNs).roundToInt()
                    measuredFps = fps.coerceIn(1, 240)
                    frameCount = 0
                    windowStartNs = frameTimeNanos
                }
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)

        onDispose {
            active = false
            choreographer.removeFrameCallback(callback)
        }
    }

    val targetLabel = if (targetFps == TargetFpsOption.AUTO) "MÁX" else "${targetFps.label}"
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(palette.surface.copy(alpha = 0.88f))
            .border(1.dp, palette.accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .testTag("live_fps_badge")
    ) {
        Text(
            text = "$measuredFps FPS · $targetLabel",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = palette.accent
        )
    }
}
