package com.asinosoft.cdm.ui.incall

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toUri
import com.asinosoft.cdm.data.repository.ProfileCardRepository
import android.os.SystemClock
import android.provider.ContactsContract
import android.telecom.Call
import android.os.Build
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

internal data class CallerInfo(val name: String?, val photoUri: String?, val contactId: String?)

/**
 * Contact lookup shared by the floating incoming window and the full call screen,
 * so the second one shows the name instantly and both match numbers in 8/+7 formats.
 */
internal object CallerLookup {
    private const val TTL_MS = 5 * 60_000L
    private const val AVATAR_MAX_SIDE = 512
    private val EMPTY = CallerInfo(null, null, null)
    private const val TAG = "CallerLookup"
    private val cache = LruCache<String, Pair<Long, CallerInfo>>(16)
    private val avatarCache = LruCache<String, Pair<Long, Bitmap>>(8)
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    fun cachedAvatar(number: String): Bitmap? {
        val key = cacheKey(number) ?: return null
        val (time, bitmap) = synchronized(avatarCache) { avatarCache.get(key) } ?: return null
        return bitmap.takeIf { SystemClock.elapsedRealtime() - time < TTL_MS }
    }

    /** Starts the contact + photo lookup as soon as a call arrives, before any call window is composed. */
    fun prefetch(context: Context, number: String) {
        if (number.isBlank()) return
        val app = context.applicationContext
        prefetchScope.launch {
            val info = lookup(app, number)
            loadAvatar(app, info.photoUri, info.contactId, number)
        }
    }

    /** Contact photo, or the in-app call background (first frame for a video) when the contact has none. */
    suspend fun loadAvatar(
        context: Context,
        photoUri: String?,
        contactId: String?,
        number: String
    ): Bitmap? = withContext(Dispatchers.IO) {
        cachedAvatar(number)?.let { return@withContext it }
        val started = SystemClock.elapsedRealtime()
        val resolver = context.contentResolver
        val contactUri = contactId?.toLongOrNull()
            ?.let { ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, it) }
        var source = "none"
        fun decode(name: String, open: () -> InputStream?): Bitmap? = try {
            open()?.use { BitmapFactory.decodeStream(it) }?.also { source = name }
        } catch (e: Exception) {
            Log.w(TAG, "Avatar $name failed for contact $contactId", e)
            null
        }

        val bitmap = photoUri?.takeIf { it.isNotEmpty() }?.let { uri -> decode("photoUri") { resolver.openInputStream(uri.toUri()) } }
            ?: contactUri?.let { decode("highres") { ContactsContract.Contacts.openContactPhotoInputStream(resolver, it, true) } }
            ?: contactUri?.let { decode("thumb") { ContactsContract.Contacts.openContactPhotoInputStream(resolver, it, false) } }
            ?: ProfileCardRepository.find(context, contactId, listOf(number))
                ?.let { ProfileCardRepository.loadBitmap(it, AVATAR_MAX_SIDE) }
                ?.also { source = "profileCard" }

        Log.d(TAG, "Avatar for contact $contactId: $source in ${SystemClock.elapsedRealtime() - started} ms")
        val key = cacheKey(number)
        if (bitmap != null && key != null) {
            synchronized(avatarCache) { avatarCache.put(key, SystemClock.elapsedRealtime() to bitmap) }
        }
        bitmap
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
            fun column(name: String) = c.getColumnIndex(name).takeIf { it != -1 }?.let { c.getString(it) }
            // The same number may belong to several contacts (duplicates) in no fixed order: prefer one with a photo.
            var first: CallerInfo? = null
            while (c.moveToNext()) {
                val info = CallerInfo(
                    name = column(ContactsContract.PhoneLookup.DISPLAY_NAME),
                    photoUri = column(ContactsContract.PhoneLookup.PHOTO_URI)?.takeIf { it.isNotEmpty() }
                        ?: column(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)?.takeIf { it.isNotEmpty() },
                    contactId = column(ContactsContract.PhoneLookup._ID)
                )
                if (info.photoUri != null) return@use info
                if (first == null) first = info
            }
            first
        }
    } catch (e: Exception) {
        Log.w(TAG, "PhoneLookup failed", e)
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
        when (digits.length) {
            11 if digits.startsWith("8") -> {
                result += "+7${digits.drop(1)}"
                result += "7${digits.drop(1)}"
            }
            11 if digits.startsWith("7") -> {
                result += "8${digits.drop(1)}"
                result += "+$digits"
            }
            10 -> {
                result += "+7$digits"
                result += "8$digits"
            }
        }
        return result.toList()
    }
}
