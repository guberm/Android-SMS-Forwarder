package com.guberdev.smsforwarder

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object LogStore {
    private const val LOG_FILE = "sms_sync.log"
    private const val MAX_SIZE_BYTES = 512 * 1024 // 512 KB — trim to half when exceeded
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
    private val lock = Any()

    fun log(context: Context, tag: String, message: String) {
        Log.d(tag, message)
        val entry = "[${fmt.format(Date())}] $tag: $message\n"
        synchronized(lock) {
            val file = getLogFile(context)
            file.appendText(entry)
            if (file.length() > MAX_SIZE_BYTES) trim(file)
        }
    }

    fun e(context: Context, tag: String, message: String) {
        Log.e(tag, message)
        val entry = "[${fmt.format(Date())}] ERROR/$tag: $message\n"
        synchronized(lock) {
            val file = getLogFile(context)
            file.appendText(entry)
            if (file.length() > MAX_SIZE_BYTES) trim(file)
        }
    }

    fun getLogFile(context: Context): File = File(context.applicationContext.filesDir, LOG_FILE)

    fun clear(context: Context) {
        synchronized(lock) { getLogFile(context).writeText("") }
    }

    private fun trim(file: File) {
        val lines = file.readLines()
        file.writeText(lines.drop(lines.size / 2).joinToString("\n") + "\n")
    }
}
