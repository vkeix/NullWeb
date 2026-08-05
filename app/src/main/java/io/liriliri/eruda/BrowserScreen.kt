package io.liriliri.eruda

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import io.liriliri.eruda.store.BookmarkStore
import io.liriliri.eruda.store.DownloadStore
import io.liriliri.eruda.store.HistoryStore
import io.liriliri.eruda.store.SearchHistory
import io.liriliri.eruda.ui.BookmarksScreen
import io.liriliri.eruda.ui.BrowserMenuSheet
import io.liriliri.eruda.ui.BrowserToolbar
import io.liriliri.eruda.ui.DevToolsScreen
import io.liriliri.eruda.ui.DownloadsScreen
import io.liriliri.eruda.ui.HistoryScreen
import io.liriliri.eruda.ui.SettingsScreen
import io.liriliri.eruda.ui.StartPage
import io.liriliri.eruda.ui.SuggestionOverlay
import io.liriliri.eruda.ui.TabSwitcherOverlay
import java.io.File
import java.net.URLEncoder

@Composable
fun BrowserScreen(
    tabManager: TabManager,
    searchHistory: SearchHistory,
    historyStore: HistoryStore,
    bookmarkStore: BookmarkStore,
    snippetStore: SnippetStore,
    downloadStore: DownloadStore,
    viewModel: BrowserViewModel = viewModel()
) {
    val displayText by viewModel.displayText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadingProgress by viewModel.loadingProgress.collectAsState()
    val showTabSwitcher by viewModel.showTabSwitcher.collectAsState()
    val showMenu by viewModel.showMenu.collectAsState()
    val openPage by viewModel.openPage.collectAsState()
    val pendingExternal by viewModel.pendingExternalUrl.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val query by viewModel.query.collectAsState()
    val tabs by tabManager.tabs.collectAsState()
    val activeTabIndex by tabManager.activeTabIndex.collectAsState()
    val thumbnails by tabManager.thumbnails.collectAsState()

    var isOmniboxFocused by remember { mutableStateOf(false) }
    var searchEntries by remember { mutableStateOf(searchHistory.all()) }
    var historyEntries by remember { mutableStateOf(historyStore.all()) }
    var bookmarks by remember { mutableStateOf(bookmarkStore.all()) }
    var downloads by remember { mutableStateOf(downloadStore.all()) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val activeTab = tabs.getOrNull(activeTabIndex)
    val isHome = activeTab?.isHome == true
    val showSuggestions = isOmniboxFocused && query.isNotEmpty()

    val historyMatches = remember(query, searchEntries) {
        searchHistory.matches(query)
    }

    val resolveAndLoad: (String) -> Unit = { input ->
        var url = input.trim()
        var display = url
        var isValid = true

        if (!isHttpUrl(url) && !isFileUrl(url)) {
            if (mayBeUrl(url)) {
                url = "https://$url"
                display = url
            } else {
                try {
                    url = "https://www.google.com/search?q=${URLEncoder.encode(url, "utf-8")}"
                    searchHistory.add(input.trim())
                    searchEntries = searchHistory.all()
                } catch (e: Exception) {
                    Log.e("BrowserScreen", "Failed to encode search query", e)
                    isValid = false
                }
            }
        }

        if (isValid) {
            viewModel.updateDisplayText(display)
            tabManager.loadUrl(url)
            focusManager.clearFocus()
        }
    }

    BackHandler(
        enabled = showTabSwitcher || openPage != null || isOmniboxFocused ||
            !isHome || activeTab?.webView?.canGoBack() == true
    ) {
        when {
            showTabSwitcher -> viewModel.hideTabSwitcher()
            openPage != null -> viewModel.closePage()
            isOmniboxFocused -> focusManager.clearFocus()
            activeTab?.webView?.canGoBack() == true -> activeTab.webView.goBack()
            !isHome -> {
                tabManager.goHome()
                viewModel.updateDisplayText("")
            }
        }
    }

    LaunchedEffect(activeTabIndex) {
        tabManager.activeTab?.let { tab ->
            viewModel.updateCurrentUrl(tab.url)
            viewModel.updateDisplayText(tab.url)
        }
    }

    LaunchedEffect(openPage) {
        when (openPage) {
            BrowserPage.History -> historyEntries = historyStore.all()
            BrowserPage.Bookmarks -> bookmarks = bookmarkStore.all()
            BrowserPage.Downloads -> downloads = downloadStore.all()
            else -> {}
        }
    }

    LaunchedEffect(showTabSwitcher) {
        if (showTabSwitcher) {
            tabManager.captureActiveThumbnail()
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
                tabManager.captureActiveThumbnail()
            }
            historyStore.add(view.title ?: "Untitled", url)
            if (viewModel.openPage.value == BrowserPage.History) {
                historyEntries = historyStore.all()
            }
        }

        tabManager.onProgressChanged = { view, progress ->
            val tabIndex = tabManager.tabs.value.indexOfFirst { it.webView === view }
            if (tabIndex == tabManager.activeTabIndex.value) {
                viewModel.setLoadingProgress(progress)
            }
        }

        tabManager.onExternalUrl = { url ->
            viewModel.requestExternalOpen(url)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
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
                        history = searchEntries,
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
                        thumbnails = thumbnails,
                        activeTabIndex = activeTabIndex,
                        onTabClick = { index ->
                            tabManager.switchToTab(index)
                            viewModel.hideTabSwitcher()
                        },
                        onTabClose = { tabManager.closeTab(it) },
                        onClose = { viewModel.hideTabSwitcher() }
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
                onNewTabClick = { tabManager.addTab() },
                onTabCountClick = { viewModel.toggleTabSwitcher() },
                onMenuClick = { viewModel.showMenu() }
            )
        }

        when (openPage) {
            BrowserPage.History -> HistoryScreen(
                entries = historyEntries,
                onBack = { viewModel.closePage() },
                onRemove = { entry ->
                    historyStore.remove(entry)
                    historyEntries = historyStore.all()
                },
                onRemoveMany = { list ->
                    historyStore.removeAll(list)
                    historyEntries = historyStore.all()
                }
            )
            BrowserPage.Bookmarks -> BookmarksScreen(
                bookmarks = bookmarks,
                onBack = { viewModel.closePage() },
                onOpen = { bookmark ->
                    viewModel.closePage()
                    resolveAndLoad(bookmark.url)
                },
                onRemove = { bookmark ->
                    bookmarkStore.remove(bookmark)
                    bookmarks = bookmarkStore.all()
                }
            )
            BrowserPage.Downloads -> DownloadsScreen(
                entries = downloads,
                onBack = { viewModel.closePage() },
                onDelete = { entry ->
                    File(
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                        entry.name
                    ).delete()
                    downloadStore.remove(entry)
                    downloads = downloadStore.all()
                    Toast.makeText(context, "Download removed", Toast.LENGTH_SHORT).show()
                }
            )
            BrowserPage.Settings -> SettingsScreen(
                onBack = { viewModel.closePage() },
                onClearHistory = {
                    historyStore.clear()
                    historyEntries = emptyList()
                    Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                },
                onClearBookmarks = {
                    bookmarkStore.clear()
                    bookmarks = emptyList()
                    Toast.makeText(context, "Bookmarks cleared", Toast.LENGTH_SHORT).show()
                },
                onClearSearches = {
                    searchHistory.clear()
                    searchEntries = emptyList()
                    Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
                }
            )
            BrowserPage.DevTools -> DevToolsScreen(
                webView = activeTab?.webView,
                snippetStore = snippetStore,
                onBack = { viewModel.closePage() }
            )
            null -> {}
        }
    }

    if (showMenu) {
        BrowserMenuSheet(
            isDesktopMode = activeTab?.isDesktop == true,
            canGoBack = activeTab?.webView?.canGoBack() == true,
            canGoForward = activeTab?.webView?.canGoForward() == true,
            onDismiss = { viewModel.hideMenu() },
            onOpenHistory = { viewModel.openPage(BrowserPage.History) },
            onOpenBookmarks = { viewModel.openPage(BrowserPage.Bookmarks) },
            onOpenDownloads = { viewModel.openPage(BrowserPage.Downloads) },
            onBookmarkPage = {
                val tab = tabManager.activeTab
                if (tab != null && tab.url.isNotEmpty()) {
                    val added = bookmarkStore.toggle(tab.title, tab.url)
                    bookmarks = bookmarkStore.all()
                    Toast.makeText(
                        context,
                        if (added) "Bookmark added" else "Bookmark removed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                viewModel.hideMenu()
            },
            onToggleDesktopMode = {
                tabManager.setDesktopMode(activeTab?.isDesktop != true)
                viewModel.hideMenu()
            },
            onOpenDevTools = { viewModel.openPage(BrowserPage.DevTools) },
            onOpenSettings = { viewModel.openPage(BrowserPage.Settings) },
            onBack = {
                activeTab?.webView?.goBack()
                viewModel.hideMenu()
            },
            onForward = {
                activeTab?.webView?.goForward()
                viewModel.hideMenu()
            },
            onShare = {
                val tab = tabManager.activeTab
                if (tab != null && tab.url.isNotEmpty()) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, tab.url)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share link"))
                }
                viewModel.hideMenu()
            },
            onRefresh = {
                activeTab?.webView?.reload()
                viewModel.hideMenu()
            }
        )
    }

    pendingExternal?.let { url ->
        AlertDialog(
            onDismissRequest = { viewModel.clearExternalRequest() },
            title = {
                Text(
                    text = "Allow this site to open?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = url,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
                    }
                    viewModel.clearExternalRequest()
                }) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearExternalRequest() }) {
                    Text("Deny")
                }
            }
        )
    }
}
