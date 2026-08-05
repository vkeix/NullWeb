package io.liriliri.eruda

import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TabManager(private val webViewFactory: () -> WebView) {

    private val _tabs = MutableStateFlow<List<Tab>>(emptyList())
    val tabs: StateFlow<List<Tab>> = _tabs.asStateFlow()

    private val _activeTabIndex = MutableStateFlow(0)
    val activeTabIndex: StateFlow<Int> = _activeTabIndex.asStateFlow()

    private val _thumbnails = MutableStateFlow<Map<Long, Bitmap>>(emptyMap())
    val thumbnails: StateFlow<Map<Long, Bitmap>> = _thumbnails.asStateFlow()

    private val _groups = MutableStateFlow<List<TabGroup>>(emptyList())
    val groups: StateFlow<List<TabGroup>> = _groups.asStateFlow()

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
        captureActiveThumbnail()
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
        _thumbnails.value = _thumbnails.value - removed.id
        pruneGroups()

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
        captureActiveThumbnail()
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

    fun createGroup(aId: Long, bId: Long, name: String, colorIndex: Int) {
        if (aId == bId) return
        if (_tabs.value.none { it.id == aId } || _tabs.value.none { it.id == bId }) return
        val groupId = System.currentTimeMillis()
        _groups.value = _groups.value + TabGroup(groupId, name, colorIndex)
        _tabs.value = _tabs.value.map { t ->
            if (t.id == aId || t.id == bId) t.copy(groupId = groupId) else t
        }
        pruneGroups()
    }

    fun addToGroup(tabId: Long, groupId: Long) {
        if (_groups.value.none { it.id == groupId }) return
        _tabs.value = _tabs.value.map { t ->
            if (t.id == tabId) t.copy(groupId = groupId) else t
        }
        pruneGroups()
    }

    fun dissolveGroup(groupId: Long) {
        _tabs.value = _tabs.value.map { t ->
            if (t.groupId == groupId) t.copy(groupId = null) else t
        }
        _groups.value = _groups.value.filterNot { it.id == groupId }
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

    fun captureActiveThumbnail() {
        val tab = activeTab ?: return
        val webView = tab.webView
        if (webView.width <= 0 || webView.height <= 0) return
        webView.post {
            try {
                val w = webView.width
                val h = webView.height
                if (w <= 0 || h <= 0) return@post
                val scale = 320f / w
                val bitmap = Bitmap.createBitmap(
                    320,
                    (h * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                canvas.scale(scale, scale)
                webView.draw(canvas)
                _thumbnails.value = _thumbnails.value + (tab.id to bitmap)
            } catch (e: Exception) {
            }
        }
    }

    private fun pruneGroups() {
        _groups.value.filter { g -> _tabs.value.count { it.groupId == g.id } < 2 }.forEach { g ->
            _tabs.value = _tabs.value.map { t ->
                if (t.groupId == g.id) t.copy(groupId = null) else t
            }
        }
        _groups.value = _groups.value.filter { g -> _tabs.value.count { it.groupId == g.id } >= 2 }
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
        var groupId: Long? = null
    )

    data class TabGroup(
        val id: Long,
        val name: String,
        val colorIndex: Int
    )

    companion object {
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
