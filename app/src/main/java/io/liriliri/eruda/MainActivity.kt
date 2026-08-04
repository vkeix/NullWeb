package io.liriliri.eruda

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

import io.liriliri.eruda.store.BookmarkStore
import io.liriliri.eruda.store.HistoryStore
import io.liriliri.eruda.store.SearchHistory

class MainActivity : AppCompatActivity() {
    private lateinit var tabManager: TabManager
    private lateinit var searchHistory: SearchHistory
    private lateinit var historyStore: HistoryStore
    private lateinit var bookmarkStore: BookmarkStore
    private val TAG = "NullWeb.MainActivity"
    var mFilePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingFileUrl: String? = null

    private val httpClient = OkHttpClient()

    /** JavaScript-to-Android bridge exposed as `window.DevToolsAndroid`. */
    inner class DevToolsBridge {
        @android.webkit.JavascriptInterface
        fun storeLog(level: String, args: String) {
            DevToolsBus.push(level, args)
        }
    }

    private var pendingAllFilesAccess = false

    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val url = pendingFileUrl
            pendingFileUrl = null
            if (granted && url != null) {
                tabManager.activeTab?.webView?.loadUrl(url)
            } else if (url != null) {
                Toast.makeText(this, R.string.storage_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        searchHistory = SearchHistory(this)
        historyStore = HistoryStore(this)
        bookmarkStore = BookmarkStore(this)

        tabManager = TabManager { createConfiguredWebView() }

        setContent {
            val colorScheme = if (getString(R.string.mode) == "night") {
                darkColorScheme(
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    surfaceVariant = Color(0xFF2A2A2A),
                    onBackground = Color.White,
                    onSurface = Color.White,
                    onSurfaceVariant = Color(0xFFB0B0B0)
                )
            } else {
                lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                BrowserScreen(tabManager, searchHistory, historyStore, bookmarkStore)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createConfiguredWebView(): WebView {
        val webView = WebViewFactory.create(this)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()

                if (isHttpUrl(url) || isFileUrl(url)) {
                    return false
                }

                tabManager.onExternalUrl?.invoke(url)
                return true
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()

                if (isFileUrl(url)) {
                    return serveFileUrl(request.url)
                }

                if (request.isForMainFrame) {
                    if (!isHttpUrl(url)) {
                        return null
                    }
                    Log.i(TAG, "Loading url: $url")

                    var headers = request.requestHeaders.toHeaders()
                    val contentType = headers["content-type"]
                    if (contentType == "application/x-www-form-urlencoded") {
                        return null
                    }
                    val cookie = CookieManager.getInstance().getCookie(url)
                    if (cookie != null) {
                        headers = (headers.toMap() + Pair("cookie", cookie)).toHeaders()
                    }

                    val client = OkHttpClient.Builder().followRedirects(false).build()
                    val req = Request.Builder()
                        .url(url)
                        .headers(headers)
                        .build()

                    return try {
                        val response = client.newCall(req).execute()
                        if (response.headers["content-security-policy"] == null) {
                            return null
                        }
                        val resHeaders =
                            response.headers.toMap().filter { it.key != "content-security-policy" }

                        WebResourceResponse(
                            "text/html",
                            response.header("content-encoding", "utf-8"),
                            response.code,
                            "ok",
                            resHeaders,
                            response.body?.byteStream()
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, e.message.toString())
                        null
                    }
                }

                return null
            }

            override fun onPageStarted(view: WebView?, url: String, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                view?.let { tabManager.onPageStarted?.invoke(it, url) }
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                tabManager.onPageFinished?.invoke(view, url)

                val tabIndex = tabManager.tabs.value.indexOfFirst { it.webView === view }
                if (tabIndex >= 0) {
                    tabManager.updateTabTitle(tabIndex, view.title ?: "Untitled")
                    tabManager.updateTabUrl(tabIndex, url)
                }

                // Hook console methods so the native DevTools panel receives logs.
                val script = """
                    (function () {
                        if (window.__devtoolsHooked) return;
                        window.__devtoolsHooked = true;
                        if (!window.DevToolsAndroid) return;
                        var _orig = {};
                        ['log','warn','error','info','debug'].forEach(function(lvl) {
                            _orig[lvl] = console[lvl].bind(console);
                        });
                        ['log','warn','error','info','debug'].forEach(function(lvl) {
                            (function(l, orig) {
                                console[l] = function() {
                                    orig.apply(console, arguments);
                                    try {
                                        var msg = Array.prototype.slice.call(arguments).map(function(a) {
                                            try { return typeof a === 'string' ? a : JSON.stringify(a); }
                                            catch(e) { return String(a); }
                                        }).join(' ');
                                        window.DevToolsAndroid.storeLog(l, msg);
                                    } catch(e) {}
                                };
                            })(lvl, _orig[lvl]);
                        });
                    })();
                """
                view.evaluateJavascript(script) {}
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                tabManager.onProgressChanged?.invoke(view, newProgress)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                if (mFilePathCallback != null) {
                    mFilePathCallback!!.onReceiveValue(null)
                    mFilePathCallback = null
                }
                mFilePathCallback = filePathCallback
                val intent = fileChooserParams.createIntent()
                try {
                    selectFileLauncher.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    mFilePathCallback = null
                    return false
                }
                return true
            }
        }

        webView.addJavascriptInterface(DevToolsBridge(), "DevToolsAndroid")

        return webView
    }

    private val selectFileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (mFilePathCallback != null) {
                mFilePathCallback!!.onReceiveValue(
                    WebChromeClient.FileChooserParams.parseResult(
                        result.resultCode,
                        result.data
                    )
                )
                mFilePathCallback = null
            }
        }

    private fun serveFileUrl(uri: android.net.Uri): WebResourceResponse? {
        return try {
            val path = uri.path ?: return null
            val file = File(path).canonicalFile
            if (!file.exists() || !file.canRead()) return null
            val ext = file.extension.lowercase()
            val mimeType = when (ext) {
                "html", "htm" -> "text/html"
                "js", "mjs" -> "application/javascript"
                "css" -> "text/css"
                "json" -> "application/json"
                "xml" -> "text/xml"
                "txt" -> "text/plain"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                "gif" -> "image/gif"
                "svg" -> "image/svg+xml"
                "webp" -> "image/webp"
                else -> android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(ext) ?: "application/octet-stream"
            }
            WebResourceResponse(mimeType, "utf-8", java.io.FileInputStream(file))
        } catch (e: Exception) {
            Log.e(TAG, "Error serving file URL: ${e.message}")
            null
        }
    }

    private fun loadFileUrl(url: String) {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                if (Environment.isExternalStorageManager()) {
                    tabManager.activeTab?.webView?.loadUrl(url)
                } else {
                    pendingFileUrl = url
                    Toast.makeText(this, R.string.all_files_access_required, Toast.LENGTH_LONG).show()
                    try {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                        pendingAllFilesAccess = true
                    } catch (e: Exception) {
                        try {
                            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                            pendingAllFilesAccess = true
                        } catch (e2: Exception) {
                            pendingFileUrl = null
                            Toast.makeText(this, R.string.storage_permission_denied, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                if (ContextCompat.checkSelfPermission(
                        this, Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    tabManager.activeTab?.webView?.loadUrl(url)
                } else {
                    pendingFileUrl = url
                    storagePermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
            else -> tabManager.activeTab?.webView?.loadUrl(url)
        }
    }

    override fun onResume() {
        super.onResume()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pendingAllFilesAccess) {
            pendingAllFilesAccess = false
            val url = pendingFileUrl
            if (url != null && Environment.isExternalStorageManager()) {
                pendingFileUrl = null
                tabManager.activeTab?.webView?.loadUrl(url)
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return super.dispatchTouchEvent(event)
    }
}

fun isHttpUrl(url: String): Boolean {
    return url.startsWith("http:") || url.startsWith("https:")
}

fun isFileUrl(url: String): Boolean {
    return url.startsWith("file://")
}

fun mayBeUrl(text: String): Boolean {
    val domains = arrayOf(".com", ".io", ".me", ".org", ".net", ".tv", ".cn")
    return domains.any { text.contains(it) }
}
