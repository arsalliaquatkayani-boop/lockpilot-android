package com.mylockpilot.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

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
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.i(TAG, "Provisioning complete — device owner set up")
        DevicePolicyHelper(context).applyBaselinePolicies()
        // TODO once a backend exists: launch a first-run flow here that
        // pairs this device with the customer/installment record the shop
        // created at the counter (likely via a token embedded in the QR
        // provisioning payload's PROVISIONING_ADMIN_EXTRAS_BUNDLE).
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.w(TAG, "Device admin disabled — this should not normally happen while payments are outstanding")
    }

    companion object {
        private const val TAG = "LockPilotDeviceAdmin"
    }
}
