package com.mylockpilot.app

import android.app.admin.DevicePolicyManager
import android.app.admin.FactoryResetProtectionPolicy
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.provider.Telephony
import android.telecom.TelecomManager

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
 * Verified on real Xiaomi hardware (2026-09-17): Device Owner grant,
 * lock-task pinning, unlock, and DISALLOW_FACTORY_RESET blocking the
 * Settings reset option all work and survive a reboot. Also confirmed:
 * DISALLOW_FACTORY_RESET does NOT block a hardware recovery-mode wipe
 * (Volume+Power) — no app-level API can, that's an intentional Android
 * safety valve. setFactoryResetProtectionPolicy below is what actually
 * closes that gap, by making a recovery-mode wipe produce a bricked
 * phone instead of a free one — see its own comment.
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

        // Our own package, plus the device's default Phone and Messages
        // apps, may run in lock task mode — this is what makes the overdue
        // screen unremovable via Home/Recents while still letting a locked
        // customer make/answer calls and send/receive texts, which is a
        // basic safety expectation even for a phone that's locked over a
        // missed payment.
        dpm.setLockTaskPackages(adminComponent, allowedLockTaskPackages())

        // Blocks the "Erase all data / factory reset" option in Settings.
        // Honest caveat: this blocks the normal in-Settings reset path on a
        // device already running as Device Owner. It does not guarantee
        // protection against a technically sophisticated attempt to flash a
        // different firmware via an unlocked bootloader/recovery mode with
        // physical access — no app-level API can promise that.
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)

        // Blocks turning USB debugging back on once the phone leaves the
        // shop's hands. Our own setup already happened before this runs, so
        // this only stops a customer from re-enabling it afterward to poke
        // at the device with adb (e.g. trying to remove the admin app or
        // interfere with lock state directly).
        dpm.addUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)

        LockNotifier.createChannel(appContext)
    }

    /**
     * The real backstop against DISALLOW_FACTORY_RESET above: recovery-mode
     * (hardware button) factory reset can't be blocked by any app — it's a
     * deliberate Android safety valve. What we CAN do is make that reset
     * useless to whoever performs it. This requires no Google account to
     * ever be signed into the customer's phone — recoveryEmail is a
     * challenge identity only, checked purely against Google's servers
     * after an unauthorized reset. Whoever resets the phone this way is met
     * with a "verify this account" screen for an account they don't control
     * and can't sign into, so the reset gains them a bricked phone, not a
     * free one.
     *
     * Called separately from applyBaselinePolicies (not as part of it)
     * because this is per-shop configuration (see FrpSetupWorker) fetched
     * over the network using this device's own pairing credentials, not
     * something known at provisioning time.
     */
    fun applyFrpPolicy(recoveryEmail: String) {
        if (!isDeviceOwner) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val frpPolicy = FactoryResetProtectionPolicy.Builder()
            .setFactoryResetProtectionEnabled(true)
            .setFactoryResetProtectionAccounts(listOf(recoveryEmail))
            .build()
        dpm.setFactoryResetProtectionPolicy(adminComponent, frpPolicy)
    }

    /**
     * Reads back whatever FRP policy is currently active, so callers (see
     * FrpSetupWorker) can skip redundant network calls once it's confirmed
     * set, rather than blindly re-fetching and re-applying forever.
     */
    fun isFrpPolicyApplied(): Boolean {
        if (!isDeviceOwner) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val policy = dpm.getFactoryResetProtectionPolicy(adminComponent) ?: return false
        return policy.isFactoryResetProtectionEnabled && policy.factoryResetProtectionAccounts.isNotEmpty()
    }

    /**
     * The device's own package plus whichever apps are currently set as the
     * default Phone (dialer) and Messages (SMS) apps — read fresh each time
     * rather than hardcoding a package name, since that differs by
     * manufacturer (Samsung, Pixel, etc. all ship their own dialer/messaging
     * apps under different package names).
     */
    private fun allowedLockTaskPackages(): Array<String> {
        val packages = mutableSetOf(appContext.packageName)

        try {
            val telecomManager =
                appContext.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            telecomManager?.defaultDialerPackage?.let { packages.add(it) }
        } catch (_: Exception) {
            // Falls back to just our own package — the lock screen's own
            // "contact shop" dial-out button still works via ACTION_DIAL.
        }

        try {
            Telephony.Sms.getDefaultSmsPackage(appContext)?.let { packages.add(it) }
        } catch (_: Exception) {
            // No default SMS app configured — nothing to add.
        }

        return packages.toTypedArray()
    }

    /** Call once the device is fully paid off. */
    fun liftRestrictions() {
        if (!isDeviceOwner) return
        dpm.setUninstallBlocked(adminComponent, appContext.packageName, false)
        dpm.setLockTaskPackages(adminComponent, arrayOf())
        dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_FACTORY_RESET)
        dpm.clearUserRestriction(adminComponent, UserManager.DISALLOW_DEBUGGING_FEATURES)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            dpm.setFactoryResetProtectionPolicy(
                adminComponent,
                FactoryResetProtectionPolicy.Builder().setFactoryResetProtectionEnabled(false).build(),
            )
        }
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
