package com.mylockpilot.app

import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives the "go check now" push sent by the send-lock-push Edge Function
 * (see lockpilot-backend/supabase/functions/send-lock-push) the instant
 * should_be_locked changes in the database. Deliberately does nothing with
 * the message's own data payload — it's just a wake-up signal. The actual
 * decision to lock or unlock always goes through LockSyncWorker's normal,
 * secure get_device_lock_state check, so a forged push can't lie about
 * lock state; at worst it triggers a redundant, harmless check.
 */
class LockPilotMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        WorkManager.getInstance(applicationContext)
            .enqueue(OneTimeWorkRequestBuilder<LockSyncWorker>().build())
    }

    override fun onNewToken(token: String) {
        FcmTokenManager.registerCurrentToken(applicationContext)
    }
}
