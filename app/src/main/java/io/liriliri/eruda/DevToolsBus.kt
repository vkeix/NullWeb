package io.liriliri.eruda

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DevToolsBus {

    data class Entry(val level: String, val message: String, val time: Long)

    private val _logs = MutableStateFlow<List<Entry>>(emptyList())
    val logs: StateFlow<List<Entry>> = _logs.asStateFlow()

    fun push(level: String, message: String) {
        _logs.value = (_logs.value + Entry(level, message, System.currentTimeMillis())).takeLast(500)
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
