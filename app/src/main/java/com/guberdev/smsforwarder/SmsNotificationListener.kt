package com.guberdev.smsforwarder

import android.app.Notification
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class SmsNotificationListener : NotificationListenerService() {

    // Common SMS app package names
    private val SMS_PACKAGES = setOf(
        "com.google.android.apps.messaging",    // Google Messages
        "com.samsung.android.messaging",         // Samsung Messages
        "com.sec.android.app.messaging",         // Samsung (older)
        "com.android.mms",                       // AOSP Messages
        "com.oneplus.mms",
        "com.miui.sms",
        "com.huawei.mms",
        "com.coloros.mms",
        "com.lge.message"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Only process notifications from SMS apps or message-category notifications
        val isSmsApp = sbn.packageName in SMS_PACKAGES
        val isMessageCategory = sbn.notification.category == Notification.CATEGORY_MESSAGE
        if (!isSmsApp && !isMessageCategory) return

        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: return

        if (text.isBlank()) return

        Log.d("SmsNotificationListener", "SMS notification from $title: $text")

        val intent = Intent(this, SmsForwarderService::class.java).apply {
            action = "FORWARD_SMS"
            putExtra("sender", title)
            putExtra("message", text)
            putExtra("timestamp", sbn.postTime)
            putExtra("source", "notification")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
