package com.yapelistener

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("YapeListener", "Boot completed — service will auto-start via NotificationListenerService")
            // NotificationListenerService re-binds automatically after boot
            // if permission was already granted. No manual start needed.
        }
    }
}
