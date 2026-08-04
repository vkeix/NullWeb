package io.liriliri.eruda

import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TabManager(private val webViewFactory: () -> WebView) {
    
    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()
    
    private val _activeTabIndex = MutableStateFlow(0)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()
    
    val activeTab: Tab?
        get() = _tabs.value.getOrNull(_activeTabIndex.value)
    
    val tabCount: Int
        get() = _tabs.value.size
        
    var onPageStarted: ((WebView, String) -> Unit)? = null
    var onPageFinished: ((WebView, String) -> Unit)? = null
    var onProgressChanged: ((WebView, Int) -> Unit)? = null
    
    init {
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
        
        // Pause the current tab before switching to save CPU/battery
        val currentTabs = _tabs.value
        currentTabs.getOrNull(_activeTabIndex.value)?.webView?.onPause()
        
        _tabs.value = currentTabs + tab
        _activeTabIndex.value = _tabs.value.size - 1
        
        // Resume the new tab
        webView.onResume()
    }
    
    fun closeTab(index: Int) {
        if (_tabs.value.size <= 1) {
            _tabs.value[index].webView.loadUrl("https://www.google.com")
            _tabs.value[index].title = "New Tab"
            return
        }
        
        val tabs = _tabs.value.toMutableList()
        val removedTab = tabs.removeAt(index)
        removedTab.webView.destroy()
        
        _tabs.value = tabs
        
        when {
            index < _activeTabIndex.value -> _activeTabIndex.value--
            index == _activeTabIndex.value -> {
                val newIndex = if (index >= tabs.size) tabs.size - 1 else index
                _activeTabIndex.value = newIndex
                tabs[newIndex].webView.onResume()
            }
        }
    }
    
    fun switchToTab(index: Int) {
        if (index in _tabs.value.indices && index != _activeTabIndex.value) {
            val tabsList = _tabs.value
            
            // Pause the old tab
            tabsList.getOrNull(_activeTabIndex.value)?.webView?.onPause()
            
            _activeTabIndex.value = index
            
            // Resume the new tab
            tabsList[index].webView.onResume()
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
