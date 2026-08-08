package com.example.test_dialer.data.repository

import android.content.Context
import android.provider.ContactsContract
import com.example.test_dialer.data.model.FavoriteContact
import com.example.test_dialer.data.model.FavoriteTab
import org.json.JSONArray
import org.json.JSONObject

class FavoritesRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("favorites_prefs", Context.MODE_PRIVATE)

    fun getFavorites(): List<FavoriteContact> {
        val savedFavorites = getSavedFavorites()
        val systemStarred = getSystemStarredContacts()

        // Combine saved & system starred contacts without duplicates by name and ID
        val combined = mutableListOf<FavoriteContact>()
        val addedNames = mutableSetOf<String>()
        val addedIds = mutableSetOf<String>()

        savedFavorites.forEach { fav ->
            val nameKey = fav.name.trim().lowercase()
            combined.add(fav)
            if (nameKey.isNotEmpty()) addedNames.add(nameKey)
            if (fav.id.isNotEmpty()) addedIds.add(fav.id)
        }

        systemStarred.forEach { starred ->
            val nameKey = starred.name.trim().lowercase()
            val rawIdKey = starred.id.replace("starred_", "").trim()

            val isDuplicate = addedNames.contains(nameKey) ||
                    addedIds.contains(starred.id) ||
                    (rawIdKey.isNotEmpty() && addedIds.contains(rawIdKey))

            if (!isDuplicate) {
                combined.add(starred.copy(order = combined.size))
                if (nameKey.isNotEmpty()) addedNames.add(nameKey)
                if (starred.id.isNotEmpty()) addedIds.add(starred.id)
            }
        }

        if (combined.isEmpty()) {
            val defaults = getDefaultFavorites()
            saveFavorites(defaults)
            return defaults
        }

        return combined
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
                        photoUri = if (obj.has("photoUri") && !obj.isNull("photoUri")) obj.getString("photoUri") else null,
                        order = if (obj.has("order")) obj.getInt("order") else i,
                        tabId = if (obj.has("tabId")) obj.getString("tabId") else "default"
                    )
                )
            }
            list.sortedBy { it.order }
        } catch (e: Exception) {
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
                val nameIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                val addedContactKeys = mutableSetOf<String>()

                while (c.moveToNext()) {
                    val id = if (idIndex != -1) c.getString(idIndex) else ""
                    val name = if (nameIndex != -1) c.getString(nameIndex) else "Без имени"
                    val number = if (numberIndex != -1) c.getString(numberIndex) else ""
                    val photoUri = if (photoIndex != -1) c.getString(photoIndex) else null

                    val key = if (id.isNotBlank()) id else name.trim().lowercase()

                    if (!addedContactKeys.contains(key)) {
                        addedContactKeys.add(key)
                        list.add(
                            FavoriteContact(
                                id = "starred_$id",
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
        prefs.edit().putString("favorites_list", array.toString()).apply()
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
        } catch (e: Exception) {
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
        prefs.edit().putString("favorites_tabs", array.toString()).apply()
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
            // Reassign contacts from deleted tab to default tab "default"
            val favorites = getFavorites().map {
                if (it.tabId == id) it.copy(tabId = "default") else it
            }
            saveFavorites(favorites)
        }
        return current
    }

    fun addFavorite(contact: FavoriteContact): List<FavoriteContact> {
        val current = getFavorites().toMutableList()
        val existingIndex = current.indexOfFirst {
            it.id == contact.id || (it.name.trim().isNotBlank() && it.name.trim().equals(contact.name.trim(), ignoreCase = true))
        }
        if (existingIndex != -1) {
            current[existingIndex] = contact
        } else {
            current.add(contact.copy(order = current.size))
        }
        saveFavorites(current)
        return current
    }

    fun removeFavorite(id: String): List<FavoriteContact> {
        val current = getFavorites().filter { it.id != id }
        saveFavorites(current)
        return current
    }

    private fun getDefaultFavorites(): List<FavoriteContact> {
        return listOf(
            FavoriteContact("fav_1", "Мама", "+7 (999) 123-45-67", order = 0),
            FavoriteContact("fav_2", "Алексей Смирнов", "+7 (921) 987-65-43", order = 1),
            FavoriteContact("fav_3", "Елена Работа", "+7 (911) 444-22-11", order = 2)
        )
    }
}
