package it.pytonballoon810.glyphha

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (GlyphServiceManager.shouldAutoRun(context)) {
                    GlyphServiceManager.startSyncService(context)
                    GlyphServiceManager.scheduleRestart(context, 30_000L)
                }
            }
        }
    }
}
