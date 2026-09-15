package com.mylockpilot.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
 * Verified on the Android 17 emulator (2026-09-14): Home, Back, and Recents
 * all fail to escape this screen once Device Owner + lock task are active.
 * Still unverified: surviving a reboot into this same screen, and
 * stopLockTask() cleanly handing control back (see testUnlockButton below).
 */
class LockScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockScreenBinding
    private lateinit var policyHelper: DevicePolicyHelper

    // Lets LockSyncWorker unlock this screen from a background thread once
    // the backend reports the overdue payment is cleared — the worker can't
    // call stopLockTask() itself since that only works on the pinned activity.
    private val remoteUnlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onPaymentConfirmed()
        }
    }

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

        binding.testUnlockButton.setOnClickListener {
            onPaymentConfirmed()
        }

        val filter = IntentFilter(LockSyncWorker.ACTION_REMOTE_UNLOCK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(remoteUnlockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(remoteUnlockReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(remoteUnlockReceiver)
    }

    /** Called either by the test button or by LockSyncWorker once the
     *  backend confirms this device's overdue payment is cleared. */
    private fun onPaymentConfirmed() {
        policyHelper.unlockDevice(this)
    }
}
