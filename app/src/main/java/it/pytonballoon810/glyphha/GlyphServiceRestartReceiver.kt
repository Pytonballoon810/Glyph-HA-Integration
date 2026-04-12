package it.pytonballoon810.glyphha

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class GlyphServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action != GlyphServiceManager.ACTION_RESTART_SERVICE && action != Intent.ACTION_SCREEN_ON) return
        if (!GlyphServiceManager.shouldAutoRun(context)) return

        val started = GlyphServiceManager.startSyncService(context)
        if (!started && action != Intent.ACTION_SCREEN_ON) {
            GlyphServiceManager.scheduleRestart(context, 30_000L)
        }
    }
}
