package it.pytonballoon810.glyphha

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GlyphSyncForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var store: SensorMappingStore
    private lateinit var glyphController: GlyphController

    private var pollingJob: Job? = null
    private val progressStates = mutableMapOf<String, ProgressState>()

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_ON) {
                onScreenTurnedOn()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = SensorMappingStore(this)
        glyphController = GlyphController(this)
        registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSyncInternal("Stopped")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.notification_waiting_config)))
                ensurePolling()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopSyncInternal("Stopped")
        unregisterReceiver(screenOnReceiver)
        serviceScope.coroutineContext.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensurePolling() {
        if (pollingJob?.isActive == true) return

        pollingJob = serviceScope.launch {
            while (isActive) {
                val baseUrl = store.loadBaseUrl()
                val token = store.loadToken()
                val mappings = store.loadMappings()

                if (baseUrl.isBlank() || token.isBlank()) {
                    updateNotification(getString(R.string.notification_waiting_config))
                    glyphController.clearAppDisplay()
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                if (mappings.isEmpty()) {
                    updateNotification(getString(R.string.notification_waiting_sensor))
                    glyphController.clearAppDisplay()
                    delay(POLL_INTERVAL_MS)
                    continue
                }

                glyphController.start()
                syncProgressStateMap(mappings)
                val client = HomeAssistantClient(baseUrl, token)
                val completionIconType = store.loadCompletionIconType()
                val customIconData = store.loadCustomIconData()

                for (mapping in mappings) {
                    if (!isActive) break

                    var nextDelayMs = POLL_INTERVAL_MS

                    try {
                        val state = withContext(Dispatchers.IO) {
                            client.fetchState(mapping.entityId)
                        }

                        val secondaryText = mapping.secondaryTextEntityId?.let { secondaryEntity ->
                            withContext(Dispatchers.IO) {
                                client.fetchState(secondaryEntity)
                            }.rawState
                        }

                        when (mapping.mode) {
                            DisplayMode.RAW_NUMBER -> glyphController.render(mapping, state)
                            DisplayMode.PROGRESS -> {
                                nextDelayMs = handleProgressMapping(
                                    mapping = mapping,
                                    state = state,
                                    secondaryText = secondaryText,
                                    completionIconType = completionIconType,
                                    customIconData = customIconData
                                )
                            }
                        }

                        updateNotification(getString(R.string.notification_syncing, mapping.entityId))
                    } catch (e: Exception) {
                        updateNotification(getString(R.string.notification_sync_error, e.message ?: "unknown"))
                    }

                    delay(nextDelayMs)
                }
            }
        }
    }

    private fun stopSyncInternal(status: String) {
        pollingJob?.cancel()
        pollingJob = null
        updateNotification(status)
        glyphController.stop()
    }

    private fun handleProgressMapping(
        mapping: SensorMapping,
        state: SensorState,
        secondaryText: String?,
        completionIconType: CompletionIconType,
        customIconData: CustomIconData?
    ): Long {
        val value = state.value ?: return POLL_INTERVAL_MS
        val max = mapping.maxValue.coerceAtLeast(1.0)
        val ratio = (value / max).coerceIn(0.0, 1.0)

        val progressState = progressStates.getOrPut(mapping.entityId) { ProgressState(trackingEnabled = true) }
        val normalizedSecondaryText = secondaryText?.trim().orEmpty()

        if (progressState.lastSecondaryText != normalizedSecondaryText) {
            progressState.lastSecondaryText = normalizedSecondaryText
            progressState.scrollOffsetPx = 0
        }

        if (ratio <= 0.005) {
            progressState.trackingEnabled = true
            progressState.completedWaitingForScreenOn = false
            progressState.blinkOn = true
        }

        if (!progressState.trackingEnabled) {
            glyphController.clearAppDisplay()
            return POLL_INTERVAL_MS
        }

        if (progressState.completedWaitingForScreenOn) {
            progressState.blinkOn = !progressState.blinkOn
            glyphController.renderCompletionBlink(progressState.blinkOn, completionIconType, customIconData)
            return BLINK_INTERVAL_MS
        }

        if (ratio >= COMPLETION_THRESHOLD) {
            progressState.completedWaitingForScreenOn = true
            progressState.blinkOn = true
            glyphController.renderCompletionBlink(true, completionIconType, customIconData)
            return BLINK_INTERVAL_MS
        }

        val hasOverflow = glyphController.renderProgressRatio(
            ratio = ratio,
            subText = normalizedSecondaryText.ifBlank { null },
            scrollOffsetPx = progressState.scrollOffsetPx
        )

        if (hasOverflow) {
            progressState.scrollOffsetPx += 1
            return TEXT_SCROLL_INTERVAL_MS
        }

        progressState.scrollOffsetPx = 0
        return POLL_INTERVAL_MS
    }

    private fun onScreenTurnedOn() {
        var disabledAny = false
        progressStates.values.forEach { state ->
            if (state.completedWaitingForScreenOn) {
                state.completedWaitingForScreenOn = false
                state.trackingEnabled = false
                state.blinkOn = false
                disabledAny = true
            }
        }

        if (disabledAny) {
            glyphController.clearAppDisplay()
        }
    }

    private fun syncProgressStateMap(mappings: List<SensorMapping>) {
        val validProgressIds = mappings
            .filter { it.mode == DisplayMode.PROGRESS }
            .map { it.entityId }
            .toSet()

        progressStates.keys.retainAll(validProgressIds)
        validProgressIds.forEach { entityId ->
            progressStates.putIfAbsent(entityId, ProgressState(trackingEnabled = true))
        }
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private data class ProgressState(
        var trackingEnabled: Boolean = false,
        var completedWaitingForScreenOn: Boolean = false,
        var blinkOn: Boolean = false,
        var scrollOffsetPx: Int = 0,
        var lastSecondaryText: String = ""
    )

    companion object {
        const val ACTION_START = "it.pytonballoon810.glyphha.action.START"
        const val ACTION_STOP = "it.pytonballoon810.glyphha.action.STOP"

        private const val CHANNEL_ID = "glyph_sync_channel"
        private const val NOTIFICATION_ID = 4242
        private const val POLL_INTERVAL_MS = 5000L
        private const val BLINK_INTERVAL_MS = 600L
        private const val TEXT_SCROLL_INTERVAL_MS = 800L
        private const val COMPLETION_THRESHOLD = 0.999
    }
}
