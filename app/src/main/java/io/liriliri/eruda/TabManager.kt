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

    var onPageStarted: ((WebView, String) -> Unit)? = null
    var onPageFinished: ((WebView, String) -> Unit)? = null
    var onProgressChanged: ((WebView, Int) -> Unit)? = null

    init {
        addTab()
    }

    fun addTab() {
        val webView = webViewFactory()
        val tab = Tab(
            id = System.currentTimeMillis(),
            webView = webView,
            title = "New Tab",
            url = "",
            isHome = true
        )

        _tabs.value.getOrNull(_activeTabIndex.value)?.webView?.onPause()
        _tabs.value = _tabs.value + tab
        _activeTabIndex.value = _tabs.value.size - 1
        webView.onResume()
    }

    fun closeTab(index: Int) {
        if (_tabs.value.size <= 1) {
            resetToHome(0)
            return
        }

        val tabs = _tabs.value.toMutableList()
        tabs.removeAt(index).webView.destroy()
        _tabs.value = tabs

        when {
            index < _activeTabIndex.value -> _activeTabIndex.value--
            index == _activeTabIndex.value -> {
                val newIndex = index.coerceAtMost(tabs.size - 1)
                _activeTabIndex.value = newIndex
                tabs[newIndex].webView.onResume()
            }
        }
    }

    fun switchToTab(index: Int) {
        if (index !in _tabs.value.indices || index == _activeTabIndex.value) return
        _tabs.value.getOrNull(_activeTabIndex.value)?.webView?.onPause()
        _activeTabIndex.value = index
        _tabs.value[index].webView.onResume()
    }

    fun loadUrl(url: String) {
        val index = _activeTabIndex.value
        val tabs = _tabs.value.toMutableList()
        tabs[index].isHome = false
        tabs[index].url = url
        _tabs.value = tabs
        tabs[index].webView.loadUrl(url)
    }

    fun goHome() {
        val index = _activeTabIndex.value
        val tabs = _tabs.value.toMutableList()
        tabs[index].isHome = true
        tabs[index].title = "New Tab"
        tabs[index].url = ""
        _tabs.value = tabs
    }

    private fun resetToHome(index: Int) {
        val tabs = _tabs.value.toMutableList()
        tabs[index].isHome = true
        tabs[index].title = "New Tab"
        tabs[index].url = ""
        tabs[index].webView.loadUrl("about:blank")
        _tabs.value = tabs
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
        var url: String,
        var isHome: Boolean = true
    )
}
