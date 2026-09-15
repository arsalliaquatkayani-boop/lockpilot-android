package com.mylockpilot.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the get_device_lock_state Postgres function (see
 * lockpilot-backend/migrations/005_device_sync.sql) — the only backend
 * endpoint this app calls. It authenticates with this device's own paired
 * secret, not a Supabase user session, so a lost or stolen phone can only
 * ever read its own should_be_locked value, never any other device's or
 * shop's data.
 *
 * The URL and key below are the same public, RLS-scoped values used by the
 * website's Supabase client (lockpilot/src/lib/supabase.ts) — safe to ship
 * in an APK. The actual secret (service_role key) never goes near this app.
 */
object SupabaseSync {
    private const val SUPABASE_URL = "https://laqcafhkqmgkpuapthdq.supabase.co"
    private const val SUPABASE_ANON_KEY = "sb_publishable_kimtZuvvkcQnNmAKQY-Fwg_dLNWxT8H"

    class SyncException(message: String) : Exception(message)

    /** Blocking network call — always run this from a background thread. */
    private fun callRpc(
        functionName: String,
        deviceId: String,
        deviceSecret: String,
        extraParams: Map<String, String> = emptyMap(),
    ): String {
        val url = URL("$SUPABASE_URL/rest/v1/rpc/$functionName")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000

            val body = JSONObject().apply {
                put("p_device_id", deviceId)
                put("p_device_secret", deviceSecret)
                extraParams.forEach { (key, value) -> put(key, value) }
            }
            OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream.bufferedReader().use { it.readText() }

            if (status !in 200..299) {
                val message = runCatching { JSONObject(responseText).optString("message") }.getOrNull()
                throw SyncException(message?.takeIf { it.isNotBlank() } ?: "Sync failed (HTTP $status)")
            }

            return responseText
        } finally {
            connection.disconnect()
        }
    }

    fun fetchShouldBeLocked(deviceId: String, deviceSecret: String): Boolean {
        val rows = JSONArray(callRpc("get_device_lock_state", deviceId, deviceSecret))
        if (rows.length() == 0) throw SyncException("Device not found")
        return rows.getJSONObject(0).getBoolean("should_be_locked")
    }

    /** Everything the customer-facing home screen shows — see
     *  lockpilot-backend/migrations/006_customer_view.sql for the shape. */
    fun fetchCustomerView(deviceId: String, deviceSecret: String): JSONObject =
        JSONObject(callRpc("get_device_customer_view", deviceId, deviceSecret))

    /** Lets this device receive instant "go check now" pushes instead of
     *  waiting for the next 15-minute background check — see
     *  lockpilot-backend/migrations/007_push_notifications.sql. */
    fun registerFcmToken(deviceId: String, deviceSecret: String, fcmToken: String) {
        callRpc("register_fcm_token", deviceId, deviceSecret, mapOf("p_fcm_token" to fcmToken))
    }
}
