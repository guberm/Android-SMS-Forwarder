package com.guberdev.smsforwarder

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.sheets.v4.SheetsScopes

class MainActivity : AppCompatActivity() {

    private lateinit var btnToggle: Button
    private lateinit var btnSignIn: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvAccount: TextView
    private lateinit var statusDot: View
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001
    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvAccount = findViewById(R.id.tvAccount)
        statusDot = findViewById(R.id.statusDot)
        btnSignIn = findViewById(R.id.btnSignIn)
        btnToggle = findViewById(R.id.btnToggleService)
        val btnBattery = findViewById<Button>(R.id.btnBattery)

        setupGoogleSignIn()

        btnSignIn.setOnClickListener {
            startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
        }

        btnToggle.setOnClickListener {
            if (checkPermissions()) {
                toggleService()
            } else {
                requestPermissions()
            }
        }

        btnBattery.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }

        updateUI()
        autoStartIfReady()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SheetsScopes.SPREADSHEETS), Scope(DriveScopes.DRIVE_FILE), Scope(GmailScopes.GMAIL_SEND))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            if (task.isSuccessful) {
                Toast.makeText(this, "Signed in", Toast.LENGTH_SHORT).show()
                updateUI()
                autoStartIfReady()
            } else {
                Log.e("MainActivity", "Sign in failed: ${task.exception?.message}")
                Toast.makeText(this, "Sign in failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                autoStartIfReady()
            }
        }
    }

    private fun autoStartIfReady() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null && checkPermissions() && !isServiceRunning()) {
            startService()
        }
    }

    private fun startService() {
        val intent = Intent(this, SmsForwarderService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateUI()
    }

    private fun toggleService() {
        if (isServiceRunning()) {
            stopService(Intent(this, SmsForwarderService::class.java))
        } else {
            startService()
        }
        updateUI()
    }

    private fun updateUI() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        val running = isServiceRunning()

        if (account != null) {
            tvAccount.text = account.email
            btnSignIn.text = "Switch account"
            btnToggle.isEnabled = checkPermissions()
        } else {
            tvAccount.text = "Not signed in"
            btnSignIn.text = "Sign in with Google"
            btnToggle.isEnabled = false
        }

        if (running) {
            tvStatus.text = "Monitoring active"
            btnToggle.text = "Stop Monitoring"
            btnToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFF44336.toInt())
            setDotColor(0xFF4CAF50.toInt())
        } else if (account != null) {
            tvStatus.text = if (checkPermissions()) "Ready — tap Start" else "Missing permissions"
            btnToggle.text = "Start Monitoring"
            btnToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF4CAF50.toInt())
            setDotColor(0xFFFF9800.toInt())
        } else {
            tvStatus.text = "Sign in to get started"
            setDotColor(0xFF9E9E9E.toInt())
        }
    }

    private fun setDotColor(color: Int) {
        val drawable = statusDot.background?.mutate() as? GradientDrawable
        drawable?.setColor(color)
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == SmsForwarderService::class.java.name }
    }

    private fun checkPermissions(): Boolean {
        val sms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val readSms = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        return sms && readSms && notification
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    private fun requestIgnoreBatteryOptimization() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } else {
                Toast.makeText(this, "Already unrestricted", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
