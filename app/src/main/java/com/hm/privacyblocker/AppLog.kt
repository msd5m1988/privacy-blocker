package com.hm.privacyblocker

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 简单日志：内存 + SharedPreferences 持久化，界面上直接看。 */
object AppLog {
    private const val PREF = "privacy_blocker_log"
    private const val KEY = "lines"
    private const val MAX = 200

    fun add(context: Context, line: String) {
        val time = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())
        val entry = "$time  $line"
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val old = prefs.getString(KEY, "") ?: ""
        val lines = (listOf(entry) + old.lines()).filter { it.isNotBlank() }.take(MAX)
        prefs.edit().putString(KEY, lines.joinToString("\n")).apply()
    }

    fun all(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return (prefs.getString(KEY, "") ?: "").lines().filter { it.isNotBlank() }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
