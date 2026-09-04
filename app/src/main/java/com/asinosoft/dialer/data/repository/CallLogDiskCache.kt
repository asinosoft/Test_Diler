package com.asinosoft.dialer.data.repository

import android.content.Context
import com.asinosoft.dialer.data.model.CallLogItem
import com.asinosoft.dialer.data.model.CallType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Fast disk cache for CallLogItems to enable instant cold starts (< 50ms).
 */
object CallLogDiskCache {

    private const val FILE_NAME = "call_logs_cache.json"
    private const val META_FILE_NAME = "contacts_meta_cache.json"
    private const val MAX_CACHED_ITEMS = 250

    fun loadCachedCallLogs(context: Context): List<CallLogItem> {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists() || file.length() == 0L) return emptyList()

            val jsonString = file.readText()
            val array = JSONArray(jsonString)
            val list = ArrayList<CallLogItem>(array.length())

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val typeStr = obj.optString("type", "INCOMING")
                val type = try {
                    CallType.valueOf(typeStr)
                } catch (_: Exception) {
                    CallType.INCOMING
                }

                val groupedIdsArray = obj.optJSONArray("groupedIds")
                val groupedIds = if (groupedIdsArray != null) {
                    val gList = ArrayList<String>(groupedIdsArray.length())
                    for (j in 0 until groupedIdsArray.length()) {
                        gList.add(groupedIdsArray.getString(j))
                    }
                    gList
                } else {
                    emptyList()
                }

                list.add(
                    CallLogItem(
                        id = obj.getString("id"),
                        number = obj.getString("number"),
                        name = if (obj.has("name") && !obj.isNull("name")) obj.getString("name") else null,
                        photoUri = if (obj.has("photoUri") && !obj.isNull("photoUri")) obj.getString("photoUri") else null,
                        type = type,
                        timestamp = obj.optLong("timestamp", 0L),
                        duration = obj.optLong("duration", 0L),
                        simNumber = obj.optInt("simNumber", 1),
                        count = obj.optInt("count", 1),
                        groupedIds = groupedIds
                    )
                )
            }
            return list
        } catch (_: Exception) {
            return emptyList()
        }
    }

    suspend fun saveCachedCallLogs(context: Context, logs: List<CallLogItem>) = withContext(Dispatchers.IO) {
        try {
            val itemsToSave = logs.take(MAX_CACHED_ITEMS)
            val array = JSONArray()

            for (item in itemsToSave) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("number", item.number)
                    put("name", item.name ?: JSONObject.NULL)
                    put("photoUri", item.photoUri ?: JSONObject.NULL)
                    put("type", item.type.name)
                    put("timestamp", item.timestamp)
                    put("duration", item.duration)
                    put("simNumber", item.simNumber)
                    put("count", item.count)
                    if (item.groupedIds.isNotEmpty()) {
                        val gArray = JSONArray()
                        item.groupedIds.forEach { gArray.put(it) }
                        put("groupedIds", gArray)
                    }
                }
                array.put(obj)
            }

            val file = File(context.filesDir, FILE_NAME)
            file.writeText(array.toString())
        } catch (_: Exception) {
            // ignore
        }
    }

    fun loadCachedMeta(
        context: Context,
        nameCache: MutableMap<String, String>,
        photoCache: MutableMap<String, String>
    ) {
        try {
            val file = File(context.filesDir, META_FILE_NAME)
            if (!file.exists() || file.length() == 0L) return

            val jsonString = file.readText()
            val obj = JSONObject(jsonString)

            val namesObj = obj.optJSONObject("names")
            if (namesObj != null) {
                val keys = namesObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    nameCache[k] = namesObj.getString(k)
                }
            }

            val photosObj = obj.optJSONObject("photos")
            if (photosObj != null) {
                val keys = photosObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    photoCache[k] = photosObj.getString(k)
                }
            }
        } catch (_: Exception) {
            // ignore
        }
    }

    suspend fun saveCachedMeta(
        context: Context,
        nameCache: Map<String, String>,
        photoCache: Map<String, String>
    ) = withContext(Dispatchers.IO) {
        try {
            val obj = JSONObject().apply {
                val namesObj = JSONObject()
                nameCache.forEach { (k, v) -> namesObj.put(k, v) }
                put("names", namesObj)

                val photosObj = JSONObject()
                photoCache.forEach { (k, v) -> photosObj.put(k, v) }
                put("photos", photosObj)
            }

            val file = File(context.filesDir, META_FILE_NAME)
            file.writeText(obj.toString())
        } catch (_: Exception) {
            // ignore
        }
    }
}
