package dev.vkeix.nullweb

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
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
import org.json.JSONObject
import java.io.File

// Store
import dev.vkeix.nullweb.store.BookmarkStore
import dev.vkeix.nullweb.store.DownloadStore
import dev.vkeix.nullweb.store.HistoryStore
import dev.vkeix.nullweb.store.SearchHistory
import dev.vkeix.nullweb.store.SnippetStore

// Browser
import dev.vkeix.nullweb.browser.BrowserScreen

// Tab
import dev.vkeix.nullweb.tab.TabManager


class MainActivity : AppCompatActivity() {
    private lateinit var tabManager: TabManager
    private lateinit var searchHistory: SearchHistory
    private lateinit var historyStore: HistoryStore
    private lateinit var bookmarkStore: BookmarkStore
    private lateinit var snippetStore: SnippetStore
    private lateinit var downloadStore: DownloadStore
    private val TAG = "NullWeb.MainActivity"
    var mFilePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingFileUrl: String? = null

    private val httpClient = OkHttpClient()

    /** JavaScript-to-Android bridge exposed as `window.DevToolsAndroid`. */
    inner class DevToolsBridge {
        @android.webkit.JavascriptInterface
        fun storeLog(level: String, args: String) {
            DevToolsBus.pushLog(level, args)
        }

        @android.webkit.JavascriptInterface
        fun storeNet(json: String) {
            DevToolsBus.pushNet(json)
        }

        @android.webkit.JavascriptInterface
        fun flags(): String {
            return JSONObject().apply {
                put("console", DevToolsBus.overrideConsole)
                put("errors", DevToolsBus.catchGlobalErrors)
            }.toString()
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
        snippetStore = SnippetStore(this)
        downloadStore = DownloadStore(this)

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
                BrowserScreen(
                    tabManager,
                    searchHistory,
                    historyStore,
                    bookmarkStore,
                    snippetStore,
                    downloadStore
                )
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
                if (DevToolsBus.clearOnNavigate) {
                    DevToolsBus.clearLogs()
                    DevToolsBus.clearNet()
                }
                tabManager.onPageFinished?.invoke(view, url)

                val tabIndex = tabManager.tabs.value.indexOfFirst { it.webView === view }
                if (tabIndex >= 0) {
                    tabManager.updateTabTitle(tabIndex, view.title ?: "Untitled")
                    tabManager.updateTabUrl(tabIndex, url)
                }

                view.evaluateJavascript(DEVTOOLS_AGENT) {}

                // Desktop mode: force a desktop-width viewport so responsive
                // sites actually serve their desktop layout.
                val isDesktop = tabManager.tabs.value.firstOrNull { it.webView === view }?.isDesktop == true
                if (isDesktop) {
                    view.evaluateJavascript(
                        """
                        (function(){
                            var c = 'width=1366';
                            var m = document.querySelector('meta[name="viewport"]');
                            if (m) { m.setAttribute('content', c); }
                            else {
                                m = document.createElement('meta');
                                m.name = 'viewport';
                                m.setAttribute('content', c);
                                (document.head || document.documentElement).appendChild(m);
                            }
                        })();
                        """
                    ) {}
                }
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

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url))
                .setMimeType(mimeType)
                .addRequestHeader("User-Agent", userAgent)
                .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url) ?: "")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setTitle(fileName)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val id = dm.enqueue(request)
            downloadStore.add(DownloadStore.Entry(id, fileName, url, System.currentTimeMillis()))
            Toast.makeText(this, "Downloading $fileName", Toast.LENGTH_SHORT).show()
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

private val DEVTOOLS_AGENT = """
    (function () {
        if (window.__dt) return;
        window.__dt = true;
        var B = window.DevToolsAndroid;
        if (!B) return;
        var F = {};
        try { F = JSON.parse(B.flags()); } catch (e) {}

        if (F.console !== false) {
            var levels = ['log','info','warn','error','debug'];
            var orig = {};
            levels.forEach(function(l){ orig[l] = console[l] ? console[l].bind(console) : function(){}; });
            function send(l, args) {
                try {
                    var msg = Array.prototype.slice.call(args).map(function(a){
                        try { return typeof a === 'string' ? a : JSON.stringify(a); } catch(e){ return String(a); }
                    }).join(' ');
                    B.storeLog(l, msg);
                } catch(e){}
            }
            levels.forEach(function(l){
                console[l] = function(){ orig[l].apply(console, arguments); send(l, arguments); };
            });
        }
        if (F.errors !== false) {
            window.addEventListener('error', function(e){
                B.storeLog('error', e.message + ' @ ' + (e.filename || '') + ':' + e.lineno);
            });
            window.addEventListener('unhandledrejection', function(e){
                B.storeLog('error', 'Unhandled rejection: ' + (e.reason && e.reason.message ? e.reason.message : e.reason));
            });
        }

        var netId = 0;
        function hdrs(h) {
            var o = {};
            try { if (h && h.forEach) h.forEach(function(v,k){ o[k]=v; }); } catch(e){}
            return o;
        }
        var ofetch = window.fetch;
        if (ofetch) {
            window.fetch = function(input, init) {
                var id = ++netId;
                var url = typeof input === 'string' ? input : input.url;
                var method = (init && init.method) || (typeof input !== 'string' && input.method) || 'GET';
                var qh = {};
                try { if (init && init.headers && init.headers.forEach) init.headers.forEach(function(v,k){ qh[k]=v; }); } catch(e){}
                var t0 = Date.now();
                return ofetch.apply(this, arguments).then(function(res){
                    res.clone().text().then(function(body){
                        B.storeNet(JSON.stringify({
                            id:id, url:url, method:method, status:res.status,
                            type:(res.headers.get('content-type')||'').split(';')[0],
                            size:body.length, time:Date.now()-t0,
                            rh:hdrs(res.headers), qh:qh, body:body.substring(0,20000)
                        }));
                    }).catch(function(){});
                    return res;
                }).catch(function(err){
                    B.storeNet(JSON.stringify({id:id,url:url,method:method,status:0,type:'error',size:0,time:Date.now()-t0,rh:{},qh:qh,body:String(err)}));
                    throw err;
                });
            };
        }
        var oxhr = window.XMLHttpRequest.prototype.open;
        var osend = window.XMLHttpRequest.prototype.send;
        window.XMLHttpRequest.prototype.open = function(m, u){
            this.__dt = { id:++netId, method:m, url:u, t0:Date.now() };
            return oxhr.apply(this, arguments);
        };
        window.XMLHttpRequest.prototype.send = function(){
            var x = this, d = x.__dt;
            if (d) {
                x.addEventListener('loadend', function(){
                    var rh = {};
                    try {
                        x.getAllResponseHeaders().trim().split(/[\r\n]+/).forEach(function(line){
                            var p = line.split(': ');
                            if (p[0]) rh[p[0]] = p.slice(1).join(': ');
                        });
                    } catch(e){}
                    var resp = '';
                    try { resp = (x.responseType === '' || x.responseType === 'text') ? String(x.responseText || '') : '[' + x.responseType + ']'; } catch(e){}
                    B.storeNet(JSON.stringify({
                        id:d.id, url:d.url, method:d.method, status:x.status,
                        type:(x.getResponseHeader('content-type')||'').split(';')[0],
                        size:resp.length, time:Date.now()-d.t0, rh:rh, qh:{}, body:resp.substring(0,20000)
                    }));
                });
            }
            return osend.apply(this, arguments);
        };

        function domNode(el, depth) {
            var o = { t:(el.tagName||'').toLowerCase(), id:el.id||'', c:(typeof el.className==='string')?el.className:'', n:[] };
            if (depth < 6 && el.children) {
                var max = Math.min(el.children.length, 60);
                for (var i=0;i<max;i++) o.n.push(domNode(el.children[i], depth+1));
            }
            return o;
        }
        function byPath(path) {
            var el = document.documentElement;
            if (path) {
                var idx = path.split('.');
                for (var i=0;i<idx.length;i++) {
                    el = el.children[+idx[i]];
                    if (!el) return null;
                }
            }
            return el;
        }
        window.__dtApi = {
            dom: function(){ return JSON.stringify(domNode(document.documentElement, 0)); },
            node: function(path){
                var el = byPath(path);
                if (!el) return '{}';
                var attrs = {};
                for (var i=0;i<el.attributes.length;i++) attrs[el.attributes[i].name] = el.attributes[i].value;
                var cs = getComputedStyle(el);
                var keys = ['width','height','margin-top','margin-right','margin-bottom','margin-left','padding-top','padding-right','padding-bottom','padding-left','border-top-width','border-right-width','border-bottom-width','border-left-width','color','background-color','font-size','font-family','display','position'];
                var comp = {};
                keys.forEach(function(k){ comp[k] = cs.getPropertyValue(k); });
                var rules = [];
                try {
                    outer:
                    for (var s=0;s<document.styleSheets.length;s++) {
                        var cr;
                        try { cr = document.styleSheets[s].cssRules; } catch(e){ continue; }
                        if (!cr) continue;
                        for (var r=0;r<cr.length;r++) {
                            var rule = cr[r];
                            if (rule.selectorText && rule.style && rule.style.cssText) {
                                try {
                                    if (el.matches(rule.selectorText)) rules.push({ sel:rule.selectorText, css:rule.style.cssText });
                                } catch(e){}
                            }
                            if (rules.length > 15) break outer;
                        }
                    }
                } catch(e){}
                return JSON.stringify({ tag:el.tagName.toLowerCase(), attrs:attrs, comp:comp, inline:(el.style?el.style.cssText:''), rules:rules });
            },
            resources: function(){
                function store(st){ var o={}; try{ for(var i=0;i<st.length;i++){ var k=st.key(i); o[k]=st.getItem(k);} }catch(e){} return o; }
                var cookies = {};
                document.cookie.split(';').forEach(function(c){
                    var p = c.split('=');
                    if (p[0].trim()) cookies[p[0].trim()] = p.slice(1).join('=');
                });
                function list(sel, prop){ return Array.prototype.map.call(document.querySelectorAll(sel), function(e){ return e[prop]||''; }).filter(function(x){return x;}); }
                return JSON.stringify({
                    local: store(localStorage), session: store(sessionStorage), cookies: cookies,
                    scripts: list('script[src]','src'), styles: list('link[rel="stylesheet"]','href'),
                    iframes: list('iframe[src]','src'),
                    images: Array.prototype.map.call(document.images, function(i){ return i.src; })
                });
            },
            info: function(){
                return JSON.stringify({
                    url: location.href, title: document.title, charset: document.characterSet,
                    doctype: document.doctype ? document.doctype.name : '', ua: navigator.userAgent,
                    lang: navigator.language, screen: screen.width+'x'+screen.height,
                    viewport: innerWidth+'x'+innerHeight, cookiesEnabled: navigator.cookieEnabled
                });
            },
            source: function(url){
                return fetch(url).then(function(r){ return r.text(); }).then(function(t){ return t.substring(0,50000); }).catch(function(e){ return 'Failed: '+e; });
            }
        };
    })();
"""

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
