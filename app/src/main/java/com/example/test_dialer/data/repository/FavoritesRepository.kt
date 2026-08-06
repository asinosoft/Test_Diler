package com.example.test_dialer.data.repository

import android.content.Context
import android.provider.ContactsContract
import com.example.test_dialer.data.model.FavoriteContact
import org.json.JSONArray
import org.json.JSONObject

class FavoritesRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("favorites_prefs", Context.MODE_PRIVATE)

    fun getFavorites(): List<FavoriteContact> {
        val savedFavorites = getSavedFavorites()
        val systemStarred = getSystemStarredContacts()

        // Combine saved & system starred contacts without duplicates
        val combined = mutableListOf<FavoriteContact>()
        val addedNumbers = mutableSetOf<String>()

        savedFavorites.forEach { fav ->
            val cleanNum = fav.number.replace(Regex("[^0-9+]"), "")
            combined.add(fav)
            if (cleanNum.isNotEmpty()) addedNumbers.add(cleanNum)
        }

        systemStarred.forEach { starred ->
            val cleanNum = starred.number.replace(Regex("[^0-9+]"), "")
            if (cleanNum.isEmpty() || !addedNumbers.contains(cleanNum)) {
                combined.add(starred.copy(order = combined.size))
                if (cleanNum.isNotEmpty()) addedNumbers.add(cleanNum)
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
                        order = if (obj.has("order")) obj.getInt("order") else i
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

                val addedNumbers = mutableSetOf<String>()

                while (c.moveToNext()) {
                    val id = if (idIndex != -1) c.getString(idIndex) else ""
                    val name = if (nameIndex != -1) c.getString(nameIndex) else "Без имени"
                    val number = if (numberIndex != -1) c.getString(numberIndex) else ""
                    val photoUri = if (photoIndex != -1) c.getString(photoIndex) else null

                    val cleanNumber = number.replace(Regex("[^0-9+]"), "")
                    if (cleanNumber.isNotBlank() && !addedNumbers.contains(cleanNumber)) {
                        addedNumbers.add(cleanNumber)
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
            }
            array.put(obj)
        }
        prefs.edit().putString("favorites_list", array.toString()).apply()
    }

    fun addFavorite(contact: FavoriteContact): List<FavoriteContact> {
        val current = getFavorites().toMutableList()
        if (current.none { it.id == contact.id || (it.number.isNotBlank() && it.number == contact.number) }) {
            current.add(contact.copy(order = current.size))
            saveFavorites(current)
        }
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
