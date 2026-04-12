package it.pytonballoon810.glyphha

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

class GlyphSyncForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var foregroundStarted = false
    private var allowAutoRestart = true

    private lateinit var store: SensorMappingStore
    private lateinit var glyphController: GlyphController

    private var pollingJob: Job? = null
    private val progressStates = mutableMapOf<String, ProgressState>()
    private val genericStates = mutableMapOf<String, GenericState>()

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
        ensureForegroundStarted(getString(R.string.notification_waiting_config))

        when (intent?.action) {
            ACTION_STOP -> {
                allowAutoRestart = false
                GlyphServiceManager.cancelScheduledRestart(this)
                stopSyncInternal("Stopped")
                stopForeground(STOP_FOREGROUND_REMOVE)
                foregroundStarted = false
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_MAPPINGS_UPDATED -> {
                allowAutoRestart = true
                handleMappingsUpdated()
            }
            ACTION_START, null -> {
                allowAutoRestart = true
                ensurePolling()
            }
        }
        GlyphServiceManager.cancelScheduledRestart(this)
        return START_STICKY
    }

    override fun onDestroy() {
        val shouldRestart = allowAutoRestart && GlyphServiceManager.shouldAutoRun(this)
        stopSyncInternal("Stopped")
        unregisterReceiver(screenOnReceiver)
        serviceScope.coroutineContext.cancel()
        if (shouldRestart) {
            GlyphServiceManager.scheduleRestart(this)
        } else {
            GlyphServiceManager.cancelScheduledRestart(this)
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (allowAutoRestart && GlyphServiceManager.shouldAutoRun(this)) {
            GlyphServiceManager.scheduleRestart(this)
        }
        super.onTaskRemoved(rootIntent)
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
                syncStateMaps(mappings)
                val client = HomeAssistantClient(baseUrl, token)
                val completionIconType = store.loadCompletionIconType()
                val errorIconType = store.loadErrorIconType()
                val customIconData = store.loadCustomIconData()

                for (mapping in mappings) {
                    if (!isActive) break

                    var nextDelayMs = POLL_INTERVAL_MS

                    try {
                        nextDelayMs = when (mapping.useCase) {
                            UseCaseType.TRACK_3D_PRINTER_PROGRESS -> handlePrinterUseCase(
                                client = client,
                                mapping = mapping,
                                completionIconType = completionIconType,
                                customIconData = customIconData
                            )

                            UseCaseType.TRACK_GENERIC_SENSOR -> handleGenericUseCase(
                                client = client,
                                mapping = mapping,
                                errorIconType = errorIconType,
                                customIconData = customIconData
                            )
                        }

                        updateNotification(getString(R.string.notification_syncing, mapping.progressEntityId))
                    } catch (e: Exception) {
                        updateNotification(getString(R.string.notification_sync_error, e.message ?: "unknown"))
                    }

                    delay(nextDelayMs)
                }
            }
        }
    }

    private suspend fun handlePrinterUseCase(
        client: HomeAssistantClient,
        mapping: SensorMapping,
        completionIconType: CompletionIconType,
        customIconData: CustomIconData?
    ): Long {
        val progressState = withContext(Dispatchers.IO) {
            client.fetchState(mapping.progressEntityId)
        }

        val remainingText = mapping.remainingTimeEntityId?.let { remainingEntity ->
            withContext(Dispatchers.IO) {
                client.fetchState(remainingEntity)
            }.let { formatSecondaryText(it) }
        }

        val interrupted = mapping.interruptedEntityId?.let { interruptedEntity ->
            withContext(Dispatchers.IO) {
                client.fetchState(interruptedEntity)
            }.let { isInterruptedState(it) }
        } ?: false

        return if (interrupted) {
            handleInterruptedMapping(mapping)
        } else {
            handleProgressMapping(
                mapping = mapping,
                progressState = progressState,
                secondaryText = remainingText,
                completionIconType = completionIconType,
                customIconData = customIconData
            )
        }
    }

    private suspend fun handleGenericUseCase(
        client: HomeAssistantClient,
        mapping: SensorMapping,
        errorIconType: CompletionIconType,
        customIconData: CustomIconData?
    ): Long {
        val sensorState = withContext(Dispatchers.IO) {
            client.fetchState(mapping.progressEntityId)
        }

        val shouldShowError = mapping.genericErrorEntityId?.let { errorEntityId ->
            val errorState = withContext(Dispatchers.IO) {
                client.fetchState(errorEntityId)
            }
            matchesConfiguredValue(
                rawState = errorState.rawState.trim(),
                numericState = errorState.value,
                configuredValue = mapping.genericErrorTriggerValue
            )
        } ?: false

        if (shouldShowError) {
            glyphController.renderCompletionBlink(
                showIcon = true,
                iconType = errorIconType,
                customIconData = customIconData
            )
            return POLL_INTERVAL_MS
        }

        val runtime = genericStates.getOrPut(mapping.progressEntityId) { GenericState(enabled = true) }
        val rawState = sensorState.rawState.trim()

        if (matchesConfiguredValue(rawState, sensorState.value, mapping.resetValue)) {
            runtime.enabled = true
        }

        if (matchesConfiguredValue(rawState, sensorState.value, mapping.turnOffValue)) {
            runtime.enabled = false
            glyphController.clearAppDisplay()
            return POLL_INTERVAL_MS
        }

        if (!runtime.enabled) {
            glyphController.clearAppDisplay()
            return POLL_INTERVAL_MS
        }

        when (mapping.genericDisplayMode) {
            GenericDisplayMode.PROGRESS -> {
                val value = sensorState.value ?: return POLL_INTERVAL_MS
                val max = mapping.maxValue.coerceAtLeast(1.0)
                glyphController.renderProgressRatio((value / max).coerceIn(0.0, 1.0))
            }

            GenericDisplayMode.NUMBER -> {
                glyphController.renderRawText(sensorState.rawState)
            }
        }

        return POLL_INTERVAL_MS
    }

    private fun matchesConfiguredValue(rawState: String, numericState: Double?, configuredValue: String?): Boolean {
        val configured = configuredValue?.trim().orEmpty()
        if (configured.isBlank()) return false

        if (rawState.equals(configured, ignoreCase = true)) return true

        val configuredNumeric = parseComparableNumeric(configured)
        val currentNumeric = numericState ?: parseComparableNumeric(rawState)

        return configuredNumeric != null && currentNumeric != null && abs(configuredNumeric - currentNumeric) < NUMERIC_MATCH_EPSILON
    }

    private fun parseComparableNumeric(value: String): Double? {
        return value
            .replace("%", "")
            .replace(",", ".")
            .replace(Regex("[^0-9+\\-\\.]"), "")
            .toDoubleOrNull()
    }

    private fun stopSyncInternal(status: String) {
        pollingJob?.cancel()
        pollingJob = null
        updateNotification(status)
        glyphController.stop()
    }

    private fun handleMappingsUpdated() {
        val mappings = store.loadMappings()
        glyphController.start()
        syncStateMaps(mappings)
        glyphController.clearAppDisplay()

        if (mappings.isEmpty()) {
            updateNotification(getString(R.string.notification_waiting_sensor))
        }

        pollingJob?.cancel()
        pollingJob = null
        ensurePolling()
    }

    private fun handleInterruptedMapping(mapping: SensorMapping): Long {
        val progressState = progressStates.getOrPut(mapping.progressEntityId) { ProgressState(trackingEnabled = true) }
        progressState.completedWaitingForScreenOn = false
        progressState.trackingEnabled = true
        progressState.blinkOn = false
        progressState.scrollOffsetPx = 0
        progressState.lastSecondaryText = ""

        glyphController.renderInterruptedX()
        return INTERRUPTED_REFRESH_MS
    }

    private fun handleProgressMapping(
        mapping: SensorMapping,
        progressState: SensorState,
        secondaryText: String?,
        completionIconType: CompletionIconType,
        customIconData: CustomIconData?
    ): Long {
        val value = progressState.value ?: return POLL_INTERVAL_MS
        val max = mapping.maxValue.coerceAtLeast(1.0)
        val ratio = (value / max).coerceIn(0.0, 1.0)

        val runtimeState = progressStates.getOrPut(mapping.progressEntityId) { ProgressState(trackingEnabled = true) }
        val normalizedSecondaryText = secondaryText?.trim().orEmpty()

        if (runtimeState.lastSecondaryText != normalizedSecondaryText) {
            runtimeState.lastSecondaryText = normalizedSecondaryText
            runtimeState.scrollOffsetPx = 0
        }

        if (ratio <= 0.005) {
            runtimeState.trackingEnabled = true
            runtimeState.completedWaitingForScreenOn = false
            runtimeState.blinkOn = true
        }

        if (!runtimeState.trackingEnabled) {
            glyphController.clearAppDisplay()
            return POLL_INTERVAL_MS
        }

        if (runtimeState.completedWaitingForScreenOn) {
            runtimeState.blinkOn = !runtimeState.blinkOn
            glyphController.renderCompletionBlink(runtimeState.blinkOn, completionIconType, customIconData)
            return BLINK_INTERVAL_MS
        }

        if (ratio >= COMPLETION_THRESHOLD) {
            runtimeState.completedWaitingForScreenOn = true
            runtimeState.blinkOn = true
            glyphController.renderCompletionBlink(true, completionIconType, customIconData)
            return BLINK_INTERVAL_MS
        }

        val hasOverflow = glyphController.renderProgressRatio(
            ratio = ratio,
            subText = normalizedSecondaryText.ifBlank { null },
            scrollOffsetPx = runtimeState.scrollOffsetPx
        )

        if (hasOverflow) {
            runtimeState.scrollOffsetPx += 1
            return TEXT_SCROLL_INTERVAL_MS
        }

        runtimeState.scrollOffsetPx = 0
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

    private fun formatSecondaryText(state: SensorState): String {
        val raw = state.rawState.trim()

        val hmMatch = Regex("""(?i)^\s*(\d+)h\s+(\d+)m\s*$""").matchEntire(raw)
        if (hmMatch != null) {
            val h = hmMatch.groupValues[1].toIntOrNull() ?: 0
            val m = hmMatch.groupValues[2].toIntOrNull() ?: 0
            return when {
                h > 0 && m > 0 -> "${h}h ${m}m"
                h > 0 -> "${h}h"
                else -> "${m}m"
            }
        }

        val unit = state.unit?.trim()?.lowercase().orEmpty()
        val value = state.value
        if (value != null) {
            when (unit) {
                "h", "hr", "hrs", "hour", "hours" -> {
                    val totalMinutes = (value * 60.0).roundToInt().coerceAtLeast(0)
                    val hours = totalMinutes / 60
                    val minutes = totalMinutes % 60
                    return when {
                        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
                        hours > 0 -> "${hours}h"
                        else -> "${minutes}m"
                    }
                }

                "m", "min", "mins", "minute", "minutes" -> {
                    val minutes = value.roundToInt().coerceAtLeast(0)
                    return "${minutes}m"
                }
            }
        }

        return raw
    }

    private fun isInterruptedState(state: SensorState): Boolean {
        val raw = state.rawState.trim().lowercase()
        if (raw.isBlank() || raw == "unknown" || raw == "unavailable") return false

        return raw == "on" ||
            raw == "true" ||
            raw == "open" ||
            raw == "problem" ||
            raw == "error" ||
            raw == "interrupted" ||
            raw == "1"
    }

    private fun syncStateMaps(mappings: List<SensorMapping>) {
        val validPrinterIds = mappings
            .filter { it.useCase == UseCaseType.TRACK_3D_PRINTER_PROGRESS }
            .map { it.progressEntityId }
            .toSet()

        val validGenericIds = mappings
            .filter { it.useCase == UseCaseType.TRACK_GENERIC_SENSOR }
            .map { it.progressEntityId }
            .toSet()

        progressStates.keys.retainAll(validPrinterIds)
        validPrinterIds.forEach { progressEntityId ->
            progressStates.putIfAbsent(progressEntityId, ProgressState(trackingEnabled = true))
        }

        genericStates.keys.retainAll(validGenericIds)
        validGenericIds.forEach { sensorId ->
            genericStates.putIfAbsent(sensorId, GenericState(enabled = true))
        }
    }

    private fun updateNotification(message: String) {
        if (!hasNotificationPermission()) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
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

    private fun ensureForegroundStarted(content: String) {
        if (foregroundStarted) return
        startForeground(NOTIFICATION_ID, buildNotification(content))
        foregroundStarted = true
    }

    private data class ProgressState(
        var trackingEnabled: Boolean = false,
        var completedWaitingForScreenOn: Boolean = false,
        var blinkOn: Boolean = false,
        var scrollOffsetPx: Int = 0,
        var lastSecondaryText: String = ""
    )

    private data class GenericState(
        var enabled: Boolean = true
    )

    companion object {
        const val ACTION_START = "it.pytonballoon810.glyphha.action.START"
        const val ACTION_STOP = "it.pytonballoon810.glyphha.action.STOP"
        const val ACTION_MAPPINGS_UPDATED = "it.pytonballoon810.glyphha.action.MAPPINGS_UPDATED"

        private const val CHANNEL_ID = "glyph_sync_channel"
        private const val NOTIFICATION_ID = 4242
        private const val POLL_INTERVAL_MS = 5000L
        private const val BLINK_INTERVAL_MS = 600L
        private const val TEXT_SCROLL_INTERVAL_MS = 800L
        private const val INTERRUPTED_REFRESH_MS = 1000L
        private const val COMPLETION_THRESHOLD = 0.999
        private const val NUMERIC_MATCH_EPSILON = 0.000001
    }
}
