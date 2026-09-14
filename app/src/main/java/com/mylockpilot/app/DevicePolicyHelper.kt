package com.mylockpilot.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Thin wrapper around the Device Owner APIs LockPilot actually uses.
 *
 * Deliberately narrow: only lock-task pinning, uninstall-blocking, and
 * immediate lock/unlock. No password policy, no wipe, no camera/keyguard
 * restrictions — matching the minimal policy set declared in
 * res/xml/device_admin_receiver.xml and the "no access to personal data"
 * trust promise made on the marketing site.
 *
 * UNVERIFIED: written without a device/emulator to test against. Before
 * relying on this, confirm on a real Android Studio emulator that:
 *  - isDeviceOwnerApp returns true after `adb shell dpm set-device-owner`
 *  - lockDevice() actually pins LockScreenActivity so Home/Recents can't
 *    escape it
 *  - unlockDevice() correctly releases lock task mode
 */
class DevicePolicyHelper(context: Context) {

    private val appContext = context.applicationContext
    private val dpm =
        appContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent =
        ComponentName(appContext, LockPilotDeviceAdminReceiver::class.java)

    val isDeviceOwner: Boolean
        get() = dpm.isDeviceOwnerApp(appContext.packageName)

    /** Call once, right after provisioning completes. */
    fun applyBaselinePolicies() {
        if (!isDeviceOwner) return

        // Customer can't uninstall LockPilot while payments are outstanding.
        dpm.setUninstallBlocked(adminComponent, appContext.packageName, true)

        // Only LockScreenActivity's package may be pinned in lock task mode —
        // this is what makes the overdue screen unremovable via Home/Recents.
        dpm.setLockTaskPackages(adminComponent, arrayOf(appContext.packageName))
    }

    /** Call once the device is fully paid off. */
    fun liftRestrictions() {
        if (!isDeviceOwner) return
        dpm.setUninstallBlocked(adminComponent, appContext.packageName, false)
        dpm.setLockTaskPackages(adminComponent, arrayOf())
    }

    /**
     * Launches the unremovable overdue screen. Actual lock-task pinning
     * happens inside LockScreenActivity.onCreate via startLockTask() —
     * this just gets that activity on screen, including over the keyguard.
     */
    fun lockDevice(context: Context) {
        val intent = Intent(context, LockScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        context.startActivity(intent)
    }

    /** Called from LockScreenActivity once a payment is confirmed. */
    fun unlockDevice(activity: android.app.Activity) {
        activity.stopLockTask()
        activity.finish()
    }

    companion object {
        fun deviceAdminComponent(context: Context) =
            ComponentName(context, LockPilotDeviceAdminReceiver::class.java)

        /** True only on API levels where lock task mode + Device Owner are reliable. */
        val isSupportedApiLevel = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }
}
