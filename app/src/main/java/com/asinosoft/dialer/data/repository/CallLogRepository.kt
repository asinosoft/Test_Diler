package com.asinosoft.dialer.data.repository

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
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

    fun resetCache() {
        nameCache.clear()
        photoCache.clear()
        photoByNameCache.clear()
    }

    /**
     * Seed `photoCache`, `photoByNameCache` and `nameCache` from all contacts on the device.
     * This performs a read of the Contacts Phone table and populates caches for faster
     * call-log rendering. Call from a background thread or use the async wrapper.
     */
    suspend fun seedCachesFromContacts() = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            )

            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { c ->
                val numIdx = c.getColumnIndex(projection[0])
                val photoIdx = c.getColumnIndex(projection[1])
                val nameIdx = c.getColumnIndex(projection[2])

                while (c.moveToNext()) {
                    val number = if (numIdx != -1) c.getString(numIdx).orEmpty() else ""
                    val photo = if (photoIdx != -1) c.getString(photoIdx) else null
                    val name = if (nameIdx != -1) c.getString(nameIdx) else null

                    val key = photoCacheKey(number)
                    if (key.isNotEmpty()) {
                        if (!photo.isNullOrEmpty() && !photoCache.containsKey(key)) {
                            photoCache[key] = photo
                        }
                        if (!name.isNullOrBlank() && !nameCache.containsKey(key)) {
                            nameCache[key] = name
                        }
                    }

                    if (!name.isNullOrBlank() && !photo.isNullOrEmpty()) {
                        val nameKey = name.trim().lowercase()
                        if (nameKey.isNotEmpty() && !photoByNameCache.containsKey(nameKey)) {
                            photoByNameCache[nameKey] = photo
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * @param limit max raw CallLog rows (newest first). Null = entire journal.
     */
    suspend fun getCallLogs(limit: Int? = DEFAULT_RECENTS_LIMIT): List<CallLogItem> =
        withContext(Dispatchers.IO) {
            if (photoCache.isEmpty() and nameCache.isEmpty()) {
                seedCachesFromContacts()
            }

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

            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val args = Bundle().apply {
                    putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                    putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(CallLog.Calls.DATE))
                    putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
                    limit?.let { putInt(ContentResolver.QUERY_ARG_LIMIT, it) }
                }

                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    args,
                    null
                )
            } else {
                val sortOrder = if (limit != null) {
                    "${CallLog.Calls.DATE} DESC LIMIT $limit"
                } else {
                    "${CallLog.Calls.DATE} DESC"
                }

                context.contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )
            }

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

    suspend fun deleteCallLogEntries(ids: Collection<String>): Int = withContext(Dispatchers.IO) {
        deleteIdsInternal(ids.mapNotNull { it.toLongOrNull() })
    }

    /**
     * Deletes all CallLog rows for a phone number.
     * Matches by normalized digits (handles +7 / spaces / dashes in stored NUMBER).
     */
    suspend fun deleteCallLogsForNumber(phoneNumber: String): Int = withContext(Dispatchers.IO) {
        val ids = findCallLogIdsForNumber(phoneNumber, limit = null)
        if (ids.isEmpty()) return@withContext 0
        deleteIdsInternal(ids)
    }

    /** Deletes up to [limit] newest CallLog rows matching [phoneNumber]. */
    suspend fun deleteNewestCallLogsForNumber(phoneNumber: String, limit: Int): Int =
        withContext(Dispatchers.IO) {
            if (limit <= 0) return@withContext 0
            val ids = findCallLogIdsForNumber(phoneNumber, limit = limit)
            if (ids.isEmpty()) return@withContext 0
            deleteIdsInternal(ids)
        }

    /** How many CallLog rows still match this number (digit suffix). */
    suspend fun countCallLogsForNumber(phoneNumber: String): Int = withContext(Dispatchers.IO) {
        findCallLogIdsForNumber(phoneNumber, limit = null).size
    }

    private fun deleteIdsInternal(longIds: Collection<Long>): Int {
        val ids = longIds.distinct()
        if (ids.isEmpty()) return 0
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_CALL_LOG
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return 0
        }
        var deleted = 0
        val cr = context.contentResolver
        try {
            // CallLogProvider only allows DELETE on content://call_log/calls —
            // content://call_log/calls/{id} throws UnsupportedOperationException.
            for (chunk in ids.chunked(50)) {
                val placeholders = chunk.joinToString(",") { "?" }
                deleted += cr.delete(
                    CallLog.Calls.CONTENT_URI,
                    "${CallLog.Calls._ID} IN ($placeholders)",
                    chunk.map { it.toString() }.toTypedArray()
                )
            }
            if (deleted == 0) {
                for (id in ids) {
                    deleted += cr.delete(
                        CallLog.Calls.CONTENT_URI,
                        "${CallLog.Calls._ID}=?",
                        arrayOf(id.toString())
                    )
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return deleted
    }

    /**
     * Scans CallLog and matches by last 7/10 digits of the number
     * (SQL LIKE fails when NUMBER contains spaces/dashes).
     */
    private fun findCallLogIdsForNumber(phoneNumber: String, limit: Int?): List<Long> {
        val target = phoneNumber.filter { it.isDigit() }
        if (target.length < 7) return emptyList()
        val suffix7 = target.takeLast(7)
        val suffix10 = target.takeLast(10)
        val ids = mutableListOf<Long>()
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { c ->
                val idIdx = c.getColumnIndex(CallLog.Calls._ID)
                val numIdx = c.getColumnIndex(CallLog.Calls.NUMBER)
                if (idIdx == -1) return@use
                while (c.moveToNext()) {
                    val number = if (numIdx != -1) c.getString(numIdx).orEmpty() else ""
                    val digits = number.filter { it.isDigit() }
                    if (digits.length < 7) continue
                    val match = digits.takeLast(7) == suffix7 ||
                            (digits.length >= 10 && digits.takeLast(10) == suffix10)
                    if (!match) continue
                    ids.add(c.getLong(idIdx))
                    if (limit != null && ids.size >= limit) break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ids
    }

    fun blockNumber(phoneNumber: String): Boolean {
        val number = phoneNumber.trim()
        if (number.isEmpty()) return false
        return try {
            if (!android.provider.BlockedNumberContract.canCurrentUserBlockNumbers(context)) {
                return false
            }
            val values = android.content.ContentValues().apply {
                put(
                    android.provider.BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
                    number
                )
            }
            context.contentResolver.insert(
                android.provider.BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                values
            ) != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun groupConsecutiveCallLogs(logs: List<CallLogItem>): List<CallLogItem> {
        if (logs.isEmpty()) return emptyList()

        val cal = java.util.Calendar.getInstance()
        fun dayKey(timestamp: Long): Long {
            if (timestamp <= 0L) return Long.MIN_VALUE
            cal.timeInMillis = timestamp
            return cal.get(java.util.Calendar.YEAR) * 10_000L +
                    (cal.get(java.util.Calendar.MONTH) + 1) * 100L +
                    cal.get(java.util.Calendar.DAY_OF_MONTH)
        }

        val grouped = mutableListOf<CallLogItem>()
        var currentGroupItem: CallLogItem? = null
        var currentCount = 0
        var currentDigits = ""
        var currentDayKey = Long.MIN_VALUE
        var currentIds = mutableListOf<String>()

        for (item in logs) {
            val itemDigits = digitsOnly(item.number)
            val itemDayKey = dayKey(item.timestamp)
            val isSameContact = currentGroupItem != null && (
                    (itemDigits.isNotEmpty() && itemDigits == currentDigits) ||
                            (item.name != null && item.name == currentGroupItem.name)
                    )
            // Never merge across calendar days — otherwise yesterday's call lands under "Сегодня"
            val isSameDay = currentGroupItem != null && itemDayKey == currentDayKey

            if (isSameContact && isSameDay) {
                currentCount++
                currentIds.add(item.id)
            } else {
                if (currentGroupItem != null) {
                    grouped.add(
                        currentGroupItem.copy(
                            count = currentCount,
                            groupedIds = currentIds.toList()
                        )
                    )
                }
                currentGroupItem = item
                currentDigits = itemDigits
                currentDayKey = itemDayKey
                currentCount = 1
                currentIds = mutableListOf(item.id)
            }
        }

        if (currentGroupItem != null) {
            grouped.add(
                currentGroupItem.copy(
                    count = currentCount,
                    groupedIds = currentIds.toList()
                )
            )
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
