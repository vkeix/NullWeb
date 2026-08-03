package io.liriliri.eruda

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for browser UI state.
 */
class BrowserViewModel : ViewModel() {
    
    private val _currentUrl = MutableStateFlow("")
    val currentUrl: StateFlow<String> = _currentUrl.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _loadingProgress = MutableStateFlow(0)
    val loadingProgress: StateFlow<Int> = _loadingProgress.asStateFlow()
    
    private val _showTabSwitcher = MutableStateFlow(false)
    val showTabSwitcher: StateFlow<Boolean> = _showTabSwitcher.asStateFlow()
    
    fun updateCurrentUrl(url: String) {
        _currentUrl.value = url
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
}
