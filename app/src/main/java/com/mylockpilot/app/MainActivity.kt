package com.mylockpilot.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.mylockpilot.app.databinding.ActivityMainBinding
import java.util.concurrent.TimeUnit

/**
 * Placeholder setup/status screen. Shown once when staff open the app
 * during provisioning; not part of the customer-facing lock flow.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var pairing: PairingStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pairing = PairingStore(this)

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

        binding.testLockButton.setOnClickListener {
            policyHelper.lockDevice(this)
        }

        binding.pairButton.setOnClickListener {
            val deviceId = binding.pairingDeviceIdInput.text.toString().trim()
            val secret = binding.pairingSecretInput.text.toString().trim()
            if (deviceId.isEmpty() || secret.isEmpty()) return@setOnClickListener

            pairing.save(deviceId, secret)
            schedulePeriodicSync()
            triggerImmediateSync()
            refreshPairingUi()
        }

        binding.syncNowButton.setOnClickListener {
            triggerImmediateSync()
        }

        refreshPairingUi()
        if (pairing.isPaired) schedulePeriodicSync()
    }

    private fun refreshPairingUi() {
        val paired = pairing.isPaired
        binding.pairingForm.visibility = if (paired) View.GONE else View.VISIBLE
        binding.syncNowButton.visibility = if (paired) View.VISIBLE else View.GONE
        binding.pairingStatus.text = if (paired) {
            "Paired with backend"
        } else {
            "Not paired yet — enter the pairing code shown on this device's row in the dashboard."
        }
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
