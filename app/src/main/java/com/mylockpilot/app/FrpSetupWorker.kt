package com.mylockpilot.app

import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker.Result
import androidx.work.Worker
import androidx.work.WorkerParameters

/**
 * Fetches this device's shop's Factory Reset Protection recovery email
 * (configured on the dashboard's Settings page — see
 * lockpilot-backend/migrations/015_per_shop_frp.sql) and applies it via
 * DevicePolicyHelper.applyFrpPolicy, then reports success back so the
 * dashboard can show real confirmation (migrations/016).
 *
 * IMPORTANT: this alone is not reliable enough. Real-device testing on
 * MIUI/Android Go found the OS can silently kill a plain background
 * WorkManager job before it ever runs, leaving a phone that looks fully
 * set up with zero actual FRP protection and no indication anything was
 * wrong. See attemptFrpSetupNow — call sites use BOTH an immediate
 * synchronous attempt and this worker (enqueued as expedited, which asks
 * the OS for near-immediate, harder-to-defer execution) as a backup in
 * case the immediate attempt runs before the network is ready.
 */
class FrpSetupWorker(appContext: Context, params: WorkerParameters) :
    Worker(appContext, params) {

    override fun doWork(): Result = attemptFrpSetupNow(applicationContext)

    companion object {
        const val WORK_NAME = "frp_setup"
        private const val TAG = "FrpSetupWorker"

        /**
         * Safe to call redundantly from multiple places and multiple
         * threads — does nothing if not yet paired or not yet Device
         * Owner, and returns Result.retry() if the shop hasn't configured
         * a recovery email yet or the network call fails, since either can
         * resolve later.
         */
        fun attemptFrpSetupNow(context: Context): Result {
            val policyHelper = DevicePolicyHelper(context)
            if (!policyHelper.isDeviceOwner) return Result.retry()

            val pairing = PairingStore(context)
            val deviceId = pairing.deviceId
            val deviceSecret = pairing.deviceSecret
            if (deviceId == null || deviceSecret == null) return Result.retry()

            return try {
                // Policy already active locally (a previous run applied it,
                // maybe before a crash/kill prevented reporting that back) —
                // just confirm to the backend rather than redundantly
                // re-fetching and re-applying.
                if (policyHelper.isFrpPolicyApplied()) {
                    SupabaseSync.reportFrpApplied(deviceId, deviceSecret)
                    return Result.success()
                }

                val email = SupabaseSync.fetchFrpRecoveryEmail(deviceId, deviceSecret)
                if (email == null) {
                    Log.i(TAG, "Shop hasn't configured a recovery email yet — will retry later")
                    return Result.retry()
                }
                policyHelper.applyFrpPolicy(email)
                SupabaseSync.reportFrpApplied(deviceId, deviceSecret)
                Log.i(TAG, "FRP policy applied and confirmed to backend")
                Result.success()
            } catch (e: SupabaseSync.SyncException) {
                Log.w(TAG, "Could not fetch/report FRP status, will retry", e)
                Result.retry()
            }
        }
    }
}
