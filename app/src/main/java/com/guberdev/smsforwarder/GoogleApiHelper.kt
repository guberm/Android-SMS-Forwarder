package com.guberdev.smsforwarder

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
    private val appName = "SMS Forwarder"

    private val driveService: Drive = Drive.Builder(transport, jsonFactory, credential)
        .setApplicationName(appName)
        .build()

    private val sheetsService: Sheets = Sheets.Builder(transport, jsonFactory, credential)
        .setApplicationName(appName)
        .build()

    private val gmailService: Gmail = Gmail.Builder(transport, jsonFactory, credential)
        .setApplicationName(appName)
        .build()

    fun findOrCreateSpreadsheet(): String? {
        val query = "name = 'SMS Forwarder Logs' and mimeType = 'application/vnd.google-apps.spreadsheet' and trashed = false"
        val result = driveService.files().list().setQ(query).setSpaces("drive").execute()
        val files = result.files

        if (!files.isNullOrEmpty()) {
            return files[0].id
        }

        val metadata = File().apply {
            name = "SMS Forwarder Logs"
            mimeType = "application/vnd.google-apps.spreadsheet"
        }
        val newFile = driveService.files().create(metadata).setFields("id").execute()
        
        val headerValues = listOf(listOf("Timestamp", "Source", "Sender", "Message"))
        val body = ValueRange().setValues(headerValues)
        sheetsService.spreadsheets().values()
            .update(newFile.id, "A1", body)
            .setValueInputOption("RAW")
            .execute()

        return newFile.id
    }

    fun appendRow(spreadsheetId: String, timestamp: String, source: String, sender: String, message: String) {
        val values = listOf(listOf(timestamp, source, sender, message))
        val body = ValueRange().setValues(values)
        
        sheetsService.spreadsheets().values()
            .append(spreadsheetId, "A1", body)
            .setValueInputOption("USER_ENTERED")
            .execute()
    }

    fun sendEmailToSelf(source: String, sender: String, timestamp: String, body: String) {
        val userEmail = credential.selectedAccountName ?: return
        val subject = "[$source] New message from $sender"
        
        val htmlContent = """
            <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 600px; margin: 20px auto; border: 1px solid #e0e0e0; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.08); overflow: hidden;">
                <div style="background-color: #1a73e8; color: white; padding: 20px; text-align: center;">
                    <h2 style="margin: 0; font-size: 22px; letter-spacing: 0.5px;">New Message Sync</h2>
                </div>
                <div style="padding: 24px; background-color: #ffffff;">
                    <div style="margin-bottom: 20px;">
                        <span style="display: inline-block; padding: 4px 12px; background-color: #e8f0fe; color: #1a73e8; border-radius: 16px; font-size: 13px; font-weight: 600; text-transform: uppercase;">$source</span>
                    </div>
                    <div style="display: flex; flex-direction: column; gap: 8px;">
                        <p style="margin: 0; color: #5f6368; font-size: 14px;"><strong>From:</strong> <span style="color: #202124;">$sender</span></p>
                        <p style="margin: 0; color: #5f6368; font-size: 14px;"><strong>Time:</strong> <span style="color: #202124;">$timestamp</span></p>
                    </div>
                    <hr style="border: 0; border-top: 1px solid #f1f3f4; margin: 24px 0;">
                    <div style="padding: 20px; background-color: #f8f9fa; border-radius: 8px; border-left: 4px solid #1a73e8;">
                        <p style="margin: 0; color: #202124; font-size: 16px; line-height: 1.6; white-space: pre-wrap;">$body</p>
                    </div>
                </div>
                <div style="background-color: #f1f3f4; padding: 12px; text-align: center; color: #70757a; font-size: 12px;">
                    This notification was automatically forwarded from your phone.
                </div>
            </div>
        """.trimIndent()
        
        val mimeMessage = MimeMessage(Session.getDefaultInstance(Properties(), null)).apply {
            setFrom(InternetAddress(userEmail))
            addRecipient(javax.mail.Message.RecipientType.TO, InternetAddress(userEmail))
            setSubject(subject)
            setContent(htmlContent, "text/html; charset=utf-8")
        }

        val buffer = ByteArrayOutputStream()
        mimeMessage.writeTo(buffer)
        val encodedEmail = Base64.encodeToString(buffer.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
        
        val message = Message().setRaw(encodedEmail)
        gmailService.users().messages().send("me", message).execute()
    }
}
