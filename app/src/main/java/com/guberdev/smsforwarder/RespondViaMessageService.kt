package com.guberdev.smsforwarder

import android.app.Service
import android.content.Intent
import android.os.IBinder

// Required to be declared as a default SMS app candidate.
// Handles quick-reply intents but this app does not support sending SMS.
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
