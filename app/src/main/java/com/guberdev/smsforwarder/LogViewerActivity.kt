package com.guberdev.smsforwarder

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider

class LogViewerActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var scrollView: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Logs"

        tvLog = findViewById(R.id.tvLog)
        scrollView = findViewById(R.id.scrollView)

        findViewById<Button>(R.id.btnShare).setOnClickListener { shareLog() }
        findViewById<Button>(R.id.btnClear).setOnClickListener { confirmClear() }
        findViewById<Button>(R.id.btnRefresh).setOnClickListener { loadLog() }

        loadLog()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadLog() {
        val file = LogStore.getLogFile(this)
        val text = if (file.exists() && file.length() > 0) file.readText() else "(no logs yet)"
        tvLog.text = text
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun shareLog() {
        val file = LogStore.getLogFile(this)
        if (!file.exists() || file.length() == 0L) {
            Toast.makeText(this, "No logs to share", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SMS Sync logs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share logs"))
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Clear logs")
            .setMessage("Delete all log entries?")
            .setPositiveButton("Clear") { _, _ ->
                LogStore.clear(this)
                loadLog()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
