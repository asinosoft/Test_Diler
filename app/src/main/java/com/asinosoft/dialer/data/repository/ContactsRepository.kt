package com.asinosoft.dialer.data.repository

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.core.net.toUri
import com.asinosoft.dialer.data.model.FavoriteContact

data class ContactMessengerAction(
    val id: String,
    val packageName: String,
    val messengerName: String,
    val accountDetail: String,
    val brandColor: Color,
    val chatIntent: Intent?,
    val audioCallIntent: Intent? = null,
    val videoCallIntent: Intent? = null
)

class ContactsRepository(private val context: Context) {
    private val colors = mapOf(
        "facebook" to Color(0xFF0084FF),
        "max" to Color(0xFF3B82F6),
        "whatsapp" to Color(0xFF25D366),
        "signal" to Color(0xFF3A76F0),
        "skype" to Color(0xFF00AFF0),
        "snapchat" to Color(0xFFE5C100),
        "telegram" to Color(0xFF24A1DE),
        "viber" to Color(0xFF7360F2),
        "vk" to Color(0xFF0077FF),
    )

    fun getMessengerActions(contact: FavoriteContact): List<ContactMessengerAction> {
        val contactId = getContactId(contact)
        val cleanNumber = contact.number.replace(Regex("[^0-9+]+"), "")
        Log.i("contacts", "ID $cleanNumber -> $contactId")
        return if (null != contactId)
            getContactActions(contactId)
        else
            getDefaultActions(contact.number)
    }

    private fun getContactActions(contactId: Long): List<ContactMessengerAction> {
        val cursor = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts.ACCOUNT_TYPE,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
                ContactsContract.Data.DATA2,
                ContactsContract.Data.DATA3,
                ContactsContract.Data.DATA4,
            ),
            "${ContactsContract.Data.CONTACT_ID} == $contactId",
            null,
            null
        )

        val actions = mutableMapOf<String, ContactMessengerAction>()

        cursor?.use { cursor ->
            val cPkg = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
            val cMimeType = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
            val c1 = cursor.getColumnIndex(ContactsContract.Data.DATA1)
            val c2 = cursor.getColumnIndex(ContactsContract.Data.DATA2)
            val c3 = cursor.getColumnIndex(ContactsContract.Data.DATA3)
            val c4 = cursor.getColumnIndex(ContactsContract.Data.DATA4)

            while (cursor.moveToNext()) {
                val pkg = cursor.getString(cPkg)
                val mimetype = cursor.getString(cMimeType)
                val data1 = cursor.getString(c1)
                val data2 = cursor.getString(c2)
                val data3 = cursor.getString(c3)
                val app = pkg.substringAfter('.').substringBefore('.')

                var action = actions[pkg] ?: ContactMessengerAction(
                    id = pkg,
                    packageName = pkg,
                    messengerName = app.capitalize(Locale.current),
                    accountDetail = data3 ?: data2 ?: data1 ?: pkg,
                    brandColor = colors.getOrDefault(app, Color.Red),
                    chatIntent = null,
                )

                val contactUri =
                    ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(contactUri, mimetype)
                    `package` = pkg
                }

                if (mimetype.contains("video")) {
                    action = action.copy(videoCallIntent = intent)
                } else if (mimetype.contains(Regex("call|audio"))) {
                    action = action.copy(audioCallIntent = intent)
                } else if (mimetype.contains(Regex("chat|profile|message"))) {
                    action = action.copy(chatIntent = intent)
                } else {

                    // ignore
                    continue
                }

                actions[pkg] = action
            }
        }

        return actions.values.toList()
    }

    private fun getContactId(contact: FavoriteContact): Long? {
        val number = contact.number.filter { it.isDigit() }
        val cursor = context.contentResolver.query(
            Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            ),
            arrayOf(ContactsContract.Data.CONTACT_ID),
            null,
            null,
            null
        )

        return cursor?.use { cursor ->
            val cId = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
            if (cId >= 0 && cursor.moveToNext()) {
                return cursor.getLong(cId)
            }
            return null
        }
    }

    private fun getDefaultActions(phoneNumber: String): List<ContactMessengerAction> {
        val phoneNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        if (phoneNumber.isBlank()) return emptyList()

        val actions = mutableListOf<ContactMessengerAction>()

        actions.addAction(
            id = "whatsapp",
            packageCandidates = listOf("com.whatsapp", "com.whatsapp.w4b"),
            name = "WhatsApp",
            brandColor = colors.getOrDefault("whatsapp", Color.Red),
            chatUrl = "https://wa.me/$phoneNumber",
            audioUrl = "https://wa.me/$phoneNumber",
            videoUrl = "https://wa.me/$phoneNumber",
            accountDetail = phoneNumber
        )

        actions.addAction(
            id = "telegram",
            packageCandidates = listOf("org.telegram.messenger"),
            name = "Telegram",
            brandColor = colors.getOrDefault("telegram", Color.Red),
            chatUrl = "https://t.me/+$phoneNumber",
            audioUrl = "https://t.me/+$phoneNumber",
            videoUrl = "https://t.me/+$phoneNumber",
            accountDetail = phoneNumber
        )

        actions.addAction(
            id = "viber",
            packageCandidates = listOf("com.viber.voip"),
            name = "Viber",
            brandColor = colors.getOrDefault("viber", Color.Red),
            chatUrl = "viber://chat?number=+$phoneNumber",
            audioUrl = "viber://calls?number=+$phoneNumber",
            videoUrl = "viber://calls?number=+$phoneNumber",
            accountDetail = phoneNumber
        )

        actions.addAction(
            id = "signal",
            packageCandidates = listOf("org.thoughtcrime.securesms"),
            name = "Signal",
            brandColor = colors.getOrDefault("signal", Color.Red),
            chatUrl = "https://signal.me/#p/+$phoneNumber",
            audioUrl = "https://signal.me/#p/+$phoneNumber",
            videoUrl = "https://signal.me/#p/+$phoneNumber",
            accountDetail = phoneNumber
        )

        actions.addAction(
            id = "skype",
            packageCandidates = listOf("com.skype.raider", "com.skype.android"),
            name = "Skype",
            brandColor = colors.getOrDefault("skype", Color.Red),
            chatUrl = "skype:+$phoneNumber?chat",
            audioUrl = "skype:+$phoneNumber?call",
            videoUrl = "skype:+$phoneNumber?call&video=true",
            accountDetail = phoneNumber
        )

        actions.addAction(
            id = "vk",
            packageCandidates = listOf("com.vk.im", "com.vkontakte.android"),
            name = "VK Messenger",
            brandColor = colors.getOrDefault("vk", Color.Red),
            chatUrl = "https://vk.me/+$phoneNumber",
            audioUrl = "https://vk.me/+$phoneNumber",
            videoUrl = "https://vk.me/+$phoneNumber",
            accountDetail = phoneNumber
        )

        actions.addAction(
            id = "max",
            packageCandidates = listOf(
                "ru.oneme.app",
                "ru.max.messenger",
                "com.max.app",
                "ru.vk.max"
            ),
            name = "MAX",
            brandColor = colors.getOrDefault("max", Color.Red),
            chatUrl = "https://max.ru/+$phoneNumber",
            audioUrl = "https://max.ru/+$phoneNumber",
            videoUrl = "https://max.ru/+$phoneNumber",
            accountDetail = phoneNumber
        )

        actions.addAction(
            id = "messenger",
            packageCandidates = listOf("com.facebook.orca"),
            name = "Messenger",
            brandColor = colors.getOrDefault("facebook", Color.Red),
            chatUrl = "https://m.me/+$phoneNumber",
            audioUrl = "https://m.me/+$phoneNumber",
            videoUrl = "https://m.me/+$phoneNumber",
            accountDetail = phoneNumber
        )

        actions.addAction(
            id = "snapchat",
            packageCandidates = listOf("com.snapchat.android"),
            name = "Snapchat",
            brandColor = colors.getOrDefault("snapchat", Color.Red),
            chatUrl = "https://snapchat.com/add/+$phoneNumber",
            audioUrl = "https://snapchat.com/add/+$phoneNumber",
            videoUrl = "https://snapchat.com/add/+$phoneNumber",
            accountDetail = phoneNumber
        )

        return actions
    }

    private fun MutableList<ContactMessengerAction>.addAction(
        id: String,
        packageCandidates: List<String>,
        name: String,
        brandColor: Color,
        chatUrl: String?,
        accountDetail: String,
        audioUrl: String? = chatUrl,
        videoUrl: String? = audioUrl
    ) {
        val resolvedPackage = packageCandidates.firstOrNull { isPackageInstalled(context, it) }
        if (resolvedPackage != null && !chatUrl.isNullOrBlank()) {
            add(
                ContactMessengerAction(
                    id = id,
                    packageName = resolvedPackage,
                    messengerName = name,
                    accountDetail = accountDetail,
                    brandColor = brandColor,
                    chatIntent = Intent(Intent.ACTION_VIEW, chatUrl.toUri()),
                    audioCallIntent = audioUrl?.let { Intent(Intent.ACTION_VIEW, it.toUri()) },
                    videoCallIntent = videoUrl?.let { Intent(Intent.ACTION_VIEW, it.toUri()) }
                )
            )
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
