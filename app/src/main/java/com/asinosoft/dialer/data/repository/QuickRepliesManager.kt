package com.asinosoft.dialer.data.repository

import android.content.Context
import androidx.core.content.edit
import com.asinosoft.dialer.R
import org.json.JSONArray

object QuickRepliesManager {
    private const val PREFS_NAME = "quick_replies_settings"
    private const val KEY_REPLIES = "quick_replies_list"

    fun defaultReplies(context: Context): List<String> = listOf(
        context.getString(R.string.quick_reply_default_1),
        context.getString(R.string.quick_reply_default_2),
        context.getString(R.string.quick_reply_default_3)
    )

    fun getQuickReplies(context: Context): List<String> {
        val defaults = defaultReplies(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_REPLIES, null)
        if (jsonStr.isNullOrEmpty()) {
            saveQuickReplies(context, defaults)
            return defaults
        }
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val str = arr.getString(i)
                if (str.isNotBlank()) list.add(str)
            }
            if (list.isEmpty()) defaults else list
        } catch (_: Exception) {
            defaults
        }
    }

    fun saveQuickReplies(context: Context, list: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit { putString(KEY_REPLIES, arr.toString()) }
    }
}
