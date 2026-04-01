package com.guberdev.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED" ||
            intent.action == "android.provider.Telephony.SMS_DELIVER") {
            val bundle = intent.extras
            if (bundle != null) {
                val pdus = bundle["pdus"] as Array<*>?
                if (pdus != null) {
                    for (pdu in pdus) {
                        val format = bundle.getString("format")
                        val smsMessage = SmsMessage.createFromPdu(pdu as ByteArray, format)
                        
                        val sender = smsMessage.displayOriginatingAddress ?: "Unknown"
                        val body = smsMessage.displayMessageBody ?: ""
                        val timestamp = smsMessage.timestampMillis
                        
                        Log.d("SmsReceiver", "SMS from $sender at $timestamp: $body")
                        
                        val serviceIntent = Intent(context, SmsForwarderService::class.java).apply {
                            action = "FORWARD_SMS"
                            putExtra("sender", sender)
                            putExtra("message", body)
                            putExtra("timestamp", timestamp)
                        }
                        context.startForegroundService(serviceIntent)
                    }
                }
            }
        }
    }
}
