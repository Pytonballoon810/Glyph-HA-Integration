package it.pytonballoon810.glyphha

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object GlyphServiceManager {
    const val ACTION_RESTART_SERVICE = "it.pytonballoon810.glyphha.action.RESTART_SERVICE"

    private const val RESTART_REQUEST_CODE = 420421

    fun shouldAutoRun(context: Context): Boolean {
        val store = SensorMappingStore(context)
        return store.loadBaseUrl().isNotBlank() &&
            store.loadToken().isNotBlank() &&
            store.loadMappings().isNotEmpty()
    }

    fun startSyncService(
        context: Context,
        action: String = GlyphSyncForegroundService.ACTION_START
    ): Boolean {
        val intent = Intent(context, GlyphSyncForegroundService::class.java).apply {
            this.action = action
        }
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun scheduleRestart(context: Context, delayMs: Long = 10_000L) {
        if (!shouldAutoRun(context)) {
            cancelScheduledRestart(context)
            return
        }

        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = restartPendingIntent(context)
        val triggerAt = System.currentTimeMillis() + delayMs
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    fun cancelScheduledRestart(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(restartPendingIntent(context))
    }

    private fun restartPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GlyphServiceRestartReceiver::class.java).apply {
            action = ACTION_RESTART_SERVICE
        }
        return PendingIntent.getBroadcast(
            context,
            RESTART_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
