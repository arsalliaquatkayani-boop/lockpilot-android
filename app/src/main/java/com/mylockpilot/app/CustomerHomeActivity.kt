package com.mylockpilot.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mylockpilot.app.databinding.ActivityCustomerHomeBinding
import com.mylockpilot.app.databinding.ItemPaymentRowBinding
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/**
 * What the customer actually sees every time they open this app: their own
 * shop's contact info, how much of their plan is paid off, and their full
 * payment history. This is the real home screen for a paired, provisioned
 * device — MainActivity only shows the pairing form, and only before this
 * screen exists for that device (a one-time staff setup step).
 */
class CustomerHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomerHomeBinding
    private lateinit var pairing: PairingStore
    private var shopPhone: String? = null

    // Standard, visible system permission prompt — shown once, normally
    // right when staff finish pairing the device. Notifications are what
    // let the lock screen reliably interrupt whatever app the customer is
    // using later on (see LockNotifier); this app never self-grants it.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pairing = PairingStore(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        binding.refreshButton.setOnClickListener { loadCustomerView() }
        binding.rePairText.setOnClickListener {
            pairing.clear()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        binding.callShopButton.setOnClickListener {
            shopPhone?.let { phone ->
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            }
        }

        loadCustomerView()
    }

    private fun loadCustomerView() {
        val deviceId = pairing.deviceId
        val deviceSecret = pairing.deviceSecret
        if (deviceId == null || deviceSecret == null) {
            binding.statusMessageText.text = getString(R.string.not_paired_message)
            return
        }

        binding.statusMessageText.text = getString(R.string.loading_message)
        Thread {
            try {
                val view = SupabaseSync.fetchCustomerView(deviceId, deviceSecret)
                runOnUiThread {
                    render(view)
                    binding.statusMessageText.text = ""
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.statusMessageText.text = getString(R.string.load_failed_message, e.message)
                }
            }
        }.start()
    }

    private fun render(view: JSONObject) {
        val shopName = view.optString("shop_name").ifBlank { getString(R.string.default_shop_name) }
        val deviceLabel = view.optString("device_label")
        val status = view.optString("status", "active")
        val shouldBeLocked = view.optBoolean("should_be_locked", false)
        shopPhone = if (view.isNull("shop_phone")) null else view.optString("shop_phone").takeIf { it.isNotBlank() }

        binding.shopNameText.text = shopName
        binding.deviceLabelText.text = deviceLabel

        when {
            shouldBeLocked || status == "locked" -> {
                binding.statusCard.setCardBackgroundColor(getColor(R.color.danger))
                binding.statusText.text = getString(R.string.status_locked)
            }
            status == "paid_off" -> {
                binding.statusCard.setCardBackgroundColor(getColor(R.color.success))
                binding.statusText.text = getString(R.string.status_paid_off)
            }
            else -> {
                binding.statusCard.setCardBackgroundColor(getColor(R.color.success))
                binding.statusText.text = getString(R.string.status_active)
            }
        }

        binding.contactShopNameText.text = shopName
        binding.contactShopPhoneText.text = shopPhone ?: getString(R.string.no_phone_on_file)
        binding.callShopButton.isEnabled = shopPhone != null

        renderPlan(view.optJSONObject("plan"))
        renderPayments(view.optJSONArray("payments"))
    }

    private fun renderPlan(plan: JSONObject?) {
        if (plan == null) {
            binding.planTotalText.text = getString(R.string.no_plan_on_file)
            binding.planInstallmentText.text = ""
            binding.planPaidText.text = ""
            binding.planRemainingText.text = ""
            return
        }

        val totalAmount = plan.optDouble("total_amount", 0.0)
        val installmentAmount = plan.optDouble("installment_amount", 0.0)
        val frequency = plan.optString("frequency", "monthly")
        val paidCount = plan.optInt("paid_count", 0)
        val remainingCount = plan.optInt("remaining_count", 0)
        val remainingBalance = plan.optDouble("remaining_balance", 0.0)
        val installmentCount = plan.optInt("installment_count", paidCount + remainingCount)

        binding.planTotalText.text = getString(R.string.plan_total_amount, formatRs(totalAmount))
        binding.planInstallmentText.text = getString(
            R.string.plan_installment_amount,
            formatRs(installmentAmount),
            if (frequency == "weekly") getString(R.string.frequency_week) else getString(R.string.frequency_month),
        )
        binding.planPaidText.text = getString(R.string.plan_installments_paid, paidCount, installmentCount)
        binding.planRemainingText.text = getString(R.string.plan_remaining_balance, formatRs(remainingBalance))
    }

    private fun renderPayments(payments: org.json.JSONArray?) {
        binding.paymentsContainer.removeAllViews()
        if (payments == null || payments.length() == 0) return

        val today = LocalDate.now().toString()

        for (i in 0 until payments.length()) {
            val payment = payments.getJSONObject(i)
            val row = ItemPaymentRowBinding.inflate(layoutInflater, binding.paymentsContainer, false)

            val dueDate = payment.optString("due_date")
            val amount = payment.optDouble("amount", 0.0)
            val paidDate = if (payment.isNull("paid_date")) null else payment.optString("paid_date")

            row.rowDateText.text = getString(R.string.payment_due_date, dueDate)
            row.rowAmountText.text = formatRs(amount)

            when {
                paidDate != null -> {
                    row.rowStatusText.text = getString(R.string.payment_paid_on, paidDate)
                    row.rowStatusText.setTextColor(getColor(R.color.success))
                }
                dueDate < today -> {
                    row.rowStatusText.text = getString(R.string.payment_overdue)
                    row.rowStatusText.setTextColor(getColor(R.color.danger))
                }
                else -> {
                    row.rowStatusText.text = getString(R.string.payment_upcoming)
                    row.rowStatusText.setTextColor(getColor(R.color.slate))
                }
            }

            binding.paymentsContainer.addView(row.root)
        }
    }

    private fun formatRs(amount: Double): String =
        "Rs ${NumberFormat.getNumberInstance(Locale.US).format(amount)}"
}
