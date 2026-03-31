package com.guberdev.smsync

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
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
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001
    private val PERMISSION_REQUEST_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        btnSignIn = findViewById(R.id.btnSignIn)
        btnToggle = findViewById(R.id.btnToggleService)
        val btnBattery = findViewById<Button>(R.id.btnBattery)

        setupGoogleSignIn()

        btnSignIn.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
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
                Toast.makeText(this, "Successfully signed in", Toast.LENGTH_SHORT).show()
                updateUI()
            } else {
                Log.e("MainActivity", "Sign in failed: ${task.exception?.message}")
                Toast.makeText(this, "Sign in failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUI() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account != null) {
            btnSignIn.text = "Logged in as ${account.email}"
            btnSignIn.isEnabled = false
            btnToggle.isEnabled = true
            tvStatus.text = "Status: Ready to sync"
        } else {
            btnSignIn.text = "Sign in with Google"
            btnSignIn.isEnabled = true
            btnToggle.isEnabled = false
            tvStatus.text = "Status: Please sign in first"
        }
    }

    private fun toggleService() {
        val intent = Intent(this, SmsForwarderService::class.java)
        if (isServiceRunning()) {
            stopService(intent)
            tvStatus.text = "Status: Monitoring disabled"
            btnToggle.text = "Start Monitoring"
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            tvStatus.text = "Status: Monitoring active"
            btnToggle.text = "Stop Monitoring"
        }
    }

    private fun isServiceRunning(): Boolean {
        // Simple mock for this demonstration, in prod use a flag or ActivityManager
        return btnToggle.text == "Stop Monitoring"
    }

    private fun checkPermissions(): Boolean {
        val sms = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
        return sms && notification
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    private fun requestIgnoreBatteryOptimization() {
        val intent = Intent()
        val packageName = packageName
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } else {
                Toast.makeText(this, "Success: App is unrestricted", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
