package com.asinosoft.dialer.data.repository

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import com.asinosoft.dialer.data.model.CallLogItem
import com.asinosoft.dialer.data.model.CallType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CallLogRepository(private val context: Context) {

    companion object {
        /** First paint window — keeps cold start fast; full list loads right after. */
        const val DEFAULT_RECENTS_LIMIT = 250
    }

    /** Normalized number (last 10 digits) → photo URI (only successful lookups) */
    private val photoCache = mutableMapOf<String, String>()

    /** Normalized name → photo URI */
    private val photoByNameCache = mutableMapOf<String, String>()

    /** Normalized number → display name from Contacts (overrides stale CallLog cache) */
    private val nameCache = mutableMapOf<String, String>()

    @Volatile
    private var cachedSubscriptions: List<SubscriptionInfo>? = null

    /**
     * Prefill photo cache from favorites (or other known contacts) so call-log rows
     * without CACHED_PHOTO_URI still show the same avatar.
     */
    fun seedPhotoCache(numberToPhoto: Map<String, String?>, nameToPhoto: Map<String, String?> = emptyMap()) {
        for ((number, uri) in numberToPhoto) {
            if (uri.isNullOrEmpty()) continue
            val key = photoCacheKey(number)
            if (key.isNotEmpty()) photoCache[key] = uri
        }
        for ((name, uri) in nameToPhoto) {
            if (uri.isNullOrEmpty()) continue
            val key = name.trim().lowercase()
            if (key.isNotEmpty()) photoByNameCache[key] = uri
        }
    }

    fun seedNameCache(numberToName: Map<String, String?>) {
        for ((number, name) in numberToName) {
            if (name.isNullOrBlank()) continue
            val key = photoCacheKey(number)
            if (key.isNotEmpty()) nameCache[key] = name
        }
    }

    fun clearNameCache() {
        nameCache.clear()
    }

    /**
     * @param limit max raw CallLog rows (newest first). Null = entire journal.
     */
    suspend fun getCallLogs(limit: Int? = DEFAULT_RECENTS_LIMIT): List<CallLogItem> =
        withContext(Dispatchers.IO) {
            val raw = queryCallLogs(
                selection = null,
                selectionArgs = null,
                limit = limit
            )
            val withNames = enrichNames(raw)
            val withPhotos = enrichPhotos(withNames)
            groupConsecutiveCallLogs(withPhotos)
        }

    /**
     * Full call history for a contact: all matching rows, no consecutive grouping.
     */
    suspend fun getCallLogsForNumbers(
        phoneNumbers: Collection<String>
    ): List<CallLogItem> = withContext(Dispatchers.IO) {
        val suffixes = phoneNumbers
            .map { digitsOnly(it) }
            .filter { it.length >= 7 }
            .map { it.takeLast(10) }
            .distinct()

        if (suffixes.isEmpty()) return@withContext emptyList()

        val selection = suffixes.joinToString(" OR ") { "${CallLog.Calls.NUMBER} LIKE ?" }
        val args = suffixes.map { "%$it" }.toTypedArray()

        val matched = queryCallLogs(
            selection = selection,
            selectionArgs = args,
            limit = null
        )

        val keys = suffixes.map { it.takeLast(7) }.toSet()
        matched.filter { item ->
            val d = digitsOnly(item.number)
            d.length >= 7 && d.takeLast(7) in keys
        }
    }

    private fun queryCallLogs(
        selection: String?,
        selectionArgs: Array<String>?,
        limit: Int?
    ): List<CallLogItem> {
        val rawCallLogs = mutableListOf<CallLogItem>()

        try {
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.CACHED_PHOTO_URI,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.PHONE_ACCOUNT_ID
            )

            val sortOrder = if (limit != null) {
                "${CallLog.Calls.DATE} DESC LIMIT $limit"
            } else {
                "${CallLog.Calls.DATE} DESC"
            }

            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use { c ->
                val idIndex = c.getColumnIndex(CallLog.Calls._ID)
                val numberIndex = c.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIndex = c.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val photoIndex = c.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)
                val typeIndex = c.getColumnIndex(CallLog.Calls.TYPE)
                val dateIndex = c.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = c.getColumnIndex(CallLog.Calls.DURATION)
                val accountIdIndex = c.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

                val subIdIndex = getColumnIndexSafe(c, "sub_id", "subscription_id")
                val simIdIndex = getColumnIndexSafe(c, "sim_id", "sim_slot")

                val subscriptions = getActiveSubscriptions()

                while (c.moveToNext()) {
                    val id = if (idIndex != -1) c.getString(idIndex) else ""
                    val number = if (numberIndex != -1) c.getString(numberIndex).orEmpty() else ""
                    val name = if (nameIndex != -1) c.getString(nameIndex) else null
                    val photoUri = if (photoIndex != -1) c.getString(photoIndex) else null
                    val accountId = if (accountIdIndex != -1) c.getString(accountIdIndex) else null
                    val subIdStr = if (subIdIndex != -1) c.getString(subIdIndex) else null
                    val simIdStr = if (simIdIndex != -1) c.getString(simIdIndex) else null

                    val simNumber = detectSimNumber(subscriptions, accountId, subIdStr, simIdStr)
                    val rawType =
                        if (typeIndex != -1) c.getInt(typeIndex) else CallLog.Calls.INCOMING_TYPE
                    val date = if (dateIndex != -1) c.getLong(dateIndex) else 0L
                    val duration = if (durationIndex != -1) c.getLong(durationIndex) else 0L

                    val type = when (rawType) {
                        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                        else -> CallType.INCOMING
                    }

                    rawCallLogs.add(
                        CallLogItem(
                            id = id,
                            number = number,
                            name = if (name.isNullOrEmpty()) null else name,
                            photoUri = photoUri,
                            type = type,
                            timestamp = date,
                            duration = duration,
                            simNumber = simNumber
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Permission not granted yet
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return rawCallLogs
    }

    /**
     * Prefer live Contacts display name over stale CallLog.CACHED_NAME.
     */
    private fun enrichNames(logs: List<CallLogItem>): List<CallLogItem> {
        if (logs.isEmpty()) return logs

        val numbersNeedingLookup = LinkedHashSet<String>()
        for (item in logs) {
            if (item.number.isBlank()) continue
            val key = photoCacheKey(item.number)
            if (key.isEmpty() || nameCache.containsKey(key)) continue
            numbersNeedingLookup.add(item.number)
        }

        for (number in numbersNeedingLookup) {
            val name = lookupContactDisplayName(number)
            if (!name.isNullOrBlank()) {
                val key = photoCacheKey(number)
                if (key.isNotEmpty()) nameCache[key] = name
            }
        }

        return logs.map { item ->
            val key = photoCacheKey(item.number)
            val live = if (key.isNotEmpty()) nameCache[key] else null
            if (!live.isNullOrBlank() && live != item.name) item.copy(name = live) else item
        }
    }

    fun resolveDisplayName(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        val key = photoCacheKey(phoneNumber)
        if (key.isNotEmpty()) nameCache[key]?.let { return it }
        val name = lookupContactDisplayName(phoneNumber) ?: return null
        if (key.isNotEmpty()) nameCache[key] = name
        return name
    }

    private fun lookupContactDisplayName(phoneNumber: String): String? {
        for (candidate in numberLookupCandidates(phoneNumber)) {
            try {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(candidate)
                )
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        val name = if (idx != -1) c.getString(idx) else null
                        if (!name.isNullOrBlank()) return name
                    }
                }
            } catch (_: Exception) {
                // try next candidate
            }
        }
        return null
    }

    /**
     * Fill missing photos via cache, PhoneLookup, digit match, then display name.
     */
    private fun enrichPhotos(logs: List<CallLogItem>): List<CallLogItem> {
        if (logs.isEmpty()) return logs

        // Seed from rows that already have a cached photo
        for (item in logs) {
            if (item.photoUri.isNullOrEmpty()) continue
            if (item.number.isNotBlank()) {
                val key = photoCacheKey(item.number)
                if (key.isNotEmpty()) photoCache[key] = item.photoUri
            }
            item.name?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { nameKey ->
                photoByNameCache[nameKey] = item.photoUri
            }
        }

        val numbersNeedingLookup = LinkedHashSet<String>()
        val namesNeedingLookup = LinkedHashSet<String>()
        for (item in logs) {
            if (!item.photoUri.isNullOrEmpty()) continue
            val key = photoCacheKey(item.number)
            if (key.isNotEmpty() && photoCache.containsKey(key)) continue
            if (item.number.isNotBlank()) numbersNeedingLookup.add(item.number)
            val nameKey = item.name?.trim()?.lowercase().orEmpty()
            if (nameKey.isNotEmpty() && !photoByNameCache.containsKey(nameKey)) {
                namesNeedingLookup.add(item.name!!.trim())
            }
        }

        for (number in numbersNeedingLookup) {
            val uri = lookupContactPhotoUri(number)
            if (!uri.isNullOrEmpty()) {
                val key = photoCacheKey(number)
                if (key.isNotEmpty()) photoCache[key] = uri
            }
        }

        for (name in namesNeedingLookup) {
            val uri = lookupContactPhotoByName(name)
            if (!uri.isNullOrEmpty()) {
                photoByNameCache[name.trim().lowercase()] = uri
            }
        }

        return logs.map { item ->
            if (!item.photoUri.isNullOrEmpty()) return@map item

            val byNumber = photoCacheKey(item.number).takeIf { it.isNotEmpty() }?.let { photoCache[it] }
            if (!byNumber.isNullOrEmpty()) return@map item.copy(photoUri = byNumber)

            val byName = item.name?.trim()?.lowercase()?.let { photoByNameCache[it] }
            if (!byName.isNullOrEmpty()) return@map item.copy(photoUri = byName)

            item
        }
    }

    private fun photoCacheKey(number: String): String {
        val digits = digitsOnly(number).filter { it.isDigit() }
        return if (digits.length >= 7) digits.takeLast(10) else digits
    }

    private fun lookupContactPhotoUri(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null

        // Try several number shapes — CallLog and Contacts often differ (+7 / 8 / local)
        val candidates = numberLookupCandidates(phoneNumber)
        for (candidate in candidates) {
            lookupPhotoViaPhoneLookup(candidate)?.let { return it }
        }
        return lookupPhotoViaPhoneTable(phoneNumber)
    }

    private fun numberLookupCandidates(phoneNumber: String): List<String> {
        val raw = phoneNumber.trim()
        val digits = raw.filter { it.isDigit() }
        val result = LinkedHashSet<String>()
        if (raw.isNotBlank()) result.add(raw)
        if (digits.isNotBlank()) result.add(digits)
        if (digits.length == 11 && digits.startsWith("8")) {
            result.add("7${digits.drop(1)}")
            result.add("+7${digits.drop(1)}")
        }
        if (digits.length == 11 && digits.startsWith("7")) {
            result.add("8${digits.drop(1)}")
            result.add("+$digits")
        }
        if (digits.length == 10) {
            result.add("7$digits")
            result.add("+7$digits")
            result.add("8$digits")
        }
        if (digits.length >= 7) {
            result.add(digits.takeLast(10))
        }
        return result.toList()
    }

    private fun lookupPhotoViaPhoneLookup(phoneNumber: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(
                ContactsContract.PhoneLookup.PHOTO_URI,
                ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val fullIndex = c.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                val thumbIndex = c.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)
                val full = if (fullIndex != -1) c.getString(fullIndex) else null
                when {
                    !full.isNullOrEmpty() -> full
                    thumbIndex != -1 -> c.getString(thumbIndex)?.takeIf { it.isNotEmpty() }
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Match by last 7 digits in Phone table — works when PhoneLookup format fails. */
    private fun lookupPhotoViaPhoneTable(phoneNumber: String): String? {
        val needle = phoneNumber.filter { it.isDigit() }.takeLast(7)
        if (needle.length < 7) return null
        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
                ),
                null,
                null,
                null
            )?.use { c ->
                val numberIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val thumbIdx =
                    c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
                while (c.moveToNext()) {
                    val num = if (numberIdx != -1) c.getString(numberIdx).orEmpty() else ""
                    val digits = num.filter { it.isDigit() }
                    if (digits.length >= 7 && digits.takeLast(7) == needle) {
                        val photo = if (photoIdx != -1) c.getString(photoIdx) else null
                        if (!photo.isNullOrEmpty()) return@use photo
                        val thumb = if (thumbIdx != -1) c.getString(thumbIdx) else null
                        if (!thumb.isNullOrEmpty()) return@use thumb
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun lookupContactPhotoByName(displayName: String): String? {
        if (displayName.isBlank()) return null
        return try {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts.PHOTO_URI,
                    ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
                ),
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} = ?",
                arrayOf(displayName),
                null
            )?.use { c ->
                if (!c.moveToFirst()) return@use null
                val fullIdx = c.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                val thumbIdx = c.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
                val full = if (fullIdx != -1) c.getString(fullIdx) else null
                when {
                    !full.isNullOrEmpty() -> full
                    thumbIdx != -1 -> c.getString(thumbIdx)?.takeIf { it.isNotEmpty() }
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun groupConsecutiveCallLogs(logs: List<CallLogItem>): List<CallLogItem> {
        if (logs.isEmpty()) return emptyList()

        val grouped = mutableListOf<CallLogItem>()
        var currentGroupItem: CallLogItem? = null
        var currentCount = 0
        var currentDigits = ""

        for (item in logs) {
            val itemDigits = digitsOnly(item.number)
            val isSameContact = currentGroupItem != null && (
                    (itemDigits.isNotEmpty() && itemDigits == currentDigits) ||
                            (item.name != null && item.name == currentGroupItem.name)
                    )

            if (isSameContact) {
                currentCount++
            } else {
                if (currentGroupItem != null) {
                    grouped.add(currentGroupItem.copy(count = currentCount))
                }
                currentGroupItem = item
                currentDigits = itemDigits
                currentCount = 1
            }
        }

        if (currentGroupItem != null) {
            grouped.add(currentGroupItem.copy(count = currentCount))
        }

        return grouped
    }

    private fun getColumnIndexSafe(cursor: Cursor, vararg columnNames: String): Int {
        for (col in columnNames) {
            val idx = cursor.getColumnIndex(col)
            if (idx != -1) return idx
        }
        return -1
    }

    @Suppress("MissingPermission")
    private fun getActiveSubscriptions(): List<SubscriptionInfo> {
        cachedSubscriptions?.let { return it }
        return try {
            val subManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val list = subManager?.activeSubscriptionInfoList.orEmpty()
            cachedSubscriptions = list
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun detectSimNumber(
        subscriptions: List<SubscriptionInfo>,
        accountHandleId: String?,
        subIdStr: String?,
        simIdStr: String?
    ): Int {
        try {
            if (subscriptions.isNotEmpty()) {
                for (info in subscriptions) {
                    val subId = info.subscriptionId.toString()
                    val slotIndex = info.simSlotIndex
                    val iccId = info.iccId ?: ""

                    if ((subIdStr != null && subIdStr == subId) ||
                        (simIdStr != null && simIdStr == subId) ||
                        (accountHandleId != null && accountHandleId == subId) ||
                        (!accountHandleId.isNullOrBlank() && iccId.isNotBlank() &&
                                accountHandleId.contains(iccId))
                    ) {
                        return slotIndex + 1
                    }

                    if (accountHandleId == slotIndex.toString() ||
                        subIdStr == slotIndex.toString() ||
                        simIdStr == slotIndex.toString()
                    ) {
                        return slotIndex + 1
                    }
                }
            }
        } catch (_: Exception) {
            // ignore
        }

        val combined =
            "${accountHandleId.orEmpty()} ${subIdStr.orEmpty()} ${simIdStr.orEmpty()}".lowercase()
                .trim()

        if (simIdStr == "1" || subIdStr == "1" || accountHandleId == "1" ||
            combined.contains("sim2") || combined.contains("sub2") || combined.contains("slot2") ||
            combined.endsWith("_2") || combined.endsWith(":1")
        ) {
            return 2
        }

        if (simIdStr == "0" || subIdStr == "0" || accountHandleId == "0" ||
            combined.contains("sim1") || combined.contains("sub1") || combined.contains("slot1") ||
            combined.endsWith("_1") || combined.endsWith(":0")
        ) {
            return 1
        }

        return 1
    }

    private fun digitsOnly(number: String): String {
        val sb = StringBuilder(number.length)
        for (c in number) {
            if (c.isDigit() || c == '+') sb.append(c)
        }
        return sb.toString()
    }
}
