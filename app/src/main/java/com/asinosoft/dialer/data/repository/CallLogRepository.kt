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

    /** Normalized number (last 10 digits) → photo URI */
    private val photoCache = mutableMapOf<String, String?>()

    @Volatile
    private var cachedSubscriptions: List<SubscriptionInfo>? = null

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
            val withPhotos = enrichPhotos(raw)
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
     * PhoneLookup only for distinct numbers missing CACHED_PHOTO_URI (not per every row).
     */
    private fun enrichPhotos(logs: List<CallLogItem>): List<CallLogItem> {
        if (logs.isEmpty()) return logs

        // Seed cache from rows that already have a cached photo
        for (item in logs) {
            if (item.photoUri.isNullOrEmpty() || item.number.isBlank()) continue
            val key = photoCacheKey(item.number)
            if (key.isNotEmpty() && !photoCache.containsKey(key)) {
                photoCache[key] = item.photoUri
            }
        }

        val numbersNeedingLookup = LinkedHashSet<String>()
        for (item in logs) {
            if (!item.photoUri.isNullOrEmpty() || item.number.isBlank()) continue
            val key = photoCacheKey(item.number)
            if (key.isEmpty()) continue
            if (!photoCache.containsKey(key)) {
                numbersNeedingLookup.add(item.number)
            }
        }

        for (number in numbersNeedingLookup) {
            val key = photoCacheKey(number)
            photoCache[key] = lookupContactPhotoUri(number)
        }

        return logs.map { item ->
            if (!item.photoUri.isNullOrEmpty()) return@map item
            val key = photoCacheKey(item.number)
            if (key.isEmpty()) return@map item
            val cached = photoCache[key]
            if (cached.isNullOrEmpty()) item else item.copy(photoUri = cached)
        }
    }

    private fun photoCacheKey(number: String): String {
        val digits = digitsOnly(number)
        return if (digits.length >= 7) digits.takeLast(10) else digits
    }

    private fun lookupContactPhotoUri(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(
                ContactsContract.PhoneLookup.PHOTO_URI,
                ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
            )
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            var photoUri: String? = null
            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val fullIndex = c.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                    val thumbIndex =
                        c.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)
                    photoUri = if (fullIndex != -1) c.getString(fullIndex) else null
                    if (photoUri.isNullOrEmpty() && thumbIndex != -1) {
                        photoUri = c.getString(thumbIndex)
                    }
                }
            }
            photoUri
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
