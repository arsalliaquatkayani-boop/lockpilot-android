package com.mylockpilot.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * A plain context.startActivity() from a background WorkManager task only
 * brings LockScreenActivity to the front while this app already has some
 * visible window of its own — Android's background-activity-launch
 * restrictions block it the moment a different app (anything the customer
 * happens to be using) is in the foreground. Verified directly: worked
 * while our own MainActivity was on screen, silently failed once the
 * dialer app was on top instead.
 *
 * A full-screen-intent notification is the mechanism Android actually
 * grants background apps for this — the same one incoming calls and
 * alarms use — so that's the real, reliable trigger. The direct
 * startActivity() call stays as a same-app fast path; the notification is
 * what makes locking work regardless of what the customer is doing.
 */
object LockNotifier {
    private const val CHANNEL_ID = "device_lock_alerts"
    private const val NOTIFICATION_ID = 1001

    fun createChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Device lock alerts",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Shown when this device locks for an overdue installment payment."
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun showLockScreen(context: Context) {
        val intent = Intent(context, LockScreenActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lock)
            .setContentTitle(context.getString(R.string.lock_title))
            .setContentText(context.getString(R.string.lock_body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)

        // Free same-app fast path — harmless no-op when it can't take effect.
        context.startActivity(intent)
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }
}
