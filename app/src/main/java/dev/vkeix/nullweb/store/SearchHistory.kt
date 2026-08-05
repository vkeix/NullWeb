package dev.vkeix.nullweb.store

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

class SearchHistory(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("eruda_browser", Context.MODE_PRIVATE)

    fun all(): List<String> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val updated = (listOf(trimmed) + all().filter { it != trimmed }).take(MAX_ENTRIES)
        prefs.edit().putString(KEY, JSONArray(updated).toString()).apply()
    }

    fun matches(query: String, limit: Int = 5): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return all().filter { it.lowercase().contains(q) }.take(limit)
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val KEY = "search_history"
        private const val MAX_ENTRIES = 20
    }
}
