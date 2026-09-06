package com.asinosoft.dialer.data.repository

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.edit
import androidx.core.net.toUri
import org.json.JSONArray
import org.json.JSONObject

object ContactRingtoneManager {
    private const val PREFS_NAME = "contact_custom_ringtones"
    private const val KEY_PREFIX = "ringtone_"

    fun getContactRingtone(context: Context, contactKey: String): Pair<String?, String?> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriStr = prefs.getString("${KEY_PREFIX}${contactKey}_uri", null)
        val title = prefs.getString("${KEY_PREFIX}${contactKey}_title", null)
        return Pair(uriStr, title)
    }

    fun setContactRingtone(context: Context, contactKey: String, uriStr: String?, title: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            if (uriStr.isNullOrBlank()) {
                remove("${KEY_PREFIX}${contactKey}_uri")
                remove("${KEY_PREFIX}${contactKey}_title")
            } else {
                putString("${KEY_PREFIX}${contactKey}_uri", uriStr)
                putString("${KEY_PREFIX}${contactKey}_title", title.orEmpty())
            }
        }
    }

    fun getSavedCustomRingtones(context: Context): List<Pair<String, String>> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("user_added_ringtones_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<Pair<String, String>>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val uri = obj.optString("uri")
                val title = obj.optString("title")
                if (uri.isNotBlank() && title.isNotBlank()) {
                    list.add(Pair(uri, title))
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCustomRingtone(context: Context, uriStr: String, title: String) {
        val current = getSavedCustomRingtones(context).filter { it.first != uriStr }.toMutableList()
        current.add(0, Pair(uriStr, title))
        val arr = JSONArray()
        current.forEach { (uri, name) ->
            val obj = JSONObject().apply {
                put("uri", uri)
                put("title", name)
            }
            arr.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putString("user_added_ringtones_json", arr.toString()) }
    }

    fun getRingtoneTitle(context: Context, uriString: String?): String {
        if (uriString.isNullOrBlank()) return "По умолчанию"
        // Check user added ringtones first
        val userRingtones = getSavedCustomRingtones(context)
        val foundUser = userRingtones.find { it.first == uriString }
        if (foundUser != null) return foundUser.second

        return try {
            val ringtone = RingtoneManager.getRingtone(context, uriString.toUri())
            ringtone?.getTitle(context) ?: "По умолчанию"
        } catch (_: Exception) {
            "По умолчанию"
        }
    }

    fun getCustomRingtoneForNumber(context: Context, rawNumber: String): String? {
        if (rawNumber.isBlank()) return null
        val clean = rawNumber.replace(Regex("[^0-9+]"), "")
        val digits = rawNumber.filter { it.isDigit() }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Match by clean number (+7...)
        val byClean = prefs.getString("${KEY_PREFIX}${clean}_uri", null)
        if (!byClean.isNullOrBlank()) return byClean

        // Match by last 10 digits
        if (digits.length >= 10) {
            val last10 = digits.takeLast(10)
            val by10 = prefs.getString("${KEY_PREFIX}${last10}_uri", null)
            if (!by10.isNullOrBlank()) return by10
        }

        // Match by system contact lookup id
        try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(rawNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup._ID)
            context.contentResolver.query(lookupUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getString(0)
                    if (!id.isNullOrBlank()) {
                        val byId = prefs.getString("${KEY_PREFIX}${id}_uri", null)
                        if (!byId.isNullOrBlank()) return byId
                    }
                }
            }
        } catch (_: Exception) {
            // ignore
        }

        return null
    }
}
