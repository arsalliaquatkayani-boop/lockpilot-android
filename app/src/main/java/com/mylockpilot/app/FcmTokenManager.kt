package com.mylockpilot.app

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Best-effort wrapper around Firebase Cloud Messaging. Every call is guarded
 * so a device without google-services.json configured (see
 * FIREBASE_SETUP.md) — or with Google Play Services unavailable, as on some
 * budget phones — just silently skips push registration and keeps relying
 * on LockSyncWorker's 15-minute background check instead. Push is a speed
 * optimization, never a requirement for locking/unlocking to work.
 */
object FcmTokenManager {
    private const val TAG = "FcmTokenManager"

    /** Fetches the current token and registers it, if this device is paired. */
    fun registerCurrentToken(context: Context) {
        val pairing = PairingStore(context)
        val deviceId = pairing.deviceId
        val deviceSecret = pairing.deviceSecret
        if (deviceId == null || deviceSecret == null) return

        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Could not fetch FCM token: ${task.exception?.message}")
                    return@addOnCompleteListener
                }
                val token = task.result ?: return@addOnCompleteListener
                registerToken(deviceId, deviceSecret, token)
            }
        } catch (e: Exception) {
            Log.w(TAG, "FCM unavailable, relying on periodic sync only: ${e.message}")
        }
    }

    fun registerToken(deviceId: String, deviceSecret: String, token: String) {
        Thread {
            try {
                SupabaseSync.registerFcmToken(deviceId, deviceSecret, token)
            } catch (e: Exception) {
                Log.w(TAG, "Could not register FCM token: ${e.message}")
            }
        }.start()
    }
}
