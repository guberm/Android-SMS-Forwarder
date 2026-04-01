package com.guberdev.smsforwarder

import android.app.*
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.sheets.v4.SheetsScopes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SmsForwarderService : Service() {

    private val CHANNEL_ID = "SmsForwarderChannel"
    private val TARGET_SHEET_ID = "1jNUK8K4Qo3NAdO3fpy_dyYICW5vMWIx2sk0ODijA7Pc"
    private val scope = CoroutineScope(Dispatchers.IO)
    private var googleApiHelper: GoogleApiHelper? = null
    private var smsObserver: ContentObserver? = null
    private var lastKnownSmsId: Long = -1L

    // Dedup cache: key = "sender|bodyHash", value = timestamp
    private val recentMessages = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerSmsObserver()
    }

    private fun registerSmsObserver() {
        lastKnownSmsId = getLatestSmsId()
        Log.d("SmsForwarder", "Starting observer, last SMS id=$lastKnownSmsId")

        smsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                scope.launch { checkForNewSms() }
            }
        }
        contentResolver.registerContentObserver(
            Telephony.Sms.Inbox.CONTENT_URI,
            true,
            smsObserver!!
        )
    }

    private fun getLatestSmsId(): Long {
        val cursor = contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            null, null,
            "${Telephony.Sms.DATE} DESC LIMIT 1"
        ) ?: return -1L
        return cursor.use {
            if (it.moveToFirst()) it.getLong(0) else -1L
        }
    }

    private fun checkForNewSms() {
        val cursor = contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms._ID} > ?",
            arrayOf(lastKnownSmsId.toString()),
            "${Telephony.Sms.DATE} ASC"
        ) ?: return

        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val sender = it.getString(1) ?: "Unknown"
                val body = it.getString(2) ?: ""
                val timestamp = it.getLong(3)
                lastKnownSmsId = maxOf(lastKnownSmsId, id)

                if (body.isBlank()) {
                    // OTP_REDACTION stripped the body — notification listener will catch it
                    Log.d("SmsForwarder", "Skipping blank body (OTP redacted) id=$id from=$sender")
                    continue
                }

                Log.d("SmsForwarder", "Observer: new SMS id=$id from=$sender")
                processSms(sender, body, timestamp)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        smsObserver?.let { contentResolver.unregisterContentObserver(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Monitoring incoming SMS...")
        startForeground(1, notification)

        if (intent?.action == "FORWARD_SMS") {
            val sender = intent.getStringExtra("sender") ?: "Unknown"
            val message = intent.getStringExtra("message") ?: ""
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            if (message.isNotBlank()) {
                processSms(sender, message, timestamp)
            }
        }

        return START_STICKY
    }

    private fun isDuplicate(sender: String, message: String): Boolean {
        val key = "$sender|${message.take(80).hashCode()}"
        val now = System.currentTimeMillis()
        val last = recentMessages[key]
        return if (last != null && now - last < 30_000) {
            Log.d("SmsForwarder", "Dedup: skipping duplicate from $sender")
            true
        } else {
            recentMessages[key] = now
            recentMessages.entries.removeIf { now - it.value > 60_000 }
            false
        }
    }

    private fun processSms(sender: String, message: String, timestamp: Long) {
        if (isDuplicate(sender, message)) return

        scope.launch {
            try {
                if (googleApiHelper == null) {
                    initApiHelper()
                }

                val helper = googleApiHelper ?: return@launch
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(timestamp))
                helper.appendRow(TARGET_SHEET_ID, dateStr, sender, message)
                helper.sendEmailToSelf(sender, dateStr, message)
                Log.d("SmsForwarder", "Synced SMS from $sender")
            } catch (e: Exception) {
                Log.e("SmsForwarder", "Failed to sync SMS from $sender: ${e.message}")
            }
        }
    }

    private fun initApiHelper() {
        val account = GoogleSignIn.getLastSignedInAccount(this) ?: return
        val scopes = listOf(
            SheetsScopes.SPREADSHEETS,
            DriveScopes.DRIVE_FILE,
            GmailScopes.GMAIL_SEND
        )
        val credential = GoogleAccountCredential.usingOAuth2(this, scopes)
        credential.selectedAccount = account.account
        googleApiHelper = GoogleApiHelper(this, credential)
    }

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Forwarder Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "SMS Forwarder Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
