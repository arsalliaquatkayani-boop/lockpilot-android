package com.mylockpilot.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Two jobs after a reboot: re-show the lock screen if this device was
 * locked before the restart (WorkManager's own periodic work generally
 * survives a reboot on its own, but re-enqueueing here with KEEP is a
 * harmless no-op if it's already scheduled, and a needed fix if it isn't).
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        if (PairingStore(context).isPaired) {
            val request = PeriodicWorkRequestBuilder<LockSyncWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                LockSyncWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        if (LockStateStore(context).isLocked) {
            DevicePolicyHelper(context).lockDevice(context)
        }
    }
}
