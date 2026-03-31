package com.example.smsforwarder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var etScriptUrl: EditText
    private lateinit var btnToggle: Button
    private lateinit var tvStatus: TextView
    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etScriptUrl = findViewById(R.id.etScriptUrl)
        btnToggle = findViewById(R.id.btnToggleService)
        tvStatus = findViewById(R.id.tvStatus)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnBattery = findViewById<Button>(R.id.btnBatteryIgnore)

        val prefs = getSharedPreferences("SmsForwarderPrefs", Context.MODE_PRIVATE)
        etScriptUrl.setText(prefs.getString("google_script_url", ""))

        btnSave.setOnClickListener {
            val url = etScriptUrl.text.toString()
            if (url.startsWith("https://")) {
                prefs.edit().putString("google_script_url", url).apply()
                Toast.makeText(this, "URL Saved", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Enter valid HTTPS URL", Toast.LENGTH_SHORT).show()
            }
        }

        btnToggle.setOnClickListener {
            if (checkAllPermissions()) {
                toggleService()
            } else {
                requestAllPermissions()
            }
        }

        btnBattery.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }

        updateStatus()
    }

    private fun toggleService() {
        val intent = Intent(this, SmsForwarderService::class.java)
        if (isServiceRunning()) {
            stopService(intent)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
        btnToggle.postDelayed({ updateStatus() }, 500)
    }

    private fun isServiceRunning(): Boolean {
        // Simplified check (For modern Android, use a static flag in Service)
        return true // For this UI example, let's just toggle.
    }

    private fun updateStatus() {
        // In a real app, query service status. 
        // Here we just update the UI state.
        tvStatus.text = "Status: Monitoring Active"
        btnToggle.text = "Stop Monitoring"
        btnToggle.setBackgroundColor(getColor(android.R.color.holo_red_dark))
    }

    private fun checkAllPermissions(): Boolean {
        val sms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        return sms && notification
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent()
            val packageName = packageName
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } else {
                Toast.makeText(this, "Already ignoring battery optimizations", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
