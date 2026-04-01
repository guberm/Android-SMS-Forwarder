package com.guberdev.smsforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Required to be declared as a default SMS app candidate.
// WAP push messages (MMS notifications) are received here but not forwarded.
class WapPushReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Not processing WAP push / MMS
    }
}
