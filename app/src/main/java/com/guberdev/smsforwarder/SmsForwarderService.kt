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
    private val scope = CoroutineScope(Dispatchers.IO)
    private var googleApiHelper: GoogleApiHelper? = null
    private var smsObserver: ContentObserver? = null
    private var lastKnownSmsId: Long = -1L

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerSmsObserver()
    }

    private fun registerSmsObserver() {
        // Read current max SMS ID so we only process new ones
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
                Log.d("SmsForwarder", "Observer: new SMS id=$id from=$sender")
                lastKnownSmsId = maxOf(lastKnownSmsId, id)
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
            
            processSms(sender, message, timestamp)
        }

        return START_STICKY
    }

    private fun processSms(sender: String, message: String, timestamp: Long) {
        scope.launch {
            try {
                if (googleApiHelper == null) {
                    initApiHelper()
                }

                val helper = googleApiHelper ?: return@launch
                val prefs = getSharedPreferences("SmsForwarderPrefs", Context.MODE_PRIVATE)
                
                var spreadsheetId = prefs.getString("spreadsheet_id", null)
                if (spreadsheetId == null) {
                    spreadsheetId = helper.findOrCreateSpreadsheet()
                    prefs.edit().putString("spreadsheet_id", spreadsheetId).apply()
                }

                if (spreadsheetId != null) {
                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(timestamp))
                    helper.appendRow(spreadsheetId, dateStr, sender, message)
                    helper.sendEmailToSelf(sender, dateStr, message)
                    Log.d("SmsForwarder", "Successfully synced SMS from $sender")
                }
            } catch (e: Exception) {
                Log.e("SmsForwarder", "Failed to sync SMS: ${e.message}")
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
