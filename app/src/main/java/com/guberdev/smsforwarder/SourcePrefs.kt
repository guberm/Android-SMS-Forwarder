package com.guberdev.smsforwarder

import android.content.Context

object SourcePrefs {
    private const val PREFS_NAME = "source_prefs"
    private const val KEY_ENABLED = "enabled_sources"
    private const val KEY_INITIALIZED = "initialized"

    const val NATIVE_SMS = "native_sms"

    val KNOWN_PACKAGES = listOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.sec.android.app.messaging",
        "com.android.mms",
        "com.oneplus.mms",
        "com.miui.sms",
        "com.huawei.mms",
        "com.coloros.mms",
        "com.lg.message",
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.microsoft.teams",
        "com.slack",
        "com.viber.voip",
        "org.telegram.messenger",
        "com.skype.raider",
        "com.facebook.orca",
        "com.tencent.mm",
        "jp.naver.line.android",
        "com.discord",
        "com.pushbullet.android"
    )

    /** Returns true if the given source (package name or NATIVE_SMS) is enabled. Default: true. */
    fun isEnabled(context: Context, sourceKey: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) return true
        return prefs.getStringSet(KEY_ENABLED, null)?.contains(sourceKey) ?: true
    }

    fun getEnabledSources(context: Context): MutableSet<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
            return (KNOWN_PACKAGES + NATIVE_SMS).toMutableSet()
        }
        return prefs.getStringSet(KEY_ENABLED, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
    }

    fun setEnabledSources(context: Context, enabled: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_INITIALIZED, true)
            .putStringSet(KEY_ENABLED, enabled)
            .apply()
    }
}
