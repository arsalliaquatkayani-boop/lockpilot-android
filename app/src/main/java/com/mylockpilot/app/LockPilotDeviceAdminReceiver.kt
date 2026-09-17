package com.mylockpilot.app

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Android binds to this receiver both during development (after
 * `adb shell dpm set-device-owner ...`) and in production (after the setup
 * wizard finishes QR-code provisioning). onProfileProvisioningComplete is
 * the production entry point — onEnabled fires in both paths.
 */
class LockPilotDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device admin enabled")
        DevicePolicyHelper(context).applyBaselinePolicies()
        scheduleFrpSetup(context)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.i(TAG, "Provisioning complete — device owner set up")
        DevicePolicyHelper(context).applyBaselinePolicies()

        // The dashboard's per-device QR embeds this device's ID + pairing
        // code in the provisioning payload's admin extras bundle, so a scan
        // pairs the phone immediately — no manual typing needed at the
        // counter. A QR without these extras (or the adb dev-setup path)
        // falls back to MainActivity's manual pairing form.
        val extras = intent.getBundleExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
        val deviceId = extras?.getString("device_id")
        val pairingCode = extras?.getString("pairing_code")

        if (!deviceId.isNullOrBlank() && !pairingCode.isNullOrBlank()) {
            Log.i(TAG, "Auto-pairing from QR provisioning extras")
            val pairing = PairingStore(context)
            pairing.save(deviceId, pairingCode)

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                LockSyncWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<LockSyncWorker>(15, TimeUnit.MINUTES).build(),
            )
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<LockSyncWorker>().build())
            FcmTokenManager.registerCurrentToken(context)

            val launchIntent = Intent(context, CustomerHomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }

        // Belt-and-suspenders with onEnabled's call below: onEnabled may
        // have fired before pairing was ready in this QR flow, so its
        // attempt would have deferred. Trying again right after pairing
        // here catches that.
        scheduleFrpSetup(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device admin disabled — this should not normally happen while payments are outstanding")
    }

    companion object {
        private const val TAG = "LockPilotDeviceAdmin"

        /**
         * Three layers, from fastest to most durable, because real-device
         * testing on MIUI/Android Go found a plain background WorkManager
         * job alone can be silently killed before it ever runs:
         *
         * 1. An immediate attempt on a plain background thread, right now
         *    — succeeds if the network happens to be ready already.
         * 2. An expedited WorkManager job — asks the OS for near-immediate,
         *    harder-to-defer execution as a fast backup.
         * 3. A 15-minute periodic job as a long-term safety net, in case
         *    both faster attempts fail (e.g. no network at all yet). It
         *    no-ops quickly via isFrpPolicyApplied() once any layer
         *    succeeds, so it's cheap to leave running indefinitely.
         */
        fun scheduleFrpSetup(context: Context) {
            Thread {
                try {
                    FrpSetupWorker.attemptFrpSetupNow(context)
                } catch (e: Exception) {
                    Log.w(TAG, "Immediate FRP setup attempt failed, other layers will retry", e)
                }
            }.start()

            WorkManager.getInstance(context).enqueueUniqueWork(
                FrpSetupWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<FrpSetupWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build(),
            )

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "${FrpSetupWorker.WORK_NAME}_periodic",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<FrpSetupWorker>(15, TimeUnit.MINUTES).build(),
            )
        }
    }
}
