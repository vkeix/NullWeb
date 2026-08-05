package io.liriliri.eruda.devtools.sources

import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.liriliri.eruda.devtools.decodeJsString
import io.liriliri.eruda.devtools.readStringArray
import org.json.JSONObject

@Composable
fun SourcesTab(webView: WebView?) {
    var items by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var viewing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(webView, refreshKey) {
        webView?.evaluateJavascript("window.__dtApi ? __dtApi.resources() : 'null'") { result ->
            try {
                val decoded = decodeJsString(result) ?: return@evaluateJavascript
                val o = JSONObject(decoded)
                items = readStringArray(o, "scripts").map { "JS" to it } +
                    readStringArray(o, "styles").map { "CSS" to it }
            } catch (_: Exception) {
            }
        }
    }

    val current = viewing
    if (current != null) {
        var source by remember { mutableStateOf("Loading…") }
        LaunchedEffect(current) {
            webView?.evaluateJavascript("__dtApi.source('${current.second.replace("'", "\\'")}')") { result ->
                source = decodeJsString(result) ?: "Failed to load"
            }
        }
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewing = null }) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(
                    text = current.second.substringAfterLast("/").take(40),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = source,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(items) { (kind, url) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewing = kind to url }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = kind,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(36.dp)
                )
                Text(
                    text = url,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
