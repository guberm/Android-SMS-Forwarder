package com.example.smsforwarder

import android.util.Log
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object NetworkClient {

    private const val TAG = "NetworkClient"

    fun sendSms(targetUrl: String, sender: String, message: String, timestamp: Long) {
        try {
            val url = URL(targetUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")

            val json = JSONObject().apply {
                put("sender", sender)
                put("message", message)
                put("timestamp", timestamp)
            }

            val writer = OutputStreamWriter(conn.outputStream)
            writer.write(json.toString())
            writer.flush()
            writer.close()

            val responseCode = conn.responseCode
            Log.d(TAG, "Sent SMS to server. Response code: $responseCode")
            
            // Note: Google Apps Script usually returns a 302 redirect for Web Apps.
            // But we don't necessarily need to follow it to just log the data.
            // If response is 200-299, it's fine.

            conn.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS: ${e.message}")
        }
    }
}
