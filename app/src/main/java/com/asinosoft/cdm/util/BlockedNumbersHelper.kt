package com.asinosoft.cdm.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.BlockedNumberContract
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BlockedNumberItem(
    val number: String,
    val displayName: String?
)

object BlockedNumbersHelper {

    fun canBlockNumbers(context: Context): Boolean = try {
        BlockedNumberContract.canCurrentUserBlockNumbers(context)
    } catch (_: Exception) {
        false
    }

    fun block(context: Context, phoneNumber: String): Boolean {
        val number = phoneNumber.trim()
        if (number.isBlank()) return false
        return try {
            if (!canBlockNumbers(context)) return false
            val values = ContentValues().apply {
                put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number)
            }
            context.contentResolver.insert(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                values
            ) != null
        } catch (_: Exception) {
            false
        }
    }

    fun unblock(context: Context, phoneNumber: String): Boolean {
        val number = phoneNumber.trim()
        if (number.isBlank()) return false
        return try {
            if (!canBlockNumbers(context)) return false
            BlockedNumberContract.unblock(context, number) > 0
        } catch (_: Exception) {
            false
        }
    }

    suspend fun loadBlockedNumbers(context: Context): List<BlockedNumberItem> =
        withContext(Dispatchers.IO) {
            if (!canBlockNumbers(context)) return@withContext emptyList()
            val result = mutableListOf<BlockedNumberItem>()
            try {
                context.contentResolver.query(
                    BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val numberIdx = cursor.getColumnIndex(
                        BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER
                    )
                    if (numberIdx == -1) return@use
                    while (cursor.moveToNext()) {
                        val number = cursor.getString(numberIdx)?.trim().orEmpty()
                        if (number.isBlank()) continue
                        result += BlockedNumberItem(
                            number = number,
                            displayName = lookupDisplayName(context, number)
                        )
                    }
                }
            } catch (_: Exception) {
                // ignore
            }
            result.distinctBy { PhoneNumberHelper.sanitizeForDial(it.number) }
                .sortedBy { (it.displayName ?: it.number).lowercase() }
        }

    private fun lookupDisplayName(context: Context, phoneNumber: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (idx != -1) cursor.getString(idx)?.takeIf { it.isNotBlank() } else null
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
