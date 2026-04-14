package com.guberdev.smsforwarder

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider

class LogViewerActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var etFilter: EditText
    private lateinit var btnClearFilter: Button
    private var fullLogText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Logs"

        tvLog = findViewById(R.id.tvLog)
        scrollView = findViewById(R.id.scrollView)
        etFilter = findViewById(R.id.etFilter)
        btnClearFilter = findViewById(R.id.btnClearFilter)

        etFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                btnClearFilter.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                applyFilter()
            }
        })

        btnClearFilter.setOnClickListener {
            etFilter.setText("")
        }

        // Quick filter chips
        val chips = mapOf(
            R.id.chipAll   to "",
            R.id.chipMsg   to "MSG",
            R.id.chipOk    to "OK",
            R.id.chipSkip  to "SKIP",
            R.id.chipErr   to "ERROR",
            R.id.chipDedup to "Dedup"
        )
        chips.forEach { (id, keyword) ->
            findViewById<Button>(id).setOnClickListener { etFilter.setText(keyword) }
        }

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
        fullLogText = if (file.exists() && file.length() > 0) file.readText() else "(no logs yet)"
        applyFilter()
    }

    private fun applyFilter() {
        val query = etFilter.text.toString().trim()
        val displayed = if (query.isEmpty()) {
            fullLogText
        } else {
            fullLogText.lines()
                .filter { it.contains(query, ignoreCase = true) }
                .joinToString("\n")
        }
        tvLog.text = displayed
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
            putExtra(Intent.EXTRA_SUBJECT, "Notifications Forwarder logs")
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
