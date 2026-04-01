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
            <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; max-width: 600px; margin: 20px auto; border: 1px solid #e0e0e0; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.08); overflow: hidden; background-color: #ffffff;">
                <div style="background-color: #1a73e8; color: white; padding: 24px; text-align: center;">
                    <h2 style="margin: 0; font-size: 24px; letter-spacing: 0.5px; font-weight: 600;">New Message Sync</h2>
                </div>
                <div style="padding: 32px;">
                    <div style="margin-bottom: 24px;">
                        <span style="display: inline-block; padding: 6px 16px; background-color: #e8f0fe; color: #1a73e8; border-radius: 20px; font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: 1px;">$source</span>
                    </div>
                    
                    <div style="margin-bottom: 24px;">
                        <div style="margin-bottom: 8px;">
                            <span style="color: #5f6368; font-size: 13px; font-weight: 600;">FROM</span><br>
                            <span style="color: #202124; font-size: 16px;">$sender</span>
                        </div>
                        <div>
                            <span style="color: #5f6368; font-size: 13px; font-weight: 600;">RECEIVED AT</span><br>
                            <span style="color: #202124; font-size: 16px;">$timestamp</span>
                        </div>
                    </div>

                    <div style="padding: 24px; background-color: #f8f9fa; border-radius: 10px; border-left: 5px solid #1a73e8;">
                        <p style="margin: 0; color: #202124; font-size: 17px; line-height: 1.6; white-space: pre-wrap;">$body</p>
                    </div>
                </div>
                <div style="background-color: #f8f9fa; padding: 16px; text-align: center; color: #70757a; font-size: 12px; border-top: 1px solid #f1f3f4;">
                    Automated Secure Forwarding &bull; Sync SMS
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
