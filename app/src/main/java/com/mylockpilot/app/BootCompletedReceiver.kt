package com.mylockpilot.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * If the device rebooted while it was supposed to be locked, re-show the
 * lock screen. Without a backend yet, "supposed to be locked" isn't
 * determinable — this currently does nothing but is wired up so the real
 * check (read cached lock state from local storage) is a one-line addition
 * once that storage exists.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // TODO: if (LocalLockState.isLocked) DevicePolicyHelper(context).lockDevice(context)
    }
}
