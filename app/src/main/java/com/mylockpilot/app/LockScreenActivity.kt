package com.mylockpilot.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.mylockpilot.app.databinding.ActivityLockScreenBinding

/**
 * The unremovable "payment overdue" screen. Two things make it hard to
 * leave: showing over the keyguard (setShowWhenLocked/setTurnScreenOn) and
 * pinning it via startLockTask(), which requires this app to be Device
 * Owner (see DevicePolicyHelper.applyBaselinePolicies). The default Phone
 * and Messages apps are also allowlisted for lock task, so Home/Recents/the
 * app drawer are blocked but the customer can still make/answer calls and
 * text via the two buttons on this screen.
 *
 * Verified on the Android 17 emulator (2026-09-14): Home, Back, and Recents
 * all fail to escape this screen once Device Owner + lock task are active.
 * Unlocking happens two ways, both driven by the backend's should_be_locked
 * flag — there is no in-app way to unlock without it, on purpose: a
 * broadcast from LockSyncWorker for the common case where this exact
 * activity instance is alive, and a direct self-check in onResume() as a
 * fallback for when the OS killed and later recreated this activity (real
 * gap found testing on a real low-RAM phone on 2026-09-16 — the broadcast
 * has no one to reach if the old instance is gone).
 */
class LockScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLockScreenBinding
    private lateinit var policyHelper: DevicePolicyHelper
    private var shopPhone: String? = null

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

        if (policyHelper.isDeviceOwner) {
            startLockTask()
        }

        binding.shopContactText.setOnClickListener {
            shopPhone?.let { phone -> startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }
        }

        // Being locked over a missed payment shouldn't cut someone off from
        // basic phone/text access — DevicePolicyHelper.applyBaselinePolicies
        // already allowlists the default dialer and SMS app for lock task
        // mode; these buttons are how the customer actually reaches them
        // with Home/Recents/the app drawer blocked.
        binding.openPhoneButton.setOnClickListener { openDialer() }
        binding.openMessagesButton.setOnClickListener { openMessages() }

        loadShopContact()

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

    override fun onResume() {
        super.onResume()
        // Belt-and-suspenders alongside the broadcast: whenever this screen
        // actually becomes visible again (e.g. the customer wakes the
        // screen), ask the backend directly instead of only trusting that a
        // broadcast arrived at the right moment. Catches the case where this
        // activity's process was killed by the OS while the screen was off
        // and recreated fresh — no broadcast could have reached that old,
        // now-gone instance.
        checkStillLocked()
    }

    private fun checkStillLocked() {
        val pairing = PairingStore(this)
        val deviceId = pairing.deviceId
        val deviceSecret = pairing.deviceSecret
        if (deviceId == null || deviceSecret == null) return

        Thread {
            try {
                val stillLocked = SupabaseSync.fetchShouldBeLocked(deviceId, deviceSecret)
                if (!stillLocked) {
                    runOnUiThread { onPaymentConfirmed() }
                }
            } catch (_: Exception) {
                // No network right now — stay locked and let the next
                // background sync or screen-on retry this.
            }
        }.start()
    }

    /** Fills in the real shop name/phone instead of leaving the layout blank. */
    private fun loadShopContact() {
        val pairing = PairingStore(this)
        val deviceId = pairing.deviceId
        val deviceSecret = pairing.deviceSecret
        if (deviceId == null || deviceSecret == null) return

        Thread {
            try {
                val view = SupabaseSync.fetchCustomerView(deviceId, deviceSecret)
                val shopName = view.optString("shop_name").ifBlank { getString(R.string.default_shop_name) }
                val phone = if (view.isNull("shop_phone")) null else view.optString("shop_phone").takeIf { it.isNotBlank() }
                shopPhone = phone

                runOnUiThread {
                    binding.shopNameText.text = shopName
                    binding.shopContactText.text = phone ?: getString(R.string.no_phone_on_file)
                }
            } catch (_: Exception) {
                // Lock screen still works without this — it just can't show
                // shop contact details until the next successful load.
            }
        }.start()
    }

    /** Called by LockSyncWorker once the backend confirms this device's
     *  overdue payment is cleared — the only path that unlocks this screen. */
    private fun onPaymentConfirmed() {
        policyHelper.unlockDevice(this)
    }

    /** Opens the device's default dialer to its keypad — not tied to the
     *  shop's number, this is for the customer's own calls. */
    private fun openDialer() {
        try {
            startActivity(Intent(Intent.ACTION_DIAL))
        } catch (_: Exception) {
            Toast.makeText(this, R.string.lock_open_phone, Toast.LENGTH_SHORT).show()
        }
    }

    /** Opens the device's default SMS app directly (ACTION_MAIN keeps this
     *  working even on launchers that don't expose a bare "compose" intent). */
    private fun openMessages() {
        val defaultSmsPackage = try {
            Telephony.Sms.getDefaultSmsPackage(this)
        } catch (_: Exception) {
            null
        }

        val launchIntent = defaultSmsPackage?.let { packageManager.getLaunchIntentForPackage(it) }
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            Toast.makeText(this, R.string.lock_open_messages, Toast.LENGTH_SHORT).show()
        }
    }
}
