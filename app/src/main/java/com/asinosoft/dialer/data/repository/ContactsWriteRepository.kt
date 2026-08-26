package com.asinosoft.dialer.data.repository

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log
import com.asinosoft.dialer.data.model.FavoriteContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Read/write bridge to Android [ContactsContract] for favorites (STARRED)
 * and basic contact fields (name, phones, emails, birthday, photo).
 */
class ContactsWriteRepository(private val context: Context) {

    data class PhoneEntry(val number: String, val label: String)
    data class EmailEntry(val address: String, val label: String)

    fun resolveContactId(contact: FavoriteContact): Long? {
        parseNumericId(contact.id)?.let { id ->
            if (contactExists(id)) return id
        }
        return lookupContactIdByNumber(contact.number)
    }

    fun resolveContactId(number: String, idHint: String? = null): Long? {
        parseNumericId(idHint)?.let { id ->
            if (contactExists(id)) return id
        }
        return lookupContactIdByNumber(number)
    }

    fun isStarred(contactId: Long): Boolean {
        return try {
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.Contacts.STARRED),
                null,
                null,
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(ContactsContract.Contacts.STARRED)
                    idx != -1 && c.getInt(idx) != 0
                } else false
            } ?: false
        } catch (e: Exception) {
            Log.w(TAG, "isStarred failed", e)
            false
        }
    }

    /** Sets Android Contacts STARRED flag. Returns resolved contact id or null. */
    fun setStarred(contact: FavoriteContact, starred: Boolean): Long? {
        val contactId = resolveContactId(contact) ?: return null
        return setStarred(contactId, starred)
    }

    fun setStarred(contactId: Long, starred: Boolean): Long? {
        return try {
            val values = ContentValues().apply {
                put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0)
            }
            val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
            context.contentResolver.update(uri, values, null, null)
            contactId
        } catch (e: SecurityException) {
            Log.w(TAG, "WRITE_CONTACTS missing for STARRED", e)
            null
        } catch (e: Exception) {
            Log.w(TAG, "setStarred failed", e)
            null
        }
    }

    /**
     * Renames contact in Android Contacts and refreshes CallLog.CACHED_NAME for matching numbers.
     */
    suspend fun renameContact(
        contact: FavoriteContact,
        newDisplayName: String,
        phoneNumbers: Collection<String> = listOf(contact.number)
    ): Boolean = withContext(Dispatchers.IO) {
        val trimmed = newDisplayName.trim()
        if (trimmed.isEmpty()) return@withContext false

        val contactId = resolveContactId(contact)
        val nameOk = if (contactId != null) {
            updateDisplayName(contactId, trimmed)
        } else {
            Log.w(TAG, "renameContact: contact id not resolved for ${contact.id}/${contact.number}")
            false
        }

        val numbers = (phoneNumbers + contact.number).filter { it.isNotBlank() }.distinct()
        updateCallLogCachedName(numbers, trimmed)

        nameOk
    }

    suspend fun updateContactDetails(
        contact: FavoriteContact,
        displayName: String,
        phones: List<PhoneEntry>,
        emails: List<EmailEntry>,
        birthdayDateString: String?,
        photoBitmap: Bitmap?
    ): Boolean = withContext(Dispatchers.IO) {
        val contactId = resolveContactId(contact)
        if (contactId == null) {
            Log.w(TAG, "updateContactDetails: unresolved contact ${contact.id}")
            // Still try to refresh call log names from app rename
            updateCallLogCachedName(
                (phones.map { it.number } + contact.number).filter { it.isNotBlank() },
                displayName.trim()
            )
            return@withContext false
        }

        val rawContactId = getWritableRawContactId(contactId) ?: getPrimaryRawContactId(contactId)
        if (rawContactId == null) {
            Log.w(TAG, "updateContactDetails: no raw contact for $contactId")
            return@withContext false
        }

        // Name first, alone — phone/email batch must not roll it back on failure
        val nameOk = updateDisplayName(contactId, displayName.trim(), rawContactId)
        updateCallLogCachedName(
            (phones.map { it.number } + contact.number).filter { it.isNotBlank() },
            displayName.trim()
        )

        try {
            val ops = ArrayList<ContentProviderOperation>()

            // Replace phones
            ops.add(
                ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                        arrayOf(
                            contactId.toString(),
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                        )
                    )
                    .build()
            )
            phones.filter { it.number.isNotBlank() }.forEach { phone ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone.number)
                        .withValue(
                            ContactsContract.CommonDataKinds.Phone.TYPE,
                            phoneTypeFromLabel(phone.label)
                        )
                        .build()
                )
            }

            // Replace emails
            ops.add(
                ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?",
                        arrayOf(
                            contactId.toString(),
                            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
                        )
                    )
                    .build()
            )
            emails.filter { it.address.isNotBlank() }.forEach { email ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE
                        )
                        .withValue(ContactsContract.CommonDataKinds.Email.ADDRESS, email.address)
                        .withValue(
                            ContactsContract.CommonDataKinds.Email.TYPE,
                            emailTypeFromLabel(email.label)
                        )
                        .build()
                )
            }

            ops.add(
                ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                    .withSelection(
                        "${ContactsContract.Data.CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=? AND ${ContactsContract.CommonDataKinds.Event.TYPE}=?",
                        arrayOf(
                            contactId.toString(),
                            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                            ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()
                        )
                    )
                    .build()
            )
            if (!birthdayDateString.isNullOrBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE
                        )
                        .withValue(
                            ContactsContract.CommonDataKinds.Event.START_DATE,
                            birthdayDateString
                        )
                        .withValue(
                            ContactsContract.CommonDataKinds.Event.TYPE,
                            ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY
                        )
                        .build()
                )
            }

            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)

            if (photoBitmap != null) {
                writeContactPhoto(rawContactId, photoBitmap)
            }
            nameOk
        } catch (e: SecurityException) {
            Log.w(TAG, "WRITE_CONTACTS missing for update", e)
            nameOk
        } catch (e: Exception) {
            Log.w(TAG, "updateContactDetails extras failed (name may still be ok)", e)
            nameOk
        }
    }

    private fun updateDisplayName(
        contactId: Long,
        displayName: String,
        rawContactIdHint: Long? = null
    ): Boolean {
        val rawContactId = rawContactIdHint
            ?: getWritableRawContactId(contactId)
            ?: getPrimaryRawContactId(contactId)
            ?: return false

        return try {
            val selection =
                "${ContactsContract.Data.RAW_CONTACT_ID}=? AND ${ContactsContract.Data.MIMETYPE}=?"
            val args = arrayOf(
                rawContactId.toString(),
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
            )

            val values = ContentValues().apply {
                put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, displayName)
                put(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, displayName)
                put(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, "")
                put(ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME, "")
                put(ContactsContract.CommonDataKinds.StructuredName.PREFIX, "")
                put(ContactsContract.CommonDataKinds.StructuredName.SUFFIX, "")
            }

            val updated = context.contentResolver.update(
                ContactsContract.Data.CONTENT_URI,
                values,
                selection,
                args
            )
            if (updated > 0) return true

            values.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
            values.put(
                ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
            )
            context.contentResolver.insert(ContactsContract.Data.CONTENT_URI, values) != null
        } catch (e: SecurityException) {
            Log.w(TAG, "WRITE_CONTACTS missing for rename", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "updateDisplayName failed", e)
            false
        }
    }

    /** Best-effort update of CallLog cached display name for matching numbers. */
    fun updateCallLogCachedName(numbers: Collection<String>, newName: String) {
        val suffixes = numbers
            .map { it.filter { ch -> ch.isDigit() } }
            .filter { it.length >= 7 }
            .map { it.takeLast(10) }
            .distinct()
        if (suffixes.isEmpty() || newName.isBlank()) return

        try {
            val values = ContentValues().apply {
                put(CallLog.Calls.CACHED_NAME, newName)
            }
            for (suffix in suffixes) {
                context.contentResolver.update(
                    CallLog.Calls.CONTENT_URI,
                    values,
                    "${CallLog.Calls.NUMBER} LIKE ?",
                    arrayOf("%$suffix")
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "WRITE_CALL_LOG missing for CACHED_NAME", e)
        } catch (e: Exception) {
            Log.w(TAG, "updateCallLogCachedName failed", e)
        }
    }

    private fun writeContactPhoto(rawContactId: Long, bitmap: Bitmap) {
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
        val photoUri = Uri.withAppendedPath(
            ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawContactId),
            ContactsContract.Contacts.Photo.CONTENT_DIRECTORY
        )
        context.contentResolver.openOutputStream(photoUri)?.use { it.write(bytes) }
            ?: run {
                val values = ContentValues().apply {
                    put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    put(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE
                    )
                    put(ContactsContract.CommonDataKinds.Photo.PHOTO, bytes)
                }
                context.contentResolver.insert(ContactsContract.Data.CONTENT_URI, values)
            }
    }

    private fun contactExists(contactId: Long): Boolean {
        return try {
            context.contentResolver.query(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
                arrayOf(ContactsContract.Contacts._ID),
                null,
                null,
                null
            )?.use { it.moveToFirst() } == true
        } catch (_: Exception) {
            false
        }
    }

    private fun getWritableRawContactId(contactId: Long): Long? {
        return try {
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.RawContacts._ID,
                    ContactsContract.RawContacts.ACCOUNT_TYPE,
                    ContactsContract.RawContacts.ACCOUNT_NAME
                ),
                "${ContactsContract.RawContacts.CONTACT_ID}=? AND ${ContactsContract.RawContacts.DELETED}=0",
                arrayOf(contactId.toString()),
                "${ContactsContract.RawContacts._ID} ASC"
            )?.use { c ->
                val idIdx = c.getColumnIndex(ContactsContract.RawContacts._ID)
                val typeIdx = c.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
                var fallback: Long? = null
                while (c.moveToNext()) {
                    val id = if (idIdx != -1) c.getLong(idIdx) else continue
                    val type = if (typeIdx != -1) c.getString(typeIdx) else null
                    if (fallback == null) fallback = id
                    // Prefer local/device or Google — skip SIM if possible
                    val t = type.orEmpty().lowercase()
                    if (!t.contains("sim") && !t.contains("adn")) {
                        return@use id
                    }
                }
                fallback
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getPrimaryRawContactId(contactId: Long): Long? {
        return try {
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                "${ContactsContract.RawContacts.CONTACT_ID}=? AND ${ContactsContract.RawContacts.DELETED}=0",
                arrayOf(contactId.toString()),
                "${ContactsContract.RawContacts._ID} ASC"
            )?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(ContactsContract.RawContacts._ID)
                    if (idx != -1) c.getLong(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun lookupContactIdByNumber(number: String): Long? {
        for (candidate in numberLookupCandidates(number)) {
            try {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(candidate)
                )
                context.contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup._ID),
                    null,
                    null,
                    null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(ContactsContract.PhoneLookup._ID)
                        if (idx != -1) return c.getLong(idx)
                    }
                }
            } catch (_: Exception) {
                // try next
            }
        }
        return lookupContactIdViaPhoneTable(number)
    }

    private fun lookupContactIdViaPhoneTable(number: String): Long? {
        val needle = number.filter { it.isDigit() }.takeLast(7)
        if (needle.length < 7) return null
        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )?.use { c ->
                val idIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (c.moveToNext()) {
                    val num = if (numIdx != -1) c.getString(numIdx).orEmpty() else ""
                    val digits = num.filter { it.isDigit() }
                    if (digits.length >= 7 && digits.takeLast(7) == needle) {
                        if (idIdx != -1) return@use c.getLong(idIdx)
                    }
                }
                null
            }
        } catch (_: Exception) {
            null
        }
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
        return result.toList()
    }

    private fun parseNumericId(id: String?): Long? {
        if (id.isNullOrBlank()) return null
        val raw = id.removePrefix("starred_").removePrefix("fav_contact_")
            .removePrefix("call_log_")
        return raw.toLongOrNull()
    }

    private fun phoneTypeFromLabel(label: String): Int {
        val l = label.lowercase()
        return when {
            l.contains("моб") || l.contains("mobile") ->
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
            l.contains("дом") || l.contains("home") ->
                ContactsContract.CommonDataKinds.Phone.TYPE_HOME
            l.contains("раб") || l.contains("work") ->
                ContactsContract.CommonDataKinds.Phone.TYPE_WORK
            else -> ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
        }
    }

    private fun emailTypeFromLabel(label: String): Int {
        val l = label.lowercase()
        return when {
            l.contains("раб") || l.contains("work") ->
                ContactsContract.CommonDataKinds.Email.TYPE_WORK
            l.contains("моб") || l.contains("mobile") ->
                ContactsContract.CommonDataKinds.Email.TYPE_MOBILE
            l.contains("лич") || l.contains("home") || l.contains("personal") ->
                ContactsContract.CommonDataKinds.Email.TYPE_HOME
            else -> ContactsContract.CommonDataKinds.Email.TYPE_OTHER
        }
    }

    companion object {
        private const val TAG = "ContactsWrite"
    }
}
