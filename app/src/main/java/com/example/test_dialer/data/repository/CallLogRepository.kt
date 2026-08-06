package com.example.test_dialer.data.repository

import android.content.Context
import android.provider.CallLog
import com.example.test_dialer.data.model.CallLogItem
import com.example.test_dialer.data.model.CallType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CallLogRepository(private val context: Context) {

    suspend fun getCallLogs(): List<CallLogItem> = withContext(Dispatchers.IO) {
        val callLogs = mutableListOf<CallLogItem>()

        try {
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.CACHED_PHOTO_URI,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            )

            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
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

                while (c.moveToNext()) {
                    val id = if (idIndex != -1) c.getString(idIndex) else ""
                    val number = if (numberIndex != -1) c.getString(numberIndex) else ""
                    val name = if (nameIndex != -1) c.getString(nameIndex) else null
                    val photoUri = if (photoIndex != -1) c.getString(photoIndex) else null
                    val rawType = if (typeIndex != -1) c.getInt(typeIndex) else CallLog.Calls.INCOMING_TYPE
                    val date = if (dateIndex != -1) c.getLong(dateIndex) else 0L
                    val duration = if (durationIndex != -1) c.getLong(durationIndex) else 0L

                    val type = when (rawType) {
                        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                        else -> CallType.INCOMING
                    }

                    callLogs.add(
                        CallLogItem(
                            id = id,
                            number = number,
                            name = if (name.isNullOrEmpty()) null else name,
                            photoUri = photoUri,
                            type = type,
                            timestamp = date,
                            duration = duration
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted yet, return mock data
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (callLogs.isEmpty()) {
            getMockCallLogs()
        } else {
            callLogs
        }
    }

    private fun getMockCallLogs(): List<CallLogItem> {
        val now = System.currentTimeMillis()
        val hour = 3600_000L
        val day = 24 * hour

        return listOf(
            CallLogItem("1", "+7 (999) 123-45-67", "Мама", null, CallType.INCOMING, now - (15 * 60_000L), 184),
            CallLogItem("2", "+7 (921) 987-65-43", "Алексей Смирнов", null, CallType.MISSED, now - (2 * hour), 0),
            CallLogItem("3", "+7 (800) 555-35-35", "Банк Поддержка", null, CallType.OUTGOING, now - (5 * hour), 45),
            CallLogItem("4", "+7 (911) 444-22-11", "Елена Работа", null, CallType.MISSED, now - (1 * day), 0),
            CallLogItem("5", "+7 (905) 333-22-11", "Доставка Озон", null, CallType.INCOMING, now - (1 * day + 3 * hour), 62),
            CallLogItem("6", "+7 (999) 123-45-67", "Мама", null, CallType.OUTGOING, now - (2 * day), 320),
            CallLogItem("7", "+7 (931) 777-88-99", "Дмитрий К.", null, CallType.MISSED, now - (2 * day + 5 * hour), 0),
            CallLogItem("8", "+7 (495) 000-11-22", null, null, CallType.INCOMING, now - (3 * day), 12)
        )
    }
}
