package com.asinosoft.dialer.data.repository

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray

object QuickRepliesManager {
    private const val PREFS_NAME = "quick_replies_settings"
    private const val KEY_REPLIES = "quick_replies_list"

    val DEFAULT_REPLIES = listOf(
        "Напишите мне.",
        "Вы можете перезвонить позже?",
        "Я перезвоню."
    )

    fun getQuickReplies(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_REPLIES, null)
        if (jsonStr.isNullOrEmpty()) {
            saveQuickReplies(context, DEFAULT_REPLIES)
            return DEFAULT_REPLIES
        }
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val str = arr.getString(i)
                if (str.isNotBlank()) list.add(str)
            }
            if (list.isEmpty()) DEFAULT_REPLIES else list
        } catch (_: Exception) {
            DEFAULT_REPLIES
        }
    }

    fun saveQuickReplies(context: Context, list: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit { putString(KEY_REPLIES, arr.toString()) }
    }
}
