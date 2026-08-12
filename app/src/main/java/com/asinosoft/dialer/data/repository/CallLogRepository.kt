package com.asinosoft.dialer.data.repository

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import com.asinosoft.dialer.data.model.CallLogItem
import com.asinosoft.dialer.data.model.CallType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CallLogRepository(private val context: Context) {

    private val photoCache = mutableMapOf<String, String?>()

    suspend fun getCallLogs(): List<CallLogItem> = withContext(Dispatchers.IO) {
        val rawCallLogs = mutableListOf<CallLogItem>()

        try {
            val projectionList = mutableListOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.CACHED_PHOTO_URI,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.PHONE_ACCOUNT_ID
            )

            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projectionList.toTypedArray(),
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
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

                while (c.moveToNext()) {
                    val id = if (idIndex != -1) c.getString(idIndex) else ""
                    val number = if (numberIndex != -1) c.getString(numberIndex) else ""
                    val name = if (nameIndex != -1) c.getString(nameIndex) else null
                    var photoUri = if (photoIndex != -1) c.getString(photoIndex) else null
                    val accountId = if (accountIdIndex != -1) c.getString(accountIdIndex) else null
                    val subIdStr = if (subIdIndex != -1) c.getString(subIdIndex) else null
                    val simIdStr = if (simIdIndex != -1) c.getString(simIdIndex) else null

                    if (number.isNotBlank()) {
                        val highResPhoto = getContactPhotoUri(number)
                        if (!highResPhoto.isNullOrEmpty()) {
                            photoUri = highResPhoto
                        }
                    }

                    val simNumber = detectSimNumber(accountId, subIdStr, simIdStr)
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
            // Permission not granted yet, return mock data
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (rawCallLogs.isEmpty()) {
            groupConsecutiveCallLogs(getMockCallLogs())
        } else {
            groupConsecutiveCallLogs(rawCallLogs)
        }
    }

    private fun groupConsecutiveCallLogs(logs: List<CallLogItem>): List<CallLogItem> {
        if (logs.isEmpty()) return emptyList()

        val grouped = mutableListOf<CallLogItem>()
        var currentGroupItem: CallLogItem? = null
        var currentCount = 0

        for (item in logs) {
            val cleanCurrentNumber = item.number.replace(Regex("[^0-9+]"), "")
            val cleanGroupNumber = currentGroupItem?.number?.replace(Regex("[^0-9+]"), "")

            val isSameContact = currentGroupItem != null &&
                    ((cleanCurrentNumber.isNotEmpty() && cleanCurrentNumber == cleanGroupNumber) ||
                            (item.name != null && item.name == currentGroupItem.name))

            if (isSameContact) {
                currentCount++
            } else {
                if (currentGroupItem != null) {
                    grouped.add(currentGroupItem.copy(count = currentCount))
                }
                currentGroupItem = item
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
    private fun detectSimNumber(
        accountHandleId: String?,
        subIdStr: String?,
        simIdStr: String?
    ): Int {
        try {
            val subManager =
                context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            if (subManager != null) {
                val activeList = subManager.activeSubscriptionInfoList
                if (!activeList.isNullOrEmpty()) {
                    for (info in activeList) {
                        val subId = info.subscriptionId.toString()
                        val slotIndex = info.simSlotIndex // 0 for SIM1, 1 for SIM2
                        val iccId = info.iccId ?: ""

                        if ((subIdStr != null && subIdStr == subId) ||
                            (simIdStr != null && simIdStr == subId) ||
                            (accountHandleId != null && accountHandleId == subId) ||
                            (!accountHandleId.isNullOrBlank() && iccId.isNotBlank() && accountHandleId.contains(
                                iccId
                            ))
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
            }
        } catch (_: Exception) {
            // ignore
        }

        // Check fallback string indicators
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

    private fun getContactPhotoUri(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        if (photoCache.containsKey(phoneNumber)) return photoCache[phoneNumber]

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
            photoCache[phoneNumber] = photoUri
            photoUri
        } catch (_: Exception) {
            null
        }
    }

    private fun getMockCallLogs(): List<CallLogItem> {
        val now = System.currentTimeMillis()
        val hour = 3600_000L
        val day = 24 * hour

        return listOf(
            CallLogItem(
                "1",
                "+7 (999) 123-45-67",
                "Мама",
                null,
                CallType.INCOMING,
                now - (15 * 60_000L),
                184,
                simNumber = 1
            ),
            CallLogItem(
                "2",
                "+7 (999) 123-45-67",
                "Мама",
                null,
                CallType.INCOMING,
                now - (20 * 60_000L),
                120,
                simNumber = 1
            ),
            CallLogItem(
                "3",
                "+7 (921) 987-65-43",
                "Алексей Смирнов",
                null,
                CallType.MISSED,
                now - (2 * hour),
                0,
                simNumber = 2
            ),
            CallLogItem(
                "4",
                "+7 (800) 555-35-35",
                "Банк Поддержка",
                null,
                CallType.OUTGOING,
                now - (5 * hour),
                45,
                simNumber = 1
            ),
            CallLogItem(
                "5",
                "+7 (911) 444-22-11",
                "Елена Работа",
                null,
                CallType.MISSED,
                now - (1 * day),
                0,
                simNumber = 2
            ),
            CallLogItem(
                "6",
                "+7 (911) 444-22-11",
                "Елена Работа",
                null,
                CallType.MISSED,
                now - (1 * day + 10 * 60_000L),
                0,
                simNumber = 2
            ),
            CallLogItem(
                "7",
                "+7 (911) 444-22-11",
                "Елена Работа",
                null,
                CallType.MISSED,
                now - (1 * day + 20 * 60_000L),
                0,
                simNumber = 2
            ),
            CallLogItem(
                "8",
                "+7 (905) 333-22-11",
                "Доставка Озон",
                null,
                CallType.INCOMING,
                now - (1 * day + 3 * hour),
                62,
                simNumber = 1
            )
        )
    }
}
