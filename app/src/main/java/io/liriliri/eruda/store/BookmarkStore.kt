package io.liriliri.eruda.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class BookmarkStore(context: Context) {

    private val prefs = context.getSharedPreferences("eruda_browser", Context.MODE_PRIVATE)

    data class Bookmark(val title: String, val url: String)

    fun all(): List<Bookmark> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Bookmark(o.optString("title"), o.optString("url"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun contains(url: String): Boolean = all().any { it.url == url }

    /** Returns true if the bookmark was added, false if it was removed. */
    fun toggle(title: String, url: String): Boolean {
        val current = all().toMutableList()
        val removed = current.removeAll { it.url == url }
        return if (removed) {
            save(current)
            false
        } else {
            current.add(0, Bookmark(title, url))
            save(current)
            true
        }
    }

    fun remove(bookmark: Bookmark) {
        save(all().filterNot { it.url == bookmark.url })
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun save(entries: List<Bookmark>) {
        val arr = JSONArray()
        entries.forEach {
            arr.put(JSONObject().apply {
                put("title", it.title)
                put("url", it.url)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        private const val KEY = "bookmarks"
    }
}
