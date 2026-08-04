package io.liriliri.eruda

import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import io.liriliri.eruda.ui.BrowserToolbar
import io.liriliri.eruda.ui.StartPage
import io.liriliri.eruda.ui.SuggestionOverlay
import java.net.URLEncoder

@Composable
fun BrowserScreen(
    tabManager: TabManager,
    searchHistory: SearchHistory,
    viewModel: BrowserViewModel = viewModel()
) {
    val displayText by viewModel.displayText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadingProgress by viewModel.loadingProgress.collectAsState()
    val showTabSwitcher by viewModel.showTabSwitcher.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val query by viewModel.query.collectAsState()
    val tabs by tabManager.tabs.collectAsState()
    val activeTabIndex by tabManager.activeTabIndex.collectAsState()

    var isOmniboxFocused by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(searchHistory.all()) }

    val focusManager = LocalFocusManager.current
    val activeTab = tabs.getOrNull(activeTabIndex)
    val isHome = activeTab?.isHome == true
    val showSuggestions = isOmniboxFocused && query.isNotEmpty()

    val historyMatches = remember(query, history) {
        searchHistory.matches(query)
    }

    val resolveAndLoad: (String) -> Unit = { input ->
        var url = input.trim()
        var display = url

        if (!isHttpUrl(url) && !isFileUrl(url)) {
            if (mayBeUrl(url)) {
                url = "https://$url"
                display = url
            } else {
                try {
                    url = "https://www.google.com/search?q=${URLEncoder.encode(url, "utf-8")}"
                    searchHistory.add(input.trim())
                    history = searchHistory.all()
                } catch (e: Exception) {
                    Log.e("BrowserScreen", "Failed to encode search query", e)
                    return@BrowserScreen
                }
            }
        }

        viewModel.updateDisplayText(display)
        tabManager.loadUrl(url)
        focusManager.clearFocus()
    }

    BackHandler(
        enabled = showTabSwitcher || isOmniboxFocused || activeTab?.webView?.canGoBack() == true
    ) {
        when {
            showTabSwitcher -> viewModel.hideTabSwitcher()
            isOmniboxFocused -> focusManager.clearFocus()
            else -> activeTab?.webView?.goBack()
        }
    }

    LaunchedEffect(activeTabIndex) {
        tabManager.activeTab?.let { tab ->
            viewModel.updateCurrentUrl(tab.url)
            viewModel.updateDisplayText(tab.url)
        }
    }

    LaunchedEffect(tabManager) {
        tabManager.onPageStarted = { view, url ->
            val tabIndex = tabManager.tabs.value.indexOfFirst { it.webView === view }
            if (tabIndex == tabManager.activeTabIndex.value) {
                viewModel.setLoading(true)
                viewModel.updateCurrentUrl(url)
            }
        }

        tabManager.onPageFinished = { view, url ->
            val tabIndex = tabManager.tabs.value.indexOfFirst { it.webView === view }
            if (tabIndex == tabManager.activeTabIndex.value) {
                viewModel.setLoading(false)
                viewModel.updateDisplayText(url)
            }
        }

        tabManager.onProgressChanged = { view, progress ->
            val tabIndex = tabManager.tabs.value.indexOfFirst { it.webView === view }
            if (tabIndex == tabManager.activeTabIndex.value) {
                viewModel.setLoadingProgress(progress)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { context ->
                    FrameLayout(context).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { container ->
                    val currentViews = (0 until container.childCount)
                        .map { container.getChildAt(it) as android.webkit.WebView }

                    tabs.forEach { tab ->
                        if (!currentViews.contains(tab.webView)) {
                            tab.webView.layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            container.addView(tab.webView)
                        }
                    }

                    currentViews.forEach { webView ->
                        if (tabs.none { it.webView === webView }) {
                            container.removeView(webView)
                        }
                    }

                    for (i in 0 until container.childCount) {
                        val webView = container.getChildAt(i)
                        val tabIndex = tabs.indexOfFirst { it.webView === webView }
                        webView.visibility = if (tabIndex == activeTabIndex) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isHome) {
                StartPage(
                    history = history,
                    onNavigate = resolveAndLoad
                )
            }

            if (showSuggestions) {
                SuggestionOverlay(
                    historyMatches = historyMatches,
                    googleSuggestions = suggestions,
                    onPick = resolveAndLoad
                )
            }

            if (showTabSwitcher) {
                TabSwitcherOverlay(
                    tabs = tabs,
                    activeTabIndex = activeTabIndex,
                    onTabClick = { index ->
                        tabManager.switchToTab(index)
                        viewModel.hideTabSwitcher()
                    },
                    onTabClose = { index ->
                        tabManager.closeTab(index)
                    },
                    onClose = {
                        viewModel.hideTabSwitcher()
                    }
                )
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                progress = loadingProgress / 100f,
                modifier = Modifier.fillMaxWidth()
            )
        }

        BrowserToolbar(
            committedUrl = displayText,
            tabCount = tabs.size,
            onUrlSubmit = resolveAndLoad,
            onQueryChange = viewModel::onQueryChange,
            onFocusChanged = { isOmniboxFocused = it },
            onHomeClick = {
                tabManager.goHome()
                viewModel.updateDisplayText("")
            },
            onNewTabClick = {
                tabManager.addTab()
            },
            onTabCountClick = {
                viewModel.toggleTabSwitcher()
            },
            onMenuClick = {
                // TODO: Show menu
            }
        )
    }
}
