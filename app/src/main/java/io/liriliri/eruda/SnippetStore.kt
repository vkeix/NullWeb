package io.liriliri.eruda

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SnippetStore(context: Context) {

    private val prefs = context.getSharedPreferences("eruda_browser", Context.MODE_PRIVATE)

    data class Snippet(val name: String, val code: String)

    fun all(): List<Snippet> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Snippet(o.optString("name"), o.optString("code"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(name: String, code: String) {
        val list = all().filterNot { it.name == name }.toMutableList()
        list.add(0, Snippet(name, code))
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("name", it.name)
                put("code", it.code)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun remove(name: String) {
        prefs.edit().putString(
            KEY,
            JSONArray(all().filterNot { it.name == name }.map {
                JSONObject().apply {
                    put("name", it.name)
                    put("code", it.code)
                }
            }).toString()
        ).apply()
    }

    companion object {
        private const val KEY = "snippets"
    }
}
