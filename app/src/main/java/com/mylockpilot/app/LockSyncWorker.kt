package com.mylockpilot.app

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Runs periodically in the background: asks the backend what
 * should_be_locked currently is for this paired device (via SupabaseSync)
 * and brings the phone's actual state in line with it. Once a device is
 * paired, this is the only thing that drives real locking/unlocking — the
 * "Test" buttons in MainActivity/LockScreenActivity are for local testing
 * without a network round trip.
 */
class LockSyncWorker(appContext: Context, params: WorkerParameters) :
    Worker(appContext, params) {

    override fun doWork(): Result {
        val pairing = PairingStore(applicationContext)
        val deviceId = pairing.deviceId
        val deviceSecret = pairing.deviceSecret
        if (deviceId == null || deviceSecret == null) return Result.success()

        return try {
            val shouldBeLocked = SupabaseSync.fetchShouldBeLocked(deviceId, deviceSecret)
            val lockState = LockStateStore(applicationContext)

            if (shouldBeLocked && !lockState.isLocked) {
                DevicePolicyHelper(applicationContext).lockDevice(applicationContext)
            } else if (!shouldBeLocked && lockState.isLocked) {
                // Unlocking has to happen on whichever LockScreenActivity
                // instance is actually pinned to the foreground (it must
                // call stopLockTask() on itself) — a broadcast lets it react
                // even though this worker has no activity reference.
                applicationContext.sendBroadcast(
                    Intent(ACTION_REMOTE_UNLOCK).setPackage(applicationContext.packageName),
                )
            }
            Result.success()
        } catch (e: Exception) {
            Log.w("LockSyncWorker", "Sync failed: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        const val ACTION_REMOTE_UNLOCK = "com.mylockpilot.app.REMOTE_UNLOCK"
        const val WORK_NAME = "lock_sync"
    }
}
