package dev.vkeix.nullweb.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

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

    fun removeAll(entries: List<Entry>) {
        val times = entries.map { it.time }.toSet()
        save(all().filterNot { times.contains(it.time) })
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

object HistoryRanges {

    val labels = listOf("Last hour", "Today", "Yesterday", "Last 7 days", "Older")

    private const val HOUR = 3_600_000L
    private const val DAY = 86_400_000L

    fun bucket(time: Long): Int {
        val startToday = startOfToday()
        return when {
            time >= System.currentTimeMillis() - HOUR -> 0
            time >= startToday -> 1
            time >= startToday - DAY -> 2
            time >= startToday - 6 * DAY -> 3
            else -> 4
        }
    }

    /** Cutoff for delete dialog options: 0 = last hour, 1 = today, 2 = last 7 days, 3 = all */
    fun deleteCutoff(option: Int): Long {
        val startToday = startOfToday()
        return when (option) {
            0 -> System.currentTimeMillis() - HOUR
            1 -> startToday
            2 -> startToday - 6 * DAY
            else -> -1L
        }
    }

    private fun startOfToday(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
