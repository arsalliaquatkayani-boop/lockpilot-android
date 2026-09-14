package com.mylockpilot.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mylockpilot.app.databinding.ActivityMainBinding

/**
 * Placeholder setup/status screen. Shown once when staff open the app
 * during provisioning; not part of the customer-facing lock flow.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // TODO once a backend exists: show real device-owner / pairing
        // status here instead of the static strings in activity_main.xml.
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
    }
}
