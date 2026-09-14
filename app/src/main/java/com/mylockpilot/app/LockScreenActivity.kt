package com.mylockpilot.app

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.mylockpilot.app.databinding.ActivityLockScreenBinding

/**
 * The unremovable "payment overdue" screen. Two things make it hard to
 * leave: showing over the keyguard (setShowWhenLocked/setTurnScreenOn) and
 * pinning it via startLockTask(), which requires this app to be Device
 * Owner with itself set as the only lock-task package (see
 * DevicePolicyHelper.applyBaselinePolicies).
 *
 * UNVERIFIED — needs testing on an emulator or device: does back/home/
 * recents actually stay blocked, does it survive a reboot into this same
 * screen, does stopLockTask() cleanly hand control back.
 */
class LockScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockScreenBinding
    private lateinit var policyHelper: DevicePolicyHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // minSdk is 26 (Build.VERSION_CODES.O), so these are always available —
        // no need for a pre-O fallback via window flags.
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        binding = ActivityLockScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        policyHelper = DevicePolicyHelper(this)

        // TODO once a backend exists: replace these placeholders with the
        // real shop name/contact for this device's owning shop, and the
        // actual overdue amount, instead of the layout's static demo text.

        if (policyHelper.isDeviceOwner) {
            startLockTask()
        }

        // Intentionally swallow back-press: the customer should not be able
        // to dismiss this screen by any normal navigation gesture.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // no-op
                }
            },
        )
    }

    /** Not wired to a real payment event yet — call this once the backend
     *  confirms a payment for this device. */
    private fun onPaymentConfirmed() {
        policyHelper.unlockDevice(this)
    }
}
