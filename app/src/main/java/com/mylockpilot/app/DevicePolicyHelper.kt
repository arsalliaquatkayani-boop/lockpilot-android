package com.mylockpilot.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager

/**
 * Thin wrapper around the Device Owner APIs LockPilot actually uses.
 *
 * Deliberately narrow: lock-task pinning, uninstall-blocking, blocking
 * factory reset, and immediate lock/unlock. No password policy, no wipe, no
 * camera/keyguard restrictions, no hiding this app from the device's own
 * user — matching the minimal policy set declared in
 * res/xml/device_admin_receiver.xml and the "no access to personal data"
 * trust promise made on the marketing site.
 *
 * IMPORTANT — this app is never hidden from the customer. It always shows
 * up under its real name in the app drawer, in Settings → Apps, and in the
 * device admin list, and the sale agreement discloses that it's installed
 * and what it does. An app that conceals its own presence on someone else's
 * device while giving a third party remote control over it is stalkerware,
 * full stop, regardless of the legitimate business reason behind it — see
 * the conversation history for the fuller reasoning. Do not add
 * PackageManager component-hiding, dpm.setApplicationHidden, or similar
 * here without revisiting that decision explicitly with the user first.
 * The same principle applies to permissions: this app asks for
 * notifications the normal way (a system prompt during staff setup, see
 * CustomerHomeActivity), not by silently self-granting them.
 *
 * Verified on an emulator (2026-09-14): Device Owner grant, lock-task
 * pinning (Home/Back/Recents all blocked), and unlock all work. Not yet
 * verified: DISALLOW_FACTORY_RESET actually blocking Settings' reset
 * option, and none of this surviving a real device reboot.
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

        // Customer can't uninstall this app while payments are outstanding.
        dpm.setUninstallBlocked(adminComponent, appContext.packageName, true)

        // Only our own package may be pinned in lock task mode — this is
        // what makes the overdue screen unremovable via Home/Recents.
        dpm.setLockTaskPackages(adminComponent, arrayOf(appContext.packageName))

        // Blocks the "Erase all data / factory reset" option in Settings.
        // Honest caveat: this blocks the normal in-Settings reset path on a
        // device already running as Device Owner. It does not guarantee
        // protection against a technically sophisticated attempt to flash a
        // different firmware via an unlocked bootloader/recovery mode with
        // physical access — no app-level API can promise that.
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)

        LockNotifier.createChannel(appContext)
    }

    /** Call once the device is fully paid off. */
    fun liftRestrictions() {
        if (!isDeviceOwner) return
        dpm.setUninstallBlocked(adminComponent, appContext.packageName, false)
        dpm.setLockTaskPackages(adminComponent, arrayOf())
        dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
    }

    /**
     * Triggers the unremovable overdue screen via LockNotifier — a plain
     * startActivity() only reliably works while this app already has a
     * visible window of its own, so the real trigger is a full-screen-intent
     * notification (see LockNotifier for why). Actual lock-task pinning
     * happens inside LockScreenActivity.onCreate via startLockTask().
     */
    fun lockDevice(context: Context) {
        LockStateStore(context).isLocked = true
        LockNotifier.showLockScreen(context)
    }

    /** Called from LockScreenActivity once a payment is confirmed. */
    fun unlockDevice(activity: android.app.Activity) {
        LockStateStore(activity).isLocked = false
        LockNotifier.cancel(activity)
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
