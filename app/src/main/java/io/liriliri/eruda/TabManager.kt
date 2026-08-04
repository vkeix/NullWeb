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
    var onExternalUrl: ((String) -> Unit)? = null

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

        val removed = _tabs.value[index]
        removed.webView.destroy()
        _tabs.value = _tabs.value.filterIndexed { i, _ -> i != index }

        when {
            index < _activeTabIndex.value -> _activeTabIndex.value--
            index == _activeTabIndex.value -> {
                val newIndex = index.coerceAtMost(_tabs.value.size - 1)
                _activeTabIndex.value = newIndex
                _tabs.value[newIndex].webView.onResume()
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
        _tabs.value = _tabs.value.mapIndexed { i, tab ->
            if (i == index) tab.copy(isHome = false, url = url) else tab
        }
        _tabs.value[index].webView.loadUrl(url)
    }

    fun goHome() {
        val index = _activeTabIndex.value
        _tabs.value = _tabs.value.mapIndexed { i, tab ->
            if (i == index) tab.copy(isHome = true, title = "New Tab", url = "") else tab
        }
    }

    fun setDesktopMode(enabled: Boolean) {
        val index = _activeTabIndex.value
        val tab = _tabs.value.getOrNull(index) ?: return
        _tabs.value = _tabs.value.mapIndexed { i, t ->
            if (i == index) t.copy(isDesktop = enabled) else t
        }
        applyMode(tab.webView, enabled)
        tab.webView.reload()
    }

    fun toggleDevTools() {
        val index = _activeTabIndex.value
        val tab = _tabs.value.getOrNull(index) ?: return
        val newState = !tab.devToolsVisible

        _tabs.value = _tabs.value.mapIndexed { i, t ->
            if (i == index) t.copy(devToolsVisible = newState) else t
        }

        val js = if (newState) {
            "if(window.eruda){try{eruda.init();}catch(e){}}"
        } else {
            "if(window.eruda){try{eruda.destroy();}catch(e){}}"
        }
        tab.webView.evaluateJavascript(js) {}
    }

    fun updateTabTitle(index: Int, title: String) {
        _tabs.value = _tabs.value.mapIndexed { i, tab ->
            if (i == index) tab.copy(title = title) else tab
        }
    }

    fun updateTabUrl(index: Int, url: String) {
        _tabs.value = _tabs.value.mapIndexed { i, tab ->
            if (i == index) tab.copy(url = url) else tab
        }
    }

    private fun applyMode(webView: WebView, isDesktop: Boolean) {
        val settings = webView.settings
        if (isDesktop) {
            settings.userAgentString = DESKTOP_USER_AGENT
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
        } else {
            settings.userAgentString = null
            settings.useWideViewPort = false
            settings.loadWithOverviewMode = false
        }
    }

    private fun resetToHome(index: Int) {
        val tab = _tabs.value.getOrNull(index) ?: return
        _tabs.value = _tabs.value.mapIndexed { i, t ->
            if (i == index) t.copy(isHome = true, title = "New Tab", url = "") else t
        }
        tab.webView.loadUrl("about:blank")
    }

    data class Tab(
        val id: Long,
        val webView: WebView,
        var title: String,
        var url: String,
        var isHome: Boolean = true,
        var isDesktop: Boolean = false,
        var devToolsVisible: Boolean = false
    )

    companion object {
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
