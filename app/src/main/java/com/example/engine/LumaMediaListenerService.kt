package com.example.engine

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import com.example.data.model.MediaPlaybackInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class IncomingAppNotification(
    val id: Long = System.nanoTime(),
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val appIcon: Bitmap? = null,
    val contentIntent: PendingIntent? = null,
    val postedAtMs: Long = System.currentTimeMillis(),
    val isTestPreview: Boolean = false
)

/**
 * System NotificationListenerService that allows Luma to:
 * 1. Read active MediaSessions (Spotify, YouTube, YouTube Music, VLC, etc.) for artwork,
 *    title, progress, and playback controls inside the Dynamic Island.
 * 2. Route incoming app notifications (WhatsApp, Instagram, Gmail, SMS, etc.) cleanly through
 *    the Dynamic Island while the reader hides the cluttered system status bar.
 */
class LumaMediaListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        SystemMediaController.bindActiveSessions(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        SystemMediaController.refreshPermissionStatus(applicationContext)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        DynamicIslandEventManager.onSystemNotificationPosted(applicationContext, sbn)
    }
}

/**
 * Real-time Android MediaSession controller with graceful AudioManager media-key fallback.
 * External app notifications are handled centrally by [DynamicIslandEventManager].
 */
object SystemMediaController {

    private val _mediaState = MutableStateFlow(MediaPlaybackInfo())
    val mediaState: StateFlow<MediaPlaybackInfo> = _mediaState.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var sessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var sessionsListenerRegistered = false
    private var lastFallbackAudioActiveMs: Long = 0L

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publishFromController(activeController)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publishFromController(activeController)
        }

        override fun onSessionDestroyed() {
            appContext?.let { bindActiveSessions(it) }
        }
    }

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        selectBestController(controllers.orEmpty())
    }

    private val progressTicker = object : Runnable {
        override fun run() {
            val ctx = appContext
            val current = _mediaState.value
            if (current.isNotificationAccessGranted) {
                if (current.hasActiveSession && current.isPlaying && current.durationMs > 0L) {
                    val now = SystemClock.elapsedRealtime()
                    val elapsed = (now - current.lastUpdatedRealtimeMs).coerceAtLeast(0L)
                    val projected = (current.positionMs + elapsed).coerceAtMost(current.durationMs)
                    _mediaState.value = current.copy(
                        positionMs = projected,
                        lastUpdatedRealtimeMs = now
                    )
                }
            } else if (ctx != null) {
                pollAudioManagerFallback(ctx)
            }
            mainHandler.postDelayed(this, 1000L)
        }
    }
    private var tickerRunning = false

    private fun pollAudioManagerFallback(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val activeNow = runCatching { audioManager.isMusicActive }.getOrDefault(false)
        val now = SystemClock.elapsedRealtime()
        if (activeNow) {
            lastFallbackAudioActiveMs = now
        }
        val recentlyPaused = lastFallbackAudioActiveMs > 0L && (now - lastFallbackAudioActiveMs) <= 120_000L
        if (activeNow || recentlyPaused) {
            val prev = _mediaState.value
            val nextTitle = if (activeNow) "Reproduciendo audio" else "Audio en pausa"
            if (!prev.hasActiveSession || prev.isPlaying != activeNow || prev.title != nextTitle) {
                _mediaState.value = MediaPlaybackInfo(
                    isNotificationAccessGranted = false,
                    hasActiveSession = true,
                    isPlaying = activeNow,
                    title = nextTitle,
                    artist = "Multimedia del dispositivo",
                    appName = "Reproductor",
                    lastUpdatedRealtimeMs = now
                )
            }
        } else if (_mediaState.value.hasActiveSession) {
            _mediaState.value = MediaPlaybackInfo(
                isNotificationAccessGranted = false,
                hasActiveSession = false
            )
        }
    }

    fun start(context: Context) {
        appContext = context.applicationContext
        if (!tickerRunning) {
            tickerRunning = true
            mainHandler.postDelayed(progressTicker, 1000L)
        }
        bindActiveSessions(context.applicationContext)
    }

    fun refreshPermissionStatus(context: Context) {
        val granted = isNotificationListenerEnabled(context)
        if (granted) {
            bindActiveSessions(context.applicationContext)
        } else {
            detachController()
            pollAudioManagerFallback(context.applicationContext)
        }
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        return runCatching {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            context.packageName in enabledPackages
        }.getOrDefault(false)
    }

    fun openNotificationAccessSettings(context: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun bindActiveSessions(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        val granted = isNotificationListenerEnabled(ctx)
        if (!granted) {
            pollAudioManagerFallback(ctx)
            return
        }

        runCatching {
            val mgr = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            sessionManager = mgr
            val component = ComponentName(ctx, LumaMediaListenerService::class.java)
            if (!sessionsListenerRegistered) {
                runCatching {
                    mgr.addOnActiveSessionsChangedListener(activeSessionsListener, component, mainHandler)
                    sessionsListenerRegistered = true
                }
            }
            val controllers = mgr.getActiveSessions(component)
            selectBestController(controllers)
        }.onFailure {
            _mediaState.value = _mediaState.value.copy(isNotificationAccessGranted = granted)
        }
    }

    private fun selectBestController(controllers: List<MediaController>) {
        // Prioritize actively playing session, then paused/buffering session with metadata
        val best = controllers.firstOrNull { ctrl ->
            val st = ctrl.playbackState?.state
            st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING
        } ?: controllers.firstOrNull { ctrl ->
            ctrl.metadata != null
        } ?: controllers.firstOrNull()

        if (best?.sessionToken != activeController?.sessionToken) {
            detachController()
            activeController = best
            best?.registerCallback(controllerCallback, mainHandler)
        }
        publishFromController(activeController)
    }

    private fun detachController() {
        runCatching { activeController?.unregisterCallback(controllerCallback) }
        activeController = null
    }

    private fun publishFromController(controller: MediaController?) {
        val ctx = appContext
        val granted = ctx != null && isNotificationListenerEnabled(ctx)
        if (controller == null) {
            _mediaState.value = MediaPlaybackInfo(
                isNotificationAccessGranted = granted,
                hasActiveSession = false
            )
            return
        }

        val metadata = controller.metadata
        val playback = controller.playbackState
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
            ?: ""
        val art: Bitmap? = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val duration = (metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0L)
        val rawPos = (playback?.position ?: 0L).coerceAtLeast(0L)
        val isPlaying = playback?.state == PlaybackState.STATE_PLAYING ||
            playback?.state == PlaybackState.STATE_BUFFERING

        val pkg = controller.packageName.orEmpty()
        val friendlyApp = resolveAppLabel(ctx, pkg)

        val currentPos = if (isPlaying && playback != null && playback.lastPositionUpdateTime > 0L) {
            val delta = ((SystemClock.elapsedRealtime() - playback.lastPositionUpdateTime) * playback.playbackSpeed).toLong()
            (rawPos + delta).coerceIn(0L, if (duration > 0L) duration else Long.MAX_VALUE)
        } else {
            rawPos
        }

        _mediaState.value = MediaPlaybackInfo(
            isNotificationAccessGranted = granted,
            hasActiveSession = title.isNotBlank() || playback != null,
            isPlaying = isPlaying,
            title = title.ifBlank { friendlyApp.ifBlank { "Reproduciendo multimedia" } },
            artist = artist,
            appName = friendlyApp,
            packageName = pkg,
            albumArt = art,
            positionMs = currentPos,
            durationMs = duration,
            lastUpdatedRealtimeMs = SystemClock.elapsedRealtime()
        )
    }

    fun togglePlayPause(context: Context) {
        val ctrl = activeController
        if (ctrl != null) {
            val playing = ctrl.playbackState?.state == PlaybackState.STATE_PLAYING
            runCatching {
                if (playing) ctrl.transportControls.pause() else ctrl.transportControls.play()
            }
            return
        }
        dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun skipToNext(context: Context) {
        val ctrl = activeController
        if (ctrl != null) {
            runCatching { ctrl.transportControls.skipToNext() }
            return
        }
        dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun skipToPrevious(context: Context) {
        val ctrl = activeController
        if (ctrl != null) {
            runCatching { ctrl.transportControls.skipToPrevious() }
            return
        }
        dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    fun seekTo(positionMs: Long) {
        val ctrl = activeController ?: return
        runCatching {
            ctrl.transportControls.seekTo(positionMs.coerceAtLeast(0L))
            _mediaState.value = _mediaState.value.copy(
                positionMs = positionMs.coerceAtLeast(0L),
                lastUpdatedRealtimeMs = SystemClock.elapsedRealtime()
            )
        }
    }

    private fun dispatchMediaKey(context: Context, keyCode: Int) {
        runCatching {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val now = SystemClock.uptimeMillis()
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        }
    }

    private fun resolveAppLabel(context: Context?, packageName: String): String {
        if (packageName.isBlank()) return ""
        val lower = packageName.lowercase()
        when {
            "spotify" in lower -> return "Spotify"
            "youtube.music" in lower -> return "YouTube Music"
            "youtube" in lower -> return "YouTube"
            "apple.android.music" in lower -> return "Apple Music"
            "soundcloud" in lower -> return "SoundCloud"
            "deezer" in lower -> return "Deezer"
            "tidal" in lower -> return "TIDAL"
            "vlc" in lower -> return "VLC"
        }
        if (context == null) return packageName.substringAfterLast('.')
        return runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            pm.getApplicationLabel(info).toString()
        }.getOrElse {
            runCatching {
                @Suppress("DEPRECATION")
                val info = context.packageManager.getApplicationInfo(packageName, 0)
                context.packageManager.getApplicationLabel(info).toString()
            }.getOrDefault("Multimedia")
        }
    }
}
