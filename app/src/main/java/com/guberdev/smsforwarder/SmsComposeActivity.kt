package com.guberdev.smsforwarder

import android.app.Activity
import android.os.Bundle
import android.widget.Toast

// Required to be declared as a default SMS app candidate.
// Handles sms: / smsto: intents but this app does not support sending SMS.
class SmsComposeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Toast.makeText(this, "SMS sending is not supported in this app", Toast.LENGTH_SHORT).show()
        finish()
    }
}
