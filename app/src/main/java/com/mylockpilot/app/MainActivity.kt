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
        binding.setupBody.text = if (policyHelper.isDeviceOwner) {
            getString(R.string.setup_body)
        } else {
            "LockPilot is not the Device Owner on this device yet."
        }

        binding.testLockButton.setOnClickListener {
            policyHelper.lockDevice(this)
        }
    }
}
