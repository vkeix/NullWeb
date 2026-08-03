package io.liriliri.eruda

import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import io.liriliri.eruda.ui.BrowserToolbar

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
    
    Column(modifier = Modifier.fillMaxSize()) {
        BrowserToolbar(
            currentUrl = currentUrl,
            tabCount = tabs.size,
            onUrlSubmit = { url ->
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
                    val container = ViewGroup(context)
                    container.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    
                    // Add all WebViews to the container
                    tabs.forEachIndexed { index, tab ->
                        tab.webView.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        tab.webView.visibility = if (index == activeTabIndex) {
                            ViewGroup.VISIBLE
                        } else {
                            ViewGroup.GONE
                        }
                        container.addView(tab.webView)
                    }
                    
                    container
                },
                update = { container ->
                    // Update visibility when active tab changes
                    for (i in 0 until container.childCount) {
                        val webView = container.getChildAt(i)
                        webView.visibility = if (i == activeTabIndex) {
                            ViewGroup.VISIBLE
                        } else {
                            ViewGroup.GONE
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
