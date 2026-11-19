package com.example.engine

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
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
        SystemMediaController.onSystemNotificationPosted(applicationContext, sbn)
    }
}

/**
 * Real-time Android MediaSession & Dynamic Island Notification controller with graceful
 * AudioManager media-key fallback.
 */
object SystemMediaController {

    private val _mediaState = MutableStateFlow(MediaPlaybackInfo())
    val mediaState: StateFlow<MediaPlaybackInfo> = _mediaState.asStateFlow()

    private val _incomingNotification = MutableStateFlow<IncomingAppNotification?>(null)
    val incomingNotification: StateFlow<IncomingAppNotification?> = _incomingNotification.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var sessionManager: MediaSessionManager? = null
    private var activeController: MediaController? = null
    private var sessionsListenerRegistered = false
    private var lastFallbackAudioActiveMs: Long = 0L

    private var clearNotifRunnable: Runnable? = null

    fun dismissIncomingNotification() {
        clearNotifRunnable?.let { mainHandler.removeCallbacks(it) }
        clearNotifRunnable = null
        _incomingNotification.value = null
    }

    fun openIncomingNotification(context: Context, notif: IncomingAppNotification) {
        dismissIncomingNotification()
        val opened = runCatching {
            notif.contentIntent?.send()
            notif.contentIntent != null
        }.getOrDefault(false)
        if (!opened && notif.packageName.isNotBlank()) {
            runCatching {
                val launch = context.packageManager.getLaunchIntentForPackage(notif.packageName)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                }
            }
        }
    }

    fun triggerTestNotification(context: Context, delayMs: Long = 220L) {
        val ctx = context.applicationContext
        val iconBitmap = extractNotificationIconBitmap(ctx, ctx.packageName)
        val item = IncomingAppNotification(
            key = "luma_test_${System.nanoTime()}",
            packageName = "",
            appName = "Mensajes · Prueba",
            title = "Notificación en Isla Dinámica",
            text = "Así aparecen los avisos de tus apps mientras lees sin barra de estado.",
            appIcon = iconBitmap,
            contentIntent = null,
            postedAtMs = System.currentTimeMillis(),
            isTestPreview = true
        )
        if (delayMs > 0L) {
            mainHandler.postDelayed({ publishIncomingNotification(item) }, delayMs)
        } else {
            publishIncomingNotification(item)
        }
    }

    private fun publishIncomingNotification(item: IncomingAppNotification) {
        mainHandler.post {
            _incomingNotification.value = item
            clearNotifRunnable?.let { mainHandler.removeCallbacks(it) }
            val clearTask = Runnable {
                if (_incomingNotification.value?.id == item.id) {
                    _incomingNotification.value = null
                }
            }
            clearNotifRunnable = clearTask
            mainHandler.postDelayed(clearTask, 4800L)
        }
    }

    internal fun onSystemNotificationPosted(context: Context, sbn: StatusBarNotification) {
        val pkg = sbn.packageName.orEmpty()
        if (pkg.isBlank() || pkg == context.packageName) return

        val notif = sbn.notification ?: return
        // Skip persistent background/ongoing/media/progress notifications & empty summary shells
        val flags = notif.flags
        if ((flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0 ||
            (flags and Notification.FLAG_GROUP_SUMMARY) != 0
        ) {
            return
        }
        val category = notif.category
        if (category == Notification.CATEGORY_TRANSPORT ||
            category == Notification.CATEGORY_SERVICE ||
            category == Notification.CATEGORY_PROGRESS
        ) {
            return
        }

        val extras = notif.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim().orEmpty()
        val shortText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        val body = shortText.ifBlank { bigText }
        if (title.isBlank() && body.isBlank()) return

        val appName = resolveAppLabel(context, pkg)
        val iconBitmap = extractNotificationIconBitmap(context, pkg)

        val item = IncomingAppNotification(
            key = sbn.key,
            packageName = pkg,
            appName = appName.ifBlank { "Notificación" },
            title = title.ifBlank { appName },
            text = body,
            appIcon = iconBitmap,
            contentIntent = notif.contentIntent,
            postedAtMs = sbn.postTime
        )
        publishIncomingNotification(item)
    }

    private fun extractNotificationIconBitmap(context: Context, packageName: String): Bitmap? {
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawableToSmallBitmap(drawable, 72)
        }.getOrNull()
    }

    private fun drawableToSmallBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, sizePx, sizePx, true)
        }
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bmp
    }

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
