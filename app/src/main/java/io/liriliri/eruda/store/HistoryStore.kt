package io.liriliri.eruda.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class HistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("eruda_browser", Context.MODE_PRIVATE)

    data class Entry(val title: String, val url: String, val time: Long)

    fun all(): List<Entry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Entry(o.optString("title"), o.optString("url"), o.optLong("time"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(title: String, url: String) {
        if (url.isEmpty() || url == "about:blank") return
        val current = all().toMutableList()
        if (current.firstOrNull()?.url == url) {
            current[0] = Entry(title, url, System.currentTimeMillis())
        } else {
            current.add(0, Entry(title, url, System.currentTimeMillis()))
        }
        save(current.take(MAX_ENTRIES))
    }

    fun remove(entry: Entry) {
        save(all().filterNot { it.time == entry.time })
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun save(entries: List<Entry>) {
        val arr = JSONArray()
        entries.forEach {
            arr.put(JSONObject().apply {
                put("title", it.title)
                put("url", it.url)
                put("time", it.time)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "history"
        private const val MAX_ENTRIES = 200
    }
}
