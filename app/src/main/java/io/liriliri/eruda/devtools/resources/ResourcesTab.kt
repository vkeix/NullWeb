package io.liriliri.eruda.devtools.resources

import android.content.Intent
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import io.liriliri.eruda.devtools.EmptyNote
import io.liriliri.eruda.devtools.KVRow
import io.liriliri.eruda.devtools.ResourcesData
import io.liriliri.eruda.devtools.SectionHeader
import io.liriliri.eruda.devtools.decodeJsString
import io.liriliri.eruda.devtools.parsePairs
import io.liriliri.eruda.devtools.readStringArray
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
            SectionHeader("Cookie", onRefresh = { refreshKey++ })
            if (d.cookies.isEmpty()) EmptyNote("Empty")
            d.cookies.forEach { (k, v) -> KVRow(k, v) }
        }

        item {
            SectionHeader("Script", onRefresh = { refreshKey++ })
            if (d.scripts.isEmpty()) EmptyNote("Empty")
            d.scripts.forEach { url ->
                ResourceRow(url = url, type = "JavaScript", onClick = { onViewSource(url) })
            }
        }

        item {
            SectionHeader("Stylesheet", onRefresh = { refreshKey++ })
            if (d.styles.isEmpty()) EmptyNote("Empty")
            d.styles.forEach { url ->
                ResourceRow(url = url, type = "CSS", onClick = { onViewSource(url) })
            }
        }

        item {
            SectionHeader("Iframe", onRefresh = { refreshKey++ })
            if (d.iframes.isEmpty()) EmptyNote("Empty")
            d.iframes.forEach { url ->
                ResourceRow(url = url, type = "Iframe", onClick = { iframeSheet = url })
            }
        }

        item {
            SectionHeader("Image", onRefresh = { refreshKey++ })
            if (d.images.isEmpty()) EmptyNote("Empty")
            d.images.forEach { url ->
                ResourceRow(url = url, type = "Image", onClick = { previewUrl = url })
            }
        }
    }

    previewUrl?.let { url ->
        ImagePreviewDialog(
            url = url,
            onDismiss = { previewUrl = null },
            onCopyUrl = {
                clipboardManager.setText(AnnotatedString(url))
                Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
            },
            onOpenExternal = {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                context.startActivity(intent)
            },
            onShare = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }
                context.startActivity(Intent.createChooser(intent, "Share"))
            }
        )
    }

    iframeSheet?.let { url ->
        IframeBottomSheet(
            url = url,
            webView = webView,
            onDismiss = { iframeSheet = null },
            onCopyUrl = {
                clipboardManager.setText(AnnotatedString(url))
                Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
            },
            onOpenExternal = {
                val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                context.startActivity(intent)
            },
            onViewSource = {
                onViewSource(url)
                iframeSheet = null
            }
        )
    }
}

@Composable
private fun ResourceRow(url: String, type: String, onClick: () -> Unit) {
    val filename = url.substringAfterLast("/").substringBefore("?").ifEmpty { "Unknown" }
    val hostname = try {
        url.substringAfter("://").substringBefore("/").substringBefore(":")
    } catch (e: Exception) {
        "Unknown host"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .height(48.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImagePreviewDialog(
    url: String,
    onDismiss: () -> Unit,
    onCopyUrl: () -> Unit,
    onOpenExternal: () -> Unit,
    onShare: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            loadDataWithBaseURL(
                                null,
                                "<html><body style='margin:0;display:flex;align-items:center;justify-content:center;height:100vh;background:black;'><img src='$url' style='max-width:100%;max-height:100vh;object-fit:contain;'/></body></html>",
                                "text/html",
                                "utf-8",
                                null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = url,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onCopyUrl) {
                    Icon(Icons.Filled.ContentCopy, "Copy URL", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = onOpenExternal) {
                    Icon(Icons.Filled.OpenInNew, "Open", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Filled.Share, "Share", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, "Close", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IframeBottomSheet(
    url: String,
    webView: WebView?,
    onDismiss: () -> Unit,
    onCopyUrl: () -> Unit,
    onOpenExternal: () -> Unit,
    onViewSource: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
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
                    .height(250.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            loadUrl(url)
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

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(onClick = onCopyUrl) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.ContentCopy, "Copy", tint = MaterialTheme.colorScheme.onSurface)
                        Text("Copy", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onOpenExternal) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.OpenInNew, "Open", tint = MaterialTheme.colorScheme.onSurface)
                        Text("Open", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onViewSource) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Refresh, "Source", tint = MaterialTheme.colorScheme.onSurface)
                        Text("Source", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
