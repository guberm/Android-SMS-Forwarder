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
                    val format = bundle.getString("format")
                    val messages = pdus.map { SmsMessage.createFromPdu(it as ByteArray, format) }

                    val sender = messages.first().displayOriginatingAddress ?: "Unknown"
                    val body = messages.joinToString("") { it.displayMessageBody ?: "" }
                    val timestamp = messages.first().timestampMillis

                    Log.d("SmsReceiver", "SMS from $sender at $timestamp (${messages.size} parts): $body")
                    LogStore.log(context, "SmsReceiver", "Received from $sender (${messages.size} part(s)): ${body.take(120)}")

                    val serviceIntent = Intent(context, SmsForwarderService::class.java).apply {
                        action = "FORWARD_SMS"
                        putExtra("sender", sender)
                        putExtra("message", body)
                        putExtra("timestamp", timestamp)
                        putExtra("source", "SMS")
                    }
                    context.startForegroundService(serviceIntent)
                }
            }
        }
    }
}
