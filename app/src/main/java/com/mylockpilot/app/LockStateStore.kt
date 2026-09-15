package com.mylockpilot.app

import android.content.Context

/**
 * Local record of whether this device is currently supposed to be showing
 * the lock screen. DevicePolicyManager has no "is lock task currently
 * pinned" query, so this is the source of truth BootCompletedReceiver and
 * LockSyncWorker use to decide whether to (re)show LockScreenActivity —
 * kept in sync by DevicePolicyHelper.lockDevice()/unlockDevice().
 */
class LockStateStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("lockpilot_lock_state", Context.MODE_PRIVATE)

    var isLocked: Boolean
        get() = prefs.getBoolean(KEY_LOCKED, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCKED, value).apply()

    companion object {
        private const val KEY_LOCKED = "is_locked"
    }
}
