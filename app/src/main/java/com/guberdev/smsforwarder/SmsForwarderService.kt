package com.guberdev.smsforwarder

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.sheets.v4.SheetsScopes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SmsForwarderService : Service() {

    private val CHANNEL_ID = "NotifForwarderChannel"
    private val TARGET_SHEET_ID = "1jNUK8K4Qo3NAdO3fpy_dyYICW5vMWIx2sk0ODijA7Pc"
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

                if (!SourcePrefs.isEnabled(this@SmsForwarderService, SourcePrefs.NATIVE_SMS)) {
                    LogStore.log(this@SmsForwarderService, "SmsForwarder", "SKIP: Native SMS disabled | from=$sender")
                    continue
                }
                Log.d("SmsForwarder", "Observer: new SMS id=$id from=$sender")
                processSms(sender, body, timestamp, "SMS")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        smsObserver?.let { contentResolver.unregisterContentObserver(it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Monitoring incoming messages...")
        startForeground(1, notification)

        if (intent?.action == "FORWARD_SMS") {
            val sender = intent.getStringExtra("sender") ?: "Unknown"
            val message = intent.getStringExtra("message") ?: ""
            val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
            val source = intent.getStringExtra("source") ?: "SMS"
            val packageName = intent.getStringExtra("packageName") ?: ""
            if (message.isNotBlank()) {
                // If packageName is set, check per-app preference; otherwise it's native SMS
                val enabled = if (packageName.isNotEmpty()) {
                    SourcePrefs.isEnabled(this, packageName)
                } else {
                    SourcePrefs.isEnabled(this, SourcePrefs.NATIVE_SMS)
                }
                if (enabled) {
                    processSms(sender, message, timestamp, source)
                } else {
                    LogStore.log(this, "SmsForwarder", "Filtered (disabled): $source")
                }
            }
        }

        return START_STICKY
    }

    private val dedupPrefs by lazy { getSharedPreferences("sms_dedup", Context.MODE_PRIVATE) }
    private val DEDUP_WINDOW_MS = 15_000L

    private fun isDuplicate(sender: String, message: String): Boolean {
        val key = "$sender|${message.trim().take(200).hashCode()}"
        val now = System.currentTimeMillis()

        val lastSeen = dedupPrefs.getLong(key, 0L)
        if (lastSeen > 0 && now - lastSeen < DEDUP_WINDOW_MS) {
            Log.d("SmsForwarder", "Dedup: skipping duplicate from $sender within ${DEDUP_WINDOW_MS / 1000}s")
            LogStore.log(this, "Dedup", "Skipped duplicate from $sender within ${DEDUP_WINDOW_MS / 1000}s")
            return true
        }

        dedupPrefs.edit().putLong(key, now).apply()

        // Prune stale entries older than 2x window
        val staleThreshold = now - DEDUP_WINDOW_MS * 2
        dedupPrefs.all.entries
            .filter { it.value is Long && (it.value as Long) < staleThreshold }
            .forEach { dedupPrefs.edit().remove(it.key).apply() }

        return false
    }

    private fun getDefaultSmsAppName(): String {
        return try {
            val defaultPackage = Telephony.Sms.getDefaultSmsPackage(this)
            if (defaultPackage != null) {
                val pm = packageManager
                val ai = pm.getApplicationInfo(defaultPackage, 0)
                pm.getApplicationLabel(ai).toString()
            } else {
                "SMS"
            }
        } catch (e: Exception) {
            "SMS"
        }
    }

    private fun getContactName(phoneNumber: String): String? {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
        val cursor = contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    private fun processSms(sender: String, message: String, timestamp: Long, source: String) {
        val contactName = if (sender.any { it.isDigit() }) getContactName(sender) else null
        val resolvedSender = if (contactName != null) "$contactName ($sender)" else sender
        val resolvedSource = if (source == "SMS") getDefaultSmsAppName() else source
        LogStore.log(this, "SmsForwarder", "MSG: $resolvedSource | $resolvedSender | ${message.take(80)}")
        if (isDuplicate(resolvedSender, message)) return

        scope.launch {
            syncWithRetry(resolvedSender, message, timestamp, resolvedSource)
        }
    }

    private suspend fun syncWithRetry(
        resolvedSender: String, message: String, timestamp: Long, resolvedSource: String,
        maxAttempts: Int = 5
    ) {
        var attempt = 0
        var delayMs = 5_000L
        while (attempt < maxAttempts) {
            try {
                if (googleApiHelper == null) initApiHelper()
                val helper = googleApiHelper ?: throw Exception("Google account not available")
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(timestamp))
                LogStore.log(this@SmsForwarderService, "Sync", "Sheets: sending | $resolvedSource | $resolvedSender")
                helper.appendRow(TARGET_SHEET_ID, dateStr, resolvedSource, resolvedSender, message)
                LogStore.log(this@SmsForwarderService, "Sync", "Sheets: OK | $resolvedSource | $resolvedSender")
                LogStore.log(this@SmsForwarderService, "Sync", "Email: sending | $resolvedSource | $resolvedSender")
                helper.sendEmailToSelf(resolvedSource, resolvedSender, dateStr, message)
                LogStore.log(this@SmsForwarderService, "Sync", "Email: OK | $resolvedSource | $resolvedSender")
                Log.d("SmsForwarder", "Synced $resolvedSource from $resolvedSender")
                return
            } catch (e: Exception) {
                attempt++
                if (attempt >= maxAttempts) {
                    Log.e("SmsForwarder", "Failed to sync $resolvedSource from $resolvedSender: ${e.message}")
                    LogStore.e(this@SmsForwarderService, "Sync", "FAILED | $resolvedSource | $resolvedSender | ${e.message}")
                    return
                }
                Log.w("SmsForwarder", "Sync attempt $attempt failed, retrying in ${delayMs / 1000}s: ${e.message}")
                LogStore.log(this@SmsForwarderService, "Sync", "RETRY $attempt/$maxAttempts in ${delayMs / 1000}s | ${e.message?.take(60)}")
                delay(delayMs)
                delayMs = minOf(delayMs * 2, 60_000L)
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
            .setContentTitle("Notifications Forwarder Active")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Notifications Forwarder Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
