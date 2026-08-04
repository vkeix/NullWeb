package io.liriliri.eruda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder

class BrowserViewModel : ViewModel() {

    private val _currentUrl = MutableStateFlow("")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()

    private val _displayText = MutableStateFlow("")
    val displayText: StateFlow<String> = _displayText.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0)
    val loadingProgress: StateFlow<Int> = _loadingProgress.asStateFlow()

    private val _showTabSwitcher = MutableStateFlow(false)
    val showTabSwitcher: StateFlow<Boolean> = _showTabSwitcher.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

    private var suggestionJob: Job? = null
    private val client = OkHttpClient()

    fun updateCurrentUrl(url: String) {
        _currentUrl.value = url
    }

    fun updateDisplayText(text: String) {
        _displayText.value = text
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun setLoadingProgress(progress: Int) {
        _loadingProgress.value = progress
    }

    fun toggleTabSwitcher() {
        _showTabSwitcher.value = !_showTabSwitcher.value
    }

    fun hideTabSwitcher() {
        _showTabSwitcher.value = false
    }

    fun onQueryChange(query: String) {
        _query.value = query
        suggestionJob?.cancel()

        if (query.isBlank()) {
            _suggestions.value = emptyList()
            return
        }

        _suggestions.value = emptyList()
        suggestionJob = viewModelScope.launch {
            delay(300)
            _suggestions.value = fetchSuggestions(query)
        }
    }

    private suspend fun fetchSuggestions(query: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val url = "https://suggestqueries.google.com/complete/search?client=chrome&q=$encodedQuery"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext emptyList()

                val body = response.body?.string() ?: return@withContext emptyList()

                val json = JSONArray(body)
                val list = json.optJSONArray(1) ?: return@withContext emptyList()
                (0 until list.length()).mapNotNull {
                    list.optString(it).takeIf(String::isNotEmpty)
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
