package com.example.smsforwarder

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsForwarderService : Service() {

    private val CHANNEL_ID = "SmsForwarderChannel"
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Monitoring SMS...")
        startForeground(1, notification)

        if (intent?.action == "FORWARD_SMS") {
            val sender = intent.getStringExtra("sender") ?: "Unknown"
            val message = intent.getStringExtra("message") ?: "No content"
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            
            forwardSms(sender, message, timestamp)
        }

        return START_STICKY
    }

    private fun forwardSms(sender: String, message: String, timestamp: Long) {
        scope.launch {
            val prefs = getSharedPreferences("SmsForwarderPrefs", Context.MODE_PRIVATE)
            val url = prefs.getString("google_script_url", "")
            
            if (!url.isNullOrBlank()) {
                NetworkClient.sendSms(url, sender, message, timestamp)
            }
        }
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Forwarder Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Sms Forwarder Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
