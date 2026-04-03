package com.guberdev.smsforwarder

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

data class AppSourceItem(
    val key: String,
    val displayName: String,
    val icon: Drawable?,
    var enabled: Boolean
)

class SourceSettingsActivity : AppCompatActivity() {

    private val items = mutableListOf<AppSourceItem>()
    private lateinit var adapter: SourceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_source_settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Message Sources"

        val enabledSources = SourcePrefs.getEnabledSources(this)
        val pm = packageManager

        items.add(
            AppSourceItem(
                key = SourcePrefs.NATIVE_SMS,
                displayName = "Native SMS",
                icon = ContextCompat.getDrawable(this, android.R.drawable.ic_dialog_email),
                enabled = enabledSources.contains(SourcePrefs.NATIVE_SMS)
            )
        )

        for (pkg in SourcePrefs.KNOWN_PACKAGES) {
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                items.add(
                    AppSourceItem(
                        key = pkg,
                        displayName = pm.getApplicationLabel(info).toString(),
                        icon = pm.getApplicationIcon(pkg),
                        enabled = enabledSources.contains(pkg)
                    )
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // Not installed — skip
            }
        }

        adapter = SourceAdapter(this, items)
        findViewById<ListView>(R.id.lvSources).adapter = adapter
    }

    override fun onPause() {
        super.onPause()
        SourcePrefs.setEnabledSources(this, items.filter { it.enabled }.map { it.key }.toSet())
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

class SourceAdapter(
    context: Context,
    private val items: MutableList<AppSourceItem>
) : ArrayAdapter<AppSourceItem>(context, 0, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_source, parent, false)

        val item = items[position]
        view.findViewById<ImageView>(R.id.ivAppIcon).setImageDrawable(item.icon)
        view.findViewById<TextView>(R.id.tvAppName).text = item.displayName
        view.findViewById<TextView>(R.id.tvPackageName).text = item.key

        val switch = view.findViewById<Switch>(R.id.switchEnabled)
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = item.enabled
        switch.setOnCheckedChangeListener { _, checked -> item.enabled = checked }

        return view
    }
}
