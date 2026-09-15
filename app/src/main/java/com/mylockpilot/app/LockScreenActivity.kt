package com.mylockpilot.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
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
 * all fail to escape this screen once Device Owner + lock task are active,
 * and the real remote-unlock path (LockSyncWorker -> broadcast -> this
 * activity) unlocks it automatically once should_be_locked flips to false —
 * there is no in-app way to unlock without that, on purpose.
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
}
