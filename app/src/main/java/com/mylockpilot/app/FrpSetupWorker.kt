package com.mylockpilot.app

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Fetches this device's shop's Factory Reset Protection recovery email
 * (configured on the dashboard's Settings page — see
 * lockpilot-backend/migrations/015_per_shop_frp.sql) and applies it via
 * DevicePolicyHelper.applyFrpPolicy. Runs separately from
 * applyBaselinePolicies because it needs a network round trip and this
 * device's pairing credentials, neither of which are available at the
 * exact moment Device Owner is granted in every provisioning path (see
 * call sites in LockPilotDeviceAdminReceiver and MainActivity).
 *
 * Safe to enqueue redundantly from multiple places — does nothing if not
 * yet paired or not yet Device Owner, and retries (with WorkManager's
 * default backoff) if the shop hasn't configured a recovery email yet or
 * the network call fails, since either can resolve later.
 */
class FrpSetupWorker(appContext: Context, params: WorkerParameters) :
    Worker(appContext, params) {

    override fun doWork(): Result {
        val policyHelper = DevicePolicyHelper(applicationContext)
        if (!policyHelper.isDeviceOwner) return Result.retry()

        val pairing = PairingStore(applicationContext)
        val deviceId = pairing.deviceId
        val deviceSecret = pairing.deviceSecret
        if (deviceId == null || deviceSecret == null) return Result.retry()

        return try {
            val email = SupabaseSync.fetchFrpRecoveryEmail(deviceId, deviceSecret)
            if (email == null) {
                Log.i(TAG, "Shop hasn't configured a recovery email yet — will retry later")
                return Result.retry()
            }
            policyHelper.applyFrpPolicy(email)
            Result.success()
        } catch (e: SupabaseSync.SyncException) {
            Log.w(TAG, "Could not fetch FRP recovery email, will retry", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "FrpSetupWorker"
        const val WORK_NAME = "frp_setup"
    }
}
