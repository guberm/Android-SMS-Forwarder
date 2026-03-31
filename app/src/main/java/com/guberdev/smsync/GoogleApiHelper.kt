package com.guberdev.smsync

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.Message
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.ValueRange
import java.util.*
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import android.util.Base64
import java.io.ByteArrayOutputStream

class GoogleApiHelper(private val context: Context, private val credential: GoogleAccountCredential) {

    private val transport = NetHttpTransport()
    private val jsonFactory = GsonFactory.getDefaultInstance()
    private val appName = "Sync SMS"

    private val driveService: Drive = Drive.Builder(transport, jsonFactory, credential)
        .setApplicationName(appName)
        .build()

    private val sheetsService: Sheets = Sheets.Builder(transport, jsonFactory, credential)
        .setApplicationName(appName)
        .build()

    private val gmailService: Gmail = Gmail.Builder(transport, jsonFactory, credential)
        .setApplicationName(appName)
        .build()

    /**
     * Finds or creates a spreadsheet named "SMS Forwarder Logs"
     */
    fun findOrCreateSpreadsheet(): String? {
        val query = "name = 'SMS Forwarder Logs' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
        val result = driveService.files().list().setQ(query).setSpaces("drive").execute()
        val files = result.files

        if (!files.isNullOrEmpty()) {
            return files[0].id
        }

        // Create new spreadsheet
        val metadata = File().apply {
            name = "SMS Forwarder Logs"
            mimeType = "application/vnd.google-apps.spreadsheet"
        }
        val newFile = driveService.files().create(metadata).setFields("id").execute()
        
        // Initialize header
        val headerValues = listOf(listOf("Timestamp", "Sender", "Message"))
        val body = ValueRange().setValues(headerValues)
        sheetsService.spreadsheets().values()
            .update(newFile.id, "A1", body)
            .setValueInputOption("RAW")
            .execute()

        return newFile.id
    }

    /**
     * Appends a row to the specified spreadsheet
     */
    fun appendRow(spreadsheetId: String, timestamp: String, sender: String, message: String) {
        val values = listOf(listOf(timestamp, sender, message))
        val body = ValueRange().setValues(values)
        
        sheetsService.spreadsheets().values()
            .append(spreadsheetId, "A1", body)
            .setValueInputOption("USER_ENTERED")
            .execute()
    }

    /**
     * Sends a Gmail message to self
     */
    fun sendEmailToSelf(sender: String, timestamp: String, body: String) {
        val userEmail = credential.selectedAccountName ?: return
        val subject = "New SMS from $sender"
        val content = "From: $sender\nTime: $timestamp\n\n$body"
        
        val mimeMessage = MimeMessage(Session.getDefaultInstance(Properties(), null)).apply {
            setFrom(InternetAddress(userEmail))
            addRecipient(javax.mail.Message.RecipientType.TO, InternetAddress(userEmail))
            setSubject(subject)
            setText(content)
        }

        val buffer = ByteArrayOutputStream()
        mimeMessage.writeTo(buffer)
        val encodedEmail = Base64.encodeToString(buffer.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
        
        val message = Message().setRaw(encodedEmail)
        gmailService.users().messages().send("me", message).execute()
    }
}
