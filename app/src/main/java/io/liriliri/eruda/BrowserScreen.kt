package io.liriliri.eruda

import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
    val isLoading by viewModel.isLoading.collectAsState()
    val loadingProgress by viewModel.loadingProgress.collectAsState()
    val showTabSwitcher by viewModel.showTabSwitcher.collectAsState()
    val tabs by tabManager.tabs.collectAsState()
    val activeTabIndex by tabManager.activeTabIndex.collectAsState()
    
    // Update current URL when active tab changes
    LaunchedEffect(activeTabIndex) {
        tabManager.activeTab?.let { tab ->
            viewModel.updateCurrentUrl(tab.url)
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
            currentUrl = currentUrl,
            tabCount = tabs.size,
            onUrlSubmit = { input ->
                var url = input.trim()
                
                // Check if it's a valid URL or should be a search
                if (!isHttpUrl(url) && !isFileUrl(url)) {
                    if (mayBeUrl(url)) {
                        url = "https://${url}"
                    } else {
                        try {
                            url = "https://www.google.com/search?q=${URLEncoder.encode(url, "utf-8")}"
                        } catch (e: Exception) {
                            Log.e("BrowserScreen", "Failed to encode search query", e)
                            return@BrowserToolbar
                        }
                    }
                }
                
                tabManager.activeTab?.webView?.loadUrl(url)
            },
            onHomeClick = {
                tabManager.activeTab?.webView?.loadUrl("https://github.com/liriliri/eruda")
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
        
        // WebView container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            AndroidView(
                factory = { context ->
                    // Use FrameLayout instead of abstract ViewGroup
                    FrameLayout(context).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { container ->
                    val currentViews = (0 until container.childCount).map { container.getChildAt(it) as android.webkit.WebView }
                    
                    // Add missing tabs (fixes bug where new tabs wouldn't show up)
                    tabs.forEach { tab ->
                        if (!currentViews.contains(tab.webView)) {
                            tab.webView.layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            container.addView(tab.webView)
                        }
                    }
                    
                    // Remove closed tabs
                    currentViews.forEach { webView ->
                        if (tabs.none { it.webView === webView }) {
                            container.removeView(webView)
                        }
                    }

                    // Update visibility when active tab changes
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
