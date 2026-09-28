package com.asinosoft.cdm.ui.incall

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.ContactsContract
import android.telecom.Call
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class CallerInfo(val name: String?, val photoUri: String?, val contactId: String?)

/**
 * Contact lookup shared by the floating incoming window and the full call screen,
 * so the second one shows the name instantly and both match numbers in 8/+7 formats.
 */
internal object CallerLookup {
    private const val TTL_MS = 5 * 60_000L
    private val EMPTY = CallerInfo(null, null, null)
    private val cache = LruCache<String, Pair<Long, CallerInfo>>(16)

    fun cached(number: String): CallerInfo? {
        val key = cacheKey(number) ?: return null
        val (time, info) = synchronized(cache) { cache.get(key) } ?: return null
        return info.takeIf { SystemClock.elapsedRealtime() - time < TTL_MS }
    }

    suspend fun lookup(context: Context, number: String): CallerInfo = withContext(Dispatchers.IO) {
        val key = cacheKey(number) ?: return@withContext EMPTY
        cached(number)?.let { return@withContext it }
        val info = candidates(number).firstNotNullOfOrNull { query(context, it) } ?: EMPTY
        if (info.name != null) {
            synchronized(cache) { cache.put(key, SystemClock.elapsedRealtime() to info) }
        }
        info
    }

    /** Name Telecom already matched in the contacts for this call (Android 11+). */
    fun telecomContactName(call: Call?): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            call?.details?.contactDisplayName?.takeIf { it.isNotBlank() }
        } else null

    private fun query(context: Context, number: String): CallerInfo? = try {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(
            ContactsContract.PhoneLookup._ID,
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.PHOTO_URI,
            ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
        )
        context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            fun column(name: String) = c.getColumnIndex(name).takeIf { it != -1 }?.let { c.getString(it) }
            CallerInfo(
                name = column(ContactsContract.PhoneLookup.DISPLAY_NAME),
                photoUri = column(ContactsContract.PhoneLookup.PHOTO_URI)?.takeIf { it.isNotEmpty() }
                    ?: column(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
                contactId = column(ContactsContract.PhoneLookup._ID)
            )
        }
    } catch (_: Exception) {
        null
    }

    private fun cacheKey(number: String): String? =
        number.filter(Char::isDigit).takeLast(10).takeIf { it.isNotEmpty() }

    private fun candidates(number: String): List<String> {
        val raw = number.trim()
        val digits = raw.filter(Char::isDigit)
        val result = LinkedHashSet<String>()
        if (raw.isNotEmpty()) result += raw
        if (digits.isNotEmpty()) result += digits
        when {
            digits.length == 11 && digits.startsWith("8") -> {
                result += "+7${digits.drop(1)}"
                result += "7${digits.drop(1)}"
            }
            digits.length == 11 && digits.startsWith("7") -> {
                result += "8${digits.drop(1)}"
                result += "+$digits"
            }
            digits.length == 10 -> {
                result += "+7$digits"
                result += "8$digits"
            }
        }
        return result.toList()
    }
}
