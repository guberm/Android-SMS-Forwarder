package com.guberdev.smsync

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
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

    private val CHANNEL_ID = "SmsSyncChannel"
    private val scope = CoroutineScope(Dispatchers.IO)
    private var googleApiHelper: GoogleApiHelper? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
                val prefs = getSharedPreferences("SmsSyncPrefs", Context.MODE_PRIVATE)
                
                // Get or find spreadsheet ID
                var spreadsheetId = prefs.getString("spreadsheet_id", null)
                if (spreadsheetId == null) {
                    spreadsheetId = helper.findOrCreateSpreadsheet()
                    prefs.edit().putString("spreadsheet_id", spreadsheetId).apply()
                }

                if (spreadsheetId != null) {
                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(timestamp))
                    
                    // 1. Log to sheets
                    helper.appendRow(spreadsheetId, dateStr, sender, message)
                    
                    // 2. Send email to self
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
            .setContentTitle("SMS Sync Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "SMS Sync Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
