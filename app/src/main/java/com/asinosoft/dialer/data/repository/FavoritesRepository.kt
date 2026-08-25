package com.asinosoft.dialer.data.repository

import android.content.Context
import android.provider.ContactsContract
import androidx.core.content.edit
import com.asinosoft.dialer.data.model.FavoriteContact
import com.asinosoft.dialer.data.model.FavoriteTab
import org.json.JSONArray
import org.json.JSONObject

class FavoritesRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("favorites_prefs", Context.MODE_PRIVATE)
    private val contactsWrite = ContactsWriteRepository(context)

    fun getFavorites(): List<FavoriteContact> {
        val savedFavorites = getSavedFavorites()
        val systemStarred = getSystemStarredContacts()

        val combined = mutableListOf<FavoriteContact>()
        val addedNames = mutableSetOf<String>()
        val addedIds = mutableSetOf<String>()
        val addedPhones = mutableSetOf<String>()

        fun mark(fav: FavoriteContact) {
            val nameKey = fav.name.trim().lowercase()
            if (nameKey.isNotEmpty()) addedNames.add(nameKey)
            normalizeId(fav.id)?.let { addedIds.add(it) }
            phoneKey(fav.number)?.let { addedPhones.add(it) }
        }

        fun isDuplicate(fav: FavoriteContact): Boolean {
            val nameKey = fav.name.trim().lowercase()
            val idKey = normalizeId(fav.id)
            val phone = phoneKey(fav.number)
            return (nameKey.isNotEmpty() && nameKey in addedNames) ||
                    (idKey != null && idKey in addedIds) ||
                    (phone != null && phone in addedPhones)
        }

        savedFavorites.forEach { fav ->
            combined.add(fav)
            mark(fav)
        }

        systemStarred.forEach { starred ->
            if (!isDuplicate(starred)) {
                combined.add(starred.copy(order = combined.size))
                mark(starred)
            }
        }

        return combined
    }

    fun isFavorite(contact: FavoriteContact): Boolean {
        val idKey = normalizeId(contact.id)
        val phone = phoneKey(contact.number)
        val nameKey = contact.name.trim().lowercase()
        return getFavorites().any { fav ->
            val favId = normalizeId(fav.id)
            val favPhone = phoneKey(fav.number)
            (idKey != null && favId != null && idKey == favId) ||
                    (phone != null && favPhone != null && phone == favPhone) ||
                    (nameKey.isNotEmpty() && fav.name.trim().equals(contact.name.trim(), true))
        }
    }

    private fun getSavedFavorites(): List<FavoriteContact> {
        val jsonString = prefs.getString("favorites_list", null) ?: return emptyList()

        return try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<FavoriteContact>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    FavoriteContact(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        number = obj.getString("number"),
                        photoUri = if (obj.has("photoUri") && !obj.isNull("photoUri")) obj.getString(
                            "photoUri"
                        ) else null,
                        order = if (obj.has("order")) obj.getInt("order") else i,
                        tabId = if (obj.has("tabId")) obj.getString("tabId") else "default"
                    )
                )
            }
            list.sortedBy { it.order }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getSystemStarredContacts(): List<FavoriteContact> {
        val list = mutableListOf<FavoriteContact>()
        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                    ContactsContract.CommonDataKinds.Phone.STARRED
                ),
                "${ContactsContract.CommonDataKinds.Phone.STARRED} != 0",
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )

            cursor?.use { c ->
                val idIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIndex =
                    c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                val addedContactKeys = mutableSetOf<String>()

                while (c.moveToNext()) {
                    val id = if (idIndex != -1) c.getString(idIndex) else ""
                    val name = if (nameIndex != -1) c.getString(nameIndex) else "Без имени"
                    val number = if (numberIndex != -1) c.getString(numberIndex) else ""
                    val photoUri = if (photoIndex != -1) c.getString(photoIndex) else null

                    val key = id.ifBlank { name.trim().lowercase() }

                    if (!addedContactKeys.contains(key)) {
                        addedContactKeys.add(key)
                        list.add(
                            FavoriteContact(
                                id = id.ifBlank { "starred_$name" },
                                name = name,
                                number = number,
                                photoUri = photoUri
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveFavorites(list: List<FavoriteContact>) {
        val array = JSONArray()
        list.forEachIndexed { index, contact ->
            val obj = JSONObject().apply {
                put("id", contact.id)
                put("name", contact.name)
                put("number", contact.number)
                put("photoUri", contact.photoUri ?: JSONObject.NULL)
                put("order", index)
                put("tabId", contact.tabId)
            }
            array.put(obj)
        }
        prefs.edit { putString("favorites_list", array.toString()) }
    }

    fun getTabs(): List<FavoriteTab> {
        val jsonString = prefs.getString("favorites_tabs", null)
        if (jsonString.isNullOrEmpty()) {
            val defaults = listOf(FavoriteTab("default", "Основные", 0))
            saveTabs(defaults)
            return defaults
        }

        return try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<FavoriteTab>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    FavoriteTab(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        order = if (obj.has("order")) obj.getInt("order") else i
                    )
                )
            }
            if (list.isEmpty()) {
                val defaults = listOf(FavoriteTab("default", "Основные", 0))
                saveTabs(defaults)
                defaults
            } else {
                list.sortedBy { it.order }
            }
        } catch (_: Exception) {
            listOf(FavoriteTab("default", "Основные", 0))
        }
    }

    fun saveTabs(tabs: List<FavoriteTab>) {
        val array = JSONArray()
        tabs.forEachIndexed { index, tab ->
            val obj = JSONObject().apply {
                put("id", tab.id)
                put("name", tab.name)
                put("order", index)
            }
            array.put(obj)
        }
        prefs.edit { putString("favorites_tabs", array.toString()) }
    }

    fun addTab(name: String): List<FavoriteTab> {
        val current = getTabs().toMutableList()
        val newTab = FavoriteTab(
            id = "tab_${System.currentTimeMillis()}",
            name = name,
            order = current.size
        )
        current.add(newTab)
        saveTabs(current)
        return current
    }

    fun renameTab(id: String, newName: String): List<FavoriteTab> {
        val current = getTabs().map {
            if (it.id == id) it.copy(name = newName) else it
        }
        saveTabs(current)
        return current
    }

    fun deleteTab(id: String): List<FavoriteTab> {
        val current = getTabs().filter { it.id != id }
        if (current.isNotEmpty()) {
            saveTabs(current)
            val favorites = getSavedFavorites().map {
                if (it.tabId == id) it.copy(tabId = "default") else it
            }
            saveFavorites(favorites)
        }
        return current
    }

    fun addFavorite(contact: FavoriteContact): List<FavoriteContact> {
        val androidId = contactsWrite.setStarred(contact, starred = true)
        val normalized = if (androidId != null) {
            contact.copy(id = androidId.toString())
        } else {
            contact
        }

        val current = getSavedFavorites().toMutableList()
        val existingIndex = current.indexOfFirst { sameContact(it, normalized) }
        if (existingIndex != -1) {
            current[existingIndex] = normalized.copy(order = existingIndex, tabId = normalized.tabId)
        } else {
            current.add(normalized.copy(order = current.size))
        }
        saveFavorites(current)
        return getFavorites()
    }

    fun removeFavorite(contact: FavoriteContact): List<FavoriteContact> {
        contactsWrite.setStarred(contact, starred = false)

        val current = getSavedFavorites().filterNot { sameContact(it, contact) }
        saveFavorites(current)
        return getFavorites()
    }

    /** @deprecated prefer [removeFavorite] with full contact for STARRED sync */
    fun removeFavorite(id: String): List<FavoriteContact> {
        val fromSaved = getSavedFavorites().find { it.id == id || normalizeId(it.id) == normalizeId(id) }
        val fromMerged = getFavorites().find { it.id == id || normalizeId(it.id) == normalizeId(id) }
        val contact = fromSaved ?: fromMerged
        return if (contact != null) {
            removeFavorite(contact)
        } else {
            saveFavorites(getSavedFavorites().filterNot { it.id == id })
            getFavorites()
        }
    }

    fun updateFavorite(contact: FavoriteContact): List<FavoriteContact> {
        val current = getSavedFavorites().toMutableList()
        val existingIndex = current.indexOfFirst { sameContact(it, contact) }
        if (existingIndex != -1) {
            current[existingIndex] = contact.copy(order = existingIndex)
            saveFavorites(current)
        } else if (isFavorite(contact)) {
            // Was only system-starred — persist metadata
            current.add(contact.copy(order = current.size))
            saveFavorites(current)
        }
        return getFavorites()
    }

    private fun sameContact(a: FavoriteContact, b: FavoriteContact): Boolean {
        val idA = normalizeId(a.id)
        val idB = normalizeId(b.id)
        if (idA != null && idB != null && idA == idB) return true
        val phoneA = phoneKey(a.number)
        val phoneB = phoneKey(b.number)
        if (phoneA != null && phoneB != null && phoneA == phoneB) return true
        return a.name.trim().isNotBlank() &&
                a.name.trim().equals(b.name.trim(), ignoreCase = true)
    }

    private fun normalizeId(id: String?): String? {
        if (id.isNullOrBlank()) return null
        val raw = id.removePrefix("starred_").removePrefix("fav_contact_")
        if (raw.startsWith("call_log_")) return null
        return raw.takeIf { it.isNotBlank() }
    }

    private fun phoneKey(number: String): String? {
        val digits = number.filter { it.isDigit() }
        return if (digits.length >= 7) digits.takeLast(10) else null
    }
}
