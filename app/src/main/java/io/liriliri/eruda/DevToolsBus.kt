package io.liriliri.eruda

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

object DevToolsBus {

    data class LogEntry(val level: String, val message: String, val time: Long)

    data class NetEntry(
        val id: Int,
        val url: String,
        val method: String,
        val status: Int,
        val type: String,
        val size: Int,
        val time: Long,
        val reqHeaders: Map<String, String>,
        val resHeaders: Map<String, String>,
        val body: String
    )

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _net = MutableStateFlow<List<NetEntry>>(emptyList())
    val net: StateFlow<List<NetEntry>> = _net.asStateFlow()

    var overrideConsole = true
    var catchGlobalErrors = true
    var clearOnNavigate = false

    fun pushLog(level: String, message: String) {
        _logs.value = (_logs.value + LogEntry(level, message, System.currentTimeMillis())).takeLast(500)
    }

    fun pushNet(json: String) {
        try {
            val o = JSONObject(json)
            val entry = NetEntry(
                id = o.optInt("id"),
                url = o.optString("url"),
                method = o.optString("method"),
                status = o.optInt("status"),
                type = o.optString("type"),
                size = o.optInt("size"),
                time = o.optLong("time"),
                reqHeaders = readMap(o.optJSONObject("qh")),
                resHeaders = readMap(o.optJSONObject("rh")),
                body = o.optString("body")
            )
            _net.value = (_net.value + entry).takeLast(200)
        } catch (e: Exception) {
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun clearNet() {
        _net.value = emptyList()
    }

    private fun readMap(o: JSONObject?): Map<String, String> {
        val map = mutableMapOf<String, String>()
        o?.keys()?.forEach { key -> map[key] = o.optString(key) }
        return map
    }
}
