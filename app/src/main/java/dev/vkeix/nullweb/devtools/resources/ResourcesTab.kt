package dev.vkeix.nullweb.devtools.resources

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.vkeix.nullweb.devtools.DetailSection
import dev.vkeix.nullweb.devtools.EmptyNote
import dev.vkeix.nullweb.devtools.KVRow
import dev.vkeix.nullweb.devtools.ResourcesData
import dev.vkeix.nullweb.devtools.SectionHeader
import dev.vkeix.nullweb.devtools.decodeJsString
import dev.vkeix.nullweb.devtools.parsePairs
import dev.vkeix.nullweb.devtools.readStringArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourcesTab(webView: WebView?, onViewSource: (String) -> Unit) {
    var data by remember { mutableStateOf<ResourcesData?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var iframeSheet by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val pageUrl = webView?.url ?: ""

    LaunchedEffect(webView, refreshKey) {
        webView?.evaluateJavascript("window.__dtApi ? __dtApi.resources() : 'null'") { result ->
            data = try {
                val decoded = decodeJsString(result) ?: return@evaluateJavascript
                val o = JSONObject(decoded)
                ResourcesData(
                    local = parsePairs(o.optJSONObject("local")),
                    session = parsePairs(o.optJSONObject("session")),
                    cookies = parsePairs(o.optJSONObject("cookies")),
                    scripts = readStringArray(o, "scripts"),
                    styles = readStringArray(o, "styles"),
                    iframes = readStringArray(o, "iframes"),
                    images = readStringArray(o, "images")
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    val d = data
    if (d == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SectionHeader("Local Storage", onRefresh = { refreshKey++ })
            if (d.local.isEmpty()) EmptyNote("Empty")
            d.local.forEach { (k, v) -> KVRow(k, v) }
        }
        item {
            SectionHeader("Session Storage", onRefresh = { refreshKey++ })
            if (d.session.isEmpty()) EmptyNote("Empty")
            d.session.forEach { (k, v) -> KVRow(k, v) }
        }
        item {
            SectionHeader("Cookies", onRefresh = { refreshKey++ })
            if (d.cookies.isEmpty()) EmptyNote("Empty")
            d.cookies.forEach { (k, v) -> KVRow(k, v) }
        }
        item {
            SectionHeader("Scripts", onRefresh = { refreshKey++ })
            if (d.scripts.isEmpty()) EmptyNote("Empty")
            d.scripts.forEach { url ->
                ResourceRow(url = url, type = "JavaScript", onClick = { onViewSource(url) })
            }
        }
        item {
            SectionHeader("Stylesheets", onRefresh = { refreshKey++ })
            if (d.styles.isEmpty()) EmptyNote("Empty")
            d.styles.forEach { url ->
                ResourceRow(url = url, type = "CSS", onClick = { onViewSource(url) })
            }
        }
        item {
            SectionHeader("Iframes", onRefresh = { refreshKey++ })
            if (d.iframes.isEmpty()) EmptyNote("Empty")
            d.iframes.forEach { url ->
                ResourceRow(url = url, type = "Iframe", onClick = { iframeSheet = url })
            }
        }
        item {
            SectionHeader("Images", onRefresh = { refreshKey++ })
            if (d.images.isEmpty()) EmptyNote("Empty")
            d.images.forEach { url ->
                ResourceRow(url = url, type = imageType(url), onClick = { previewUrl = url })
            }
        }
    }

    previewUrl?.let { url ->
        ImagePreviewDialog(
            url = url,
            pageUrl = pageUrl,
            onDismiss = { previewUrl = null },
            onCopyUrl = {
                clipboardManager.setText(AnnotatedString(url))
                Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
            },
            onOpenExternal = { openExternal(context, url) },
            onShare = { share(context, url) }
        )
    }

    iframeSheet?.let { url ->
        IframeBottomSheet(
            url = url,
            pageUrl = pageUrl,
            onDismiss = { iframeSheet = null },
            onCopyUrl = {
                clipboardManager.setText(AnnotatedString(url))
                Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
            },
            onOpenExternal = { openExternal(context, url) },
            onViewSource = {
                onViewSource(url)
                iframeSheet = null
            }
        )
    }
}

private fun openExternal(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open URL", Toast.LENGTH_SHORT).show()
    }
}

private fun share(context: android.content.Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot share", Toast.LENGTH_SHORT).show()
    }
}

private fun imageType(url: String): String {
    return when {
        url.startsWith("data:image/") ->
            url.substringAfter("data:").substringBefore(";").substringBefore(",").substringAfter("/").uppercase()
        url.contains(".png") -> "PNG"
        url.contains(".jpg") || url.contains(".jpeg") -> "JPEG"
        url.contains(".gif") -> "GIF"
        url.contains(".webp") -> "WebP"
        url.contains(".svg") -> "SVG"
        else -> "Image"
    }
}

@Composable
private fun ResourceRow(url: String, type: String, onClick: () -> Unit) {
    val filename = when {
        url.startsWith("data:") -> "data:${imageType(url)} (Base64)"
        url.startsWith("blob:") -> "blob: (runtime object)"
        else -> url.substringAfterLast("/").substringBefore("?").ifEmpty { "Untitled" }
    }
    val hostname = when {
        url.startsWith("data:") -> "embedded"
        url.startsWith("blob:") -> "runtime"
        else -> url.substringAfter("://").substringBefore("/").substringBefore(":")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = filename,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "$hostname • $type",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configurePreviewWebView(wv: WebView) {
    wv.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        loadWithOverviewMode = true
        useWideViewPort = true
        builtInZoomControls = true
        displayZoomControls = false
        mediaPlaybackRequiresUserGesture = false
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    }
}

@Composable
private fun ImagePreviewDialog(
    url: String,
    pageUrl: String,
    onDismiss: () -> Unit,
    onCopyUrl: () -> Unit,
    onOpenExternal: () -> Unit,
    onShare: () -> Unit
) {
    val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                * { margin: 0; padding: 0; }
                body { background: #000; display: flex; align-items: center; justify-content: center; height: 100vh; }
                img { max-width: 100%; max-height: 100vh; object-fit: contain; }
                .err { color: #888; font: 13px monospace; text-align: center; padding: 20px; }
            </style>
        </head>
        <body>
            <img src="${url.replace("\"", "&quot;")}"
                 referrerpolicy="no-referrer-when-downgrade"
                 onerror="this.outerHTML='<div class=err>Image failed to load</div>'">
        </body>
        </html>
    """.trimIndent()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            configurePreviewWebView(this)
                            webViewClient = WebViewClient()
                            // Document origin = the page, so the image host sees the right Referer
                            loadDataWithBaseURL(
                                pageUrl.ifEmpty { null },
                                html,
                                "text/html",
                                "utf-8",
                                null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = url,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PreviewAction(Icons.Filled.ContentCopy, "Copy", onCopyUrl)
                PreviewAction(Icons.Filled.OpenInNew, "Open", onOpenExternal)
                PreviewAction(Icons.Filled.Share, "Share", onShare)
                PreviewAction(Icons.Filled.Close, "Close", onDismiss)
            }
        }
    }
}

@Composable
private fun PreviewAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IframeBottomSheet(
    url: String,
    pageUrl: String,
    onDismiss: () -> Unit,
    onCopyUrl: () -> Unit,
    onOpenExternal: () -> Unit,
    onViewSource: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var loadError by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Iframe Preview",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (loadError) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Cannot embed",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "This site refuses to load outside its own pages. Use Open instead.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                configurePreviewWebView(this)
                                webViewClient = object : WebViewClient() {
                                    override fun onReceivedError(
                                        view: WebView?, errorCode: Int,
                                        description: String?, failingUrl: String?
                                    ) {
                                        super.onReceivedError(view, errorCode, description, failingUrl)
                                        if (failingUrl == url) loadError = true
                                    }
                                }
                                // Impersonate the page so referrer checks pass
                                if (pageUrl.isNotEmpty()) {
                                    loadUrl(url, mapOf("Referer" to pageUrl))
                                } else {
                                    loadUrl(url)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = url,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PreviewAction(Icons.Filled.ContentCopy, "Copy", onCopyUrl)
                PreviewAction(Icons.Filled.OpenInNew, "Open", onOpenExternal)
                PreviewAction(Icons.Filled.Refresh, "Source", onViewSource)
            }
        }
    }
}
