package io.liriliri.eruda

import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import io.liriliri.eruda.ui.BrowserToolbar
import java.net.URLEncoder

@Composable
fun BrowserScreen(
    tabManager: TabManager,
    viewModel: BrowserViewModel = viewModel()
) {
    val currentUrl by viewModel.currentUrl.collectAsState()
    val displayText by viewModel.displayText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadingProgress by viewModel.loadingProgress.collectAsState()
    val showTabSwitcher by viewModel.showTabSwitcher.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val tabs by tabManager.tabs.collectAsState()
    val activeTabIndex by tabManager.activeTabIndex.collectAsState()
    
    BackHandler(enabled = showTabSwitcher || tabManager.activeTab?.webView?.canGoBack() == true) {
        when {
            showTabSwitcher -> viewModel.hideTabSwitcher()
            else -> tabManager.activeTab?.webView?.goBack()
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
        BrowserToolbar(
            committedUrl = displayText,
            tabCount = tabs.size,
            suggestions = suggestions,
            onUrlSubmit = { input ->
                var url = input.trim()
                var display = input.trim()
                
                if (!isHttpUrl(url) && !isFileUrl(url)) {
                    if (mayBeUrl(url)) {
                        url = "https://${url}"
                        display = url
                    } else {
                        try {
                            url = "https://www.google.com/search?q=${URLEncoder.encode(url, "utf-8")}"
                        } catch (e: Exception) {
                            Log.e("BrowserScreen", "Failed to encode search query", e)
                            return@BrowserToolbar
                        }
                    }
                } else {
                    display = url
                }
                
                viewModel.updateDisplayText(display)
                tabManager.activeTab?.webView?.loadUrl(url)
            },
            onQueryChange = { query ->
                viewModel.onQueryChange(query)
            },
            onHomeClick = {
                tabManager.activeTab?.webView?.loadUrl("https://www.google.com")
                viewModel.updateDisplayText("https://www.google.com")
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
        
        if (isLoading) {
            LinearProgressIndicator(
                progress = loadingProgress / 100f,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
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
                    val currentViews = (0 until container.childCount).map { container.getChildAt(it) as android.webkit.WebView }
                    
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
}
