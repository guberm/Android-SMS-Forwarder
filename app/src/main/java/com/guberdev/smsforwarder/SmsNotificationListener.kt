package com.guberdev.smsforwarder

import android.app.Notification
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class SmsNotificationListener : NotificationListenerService() {

    // Common messaging app package names
    private val MESSAGE_PACKAGES = setOf(
        "com.google.android.apps.messaging",    // Google Messages
        "com.samsung.android.messaging",         // Samsung Messages
        "com.sec.android.app.messaging",         // Samsung (older)
        "com.android.mms",                       // AOSP Messages
        "com.oneplus.mms",
        "com.miui.sms",
        "com.huawei.mms",
        "com.coloros.mms",
        "com.lg.message",
        "com.whatsapp",                          // WhatsApp
        "com.whatsapp.w4b",                      // WhatsApp Business
        "com.microsoft.teams",                   // Microsoft Teams
        "com.slack",                             // Slack
        "com.viber.voip",                        // Viber
        "org.telegram.messenger",                // Telegram
        "com.skype.raider",                       // Skype
        "com.facebook.orca",                     // Facebook Messenger
        "com.tencent.mm",                        // WeChat
        "jp.naver.line.android",                 // LINE
        "com.discord",                           // Discord
        "com.pushbullet.android"                 // Pushbullet
    )

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        
        // Detection logic:
        // 1. Is it in our list of known message apps?
        val isKnownApp = sbn.packageName in MESSAGE_PACKAGES
        
        // 2. Does it have the explicit message category?
        val isMessageCategory = sbn.notification.category == Notification.CATEGORY_MESSAGE
        
        // 3. Does it have messaging-specific extras (common in modern Android apps)?
        val hasMessagingExtras = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            extras.containsKey(Notification.EXTRA_MESSAGING_PERSON) || 
            extras.containsKey(Notification.EXTRA_CONVERSATION_TITLE) ||
            extras.containsKey("android.messagingUser")
        } else {
            false
        }

        if (!isKnownApp && !isMessageCategory && !hasMessagingExtras) return

        val title = extras.getString(Notification.EXTRA_TITLE) ?: return
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: return

        if (text.isBlank()) return

        val appName = try {
            val pm = packageManager
            val ai = pm.getApplicationInfo(sbn.packageName, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            "Notification"
        }

        Log.d("SmsNotificationListener", "Message from $appName ($title): $text")

        val intent = Intent(this, SmsForwarderService::class.java).apply {
            action = "FORWARD_SMS"
            putExtra("sender", title)
            putExtra("message", text)
            putExtra("timestamp", sbn.postTime)
            putExtra("source", appName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
