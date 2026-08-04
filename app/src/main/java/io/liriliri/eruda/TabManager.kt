package io.liriliri.eruda

import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages multiple browser tabs, handling creation, removal, and switching.
 */
class TabManager(private val webViewFactory: () -> WebView) {
    var onPageStarted: ((WebView, String) -> Unit)? = null
    var onPageFinished: ((WebView, String) -> Unit)? = null
    var onProgressChanged: ((WebView, Int) -> Unit)? = null
    
    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()
    
    private val _activeTabIndex = MutableStateFlow(0)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()
    
    val activeTab: Tab?
        get() = _tabs.value.getOrNull(_activeTabIndex.value)
    
    val tabCount: Int
        get() = _tabs.value.size
    
    init {
        // Start with one tab
        addTab()
    }
    
    fun addTab(url: String = "https://www.google.com") {
        val webView = webViewFactory()
        val tab = Tab(
            id = System.currentTimeMillis(),
            webView = webView,
            title = "New Tab",
            url = url
        )
        webView.loadUrl(url)
        
        _tabs.value = _tabs.value + tab
        _activeTabIndex.value = _tabs.value.size - 1
    }
    
    fun closeTab(index: Int) {
        if (_tabs.value.size <= 1) {
            // Don't close the last tab, just clear it
            _tabs.value[index].webView.loadUrl("https://www.google.com")
            _tabs.value[index].title = "New Tab"
            return
        }
        
        val tabs = _tabs.value.toMutableList()
        val removedTab = tabs.removeAt(index)
        removedTab.webView.destroy()
        
        _tabs.value = tabs
        
        // Adjust active tab index
        when {
            index < _activeTabIndex.value -> _activeTabIndex.value--
            index == _activeTabIndex.value -> {
                _activeTabIndex.value = if (index >= tabs.size) tabs.size - 1 else index
            }
        }
    }
    
    fun switchToTab(index: Int) {
        if (index in _tabs.value.indices) {
            _activeTabIndex.value = index
        }
    }
    
    fun updateTabTitle(index: Int, title: String) {
        val tabs = _tabs.value.toMutableList()
        if (index in tabs.indices) {
            tabs[index].title = title
            _tabs.value = tabs
        }
    }
    
    fun updateTabUrl(index: Int, url: String) {
        val tabs = _tabs.value.toMutableList()
        if (index in tabs.indices) {
            tabs[index].url = url
            _tabs.value = tabs
        }
    }
    
    data class Tab(
        val id: Long,
        val webView: WebView,
        var title: String,
        var url: String
    )
}
