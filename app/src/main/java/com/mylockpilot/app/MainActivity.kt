package com.mylockpilot.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mylockpilot.app.databinding.ActivityMainBinding
import java.util.concurrent.TimeUnit

/**
 * Staff-only setup screen. Shown once, right after a phone is provisioned
 * (Device Owner granted via the QR-code flow or, in development, `adb shell
 * dpm set-device-owner`), so staff can enter the pairing code from the
 * dashboard's Devices panel. Once paired, this screen never shows again —
 * every future launch redirects straight to CustomerHomeActivity, which is
 * what the customer actually uses.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var pairing: PairingStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pairing = PairingStore(this)

        if (pairing.isPaired) {
            schedulePeriodicSync()
            FcmTokenManager.registerCurrentToken(this)
            goToCustomerHome()
            return
        }

        // The dashboard's USB setup flow launches this activity directly
        // with these two extras (via `adb shell am start ... -e device_id
        // ... -e pairing_code ...`) right after granting Device Owner, so
        // staff never have to type the pairing code by hand.
        val extraDeviceId = intent.getStringExtra("device_id")
        val extraPairingCode = intent.getStringExtra("pairing_code")
        if (!extraDeviceId.isNullOrBlank() && !extraPairingCode.isNullOrBlank()) {
            pairing.save(extraDeviceId, extraPairingCode)
            schedulePeriodicSync()
            triggerImmediateSync()
            FcmTokenManager.registerCurrentToken(this)
            goToCustomerHome()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val policyHelper = DevicePolicyHelper(this)
        if (policyHelper.isDeviceOwner) {
            // Idempotent — safe to re-apply every time this screen opens,
            // so a policy added in an app update (like DISALLOW_FACTORY_RESET)
            // takes effect on already-provisioned devices too.
            policyHelper.applyBaselinePolicies()
        }
        binding.setupBody.text = if (policyHelper.isDeviceOwner) {
            getString(R.string.setup_body)
        } else {
            "Not yet set as Device Owner on this device."
        }
        binding.pairingStatus.text =
            "Not paired yet — enter the pairing code shown on this device's row in the dashboard."

        binding.pairButton.setOnClickListener {
            val deviceId = binding.pairingDeviceIdInput.text.toString().trim()
            val secret = binding.pairingSecretInput.text.toString().trim()
            if (deviceId.isEmpty() || secret.isEmpty()) return@setOnClickListener

            pairing.save(deviceId, secret)
            schedulePeriodicSync()
            triggerImmediateSync()
            FcmTokenManager.registerCurrentToken(this)
            goToCustomerHome()
        }
    }

    private fun goToCustomerHome() {
        startActivity(Intent(this, CustomerHomeActivity::class.java))
        finish()
    }

    private fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<LockSyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            LockSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun triggerImmediateSync() {
        WorkManager.getInstance(this).enqueue(OneTimeWorkRequestBuilder<LockSyncWorker>().build())
    }
}
