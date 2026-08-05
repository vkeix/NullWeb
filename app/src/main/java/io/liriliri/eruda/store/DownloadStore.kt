package io.liriliri.eruda.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class DownloadStore(context: Context) {

    private val prefs = context.getSharedPreferences("eruda_browser", Context.MODE_PRIVATE)

    data class Entry(val id: Long, val name: String, val url: String, val time: Long)

    fun all(): List<Entry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(o.optLong("id"), o.optString("name"), o.optString("url"), o.optLong("time"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(entry: Entry) {
        val list = all().toMutableList()
        list.add(0, entry)
        save(list.take(100))
    }

    fun remove(entry: Entry) {
        save(all().filterNot { it.id == entry.id })
    }

    private fun save(entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("url", it.url)
                put("time", it.time)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "downloads"
    }
}
