package dev.vkeix.nullweb.devtools.sources

import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import dev.vkeix.nullweb.devtools.DetailSection
import dev.vkeix.nullweb.devtools.EmptyNote
import dev.vkeix.nullweb.devtools.decodeJsString
import org.json.JSONObject

@Composable
fun SourcesTab(webView: WebView?, initialUrl: String? = null) {
    var items by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var viewing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(initialUrl) {
        initialUrl?.let { url ->
            viewing = "JS" to url
        }
    }

    LaunchedEffect(webView, refreshKey) {
        webView?.evaluateJavascript("window.__dtApi ? __dtApi.resources() : 'null'") { result ->
            try {
                val decoded = decodeJsString(result) ?: return@evaluateJavascript
                val o = JSONObject(decoded)
                val scripts = mutableListOf<String>()
                val styles = mutableListOf<String>()
                o.optJSONArray("scripts")?.let { arr ->
                    for (i in 0 until arr.length()) scripts.add(arr.optString(i))
                }
                o.optJSONArray("styles")?.let { arr ->
                    for (i in 0 until arr.length()) styles.add(arr.optString(i))
                }
                items = scripts.map { "JS" to it } + styles.map { "CSS" to it }
            } catch (e: Exception) {
            }
        }
    }

    if (viewing != null) {
        val (type, url) = viewing!!
        var source by remember { mutableStateOf("Loading...") }

        LaunchedEffect(url) {
            webView?.evaluateJavascript("""
                (function() {
                    return fetch('$url')
                        .then(function(r) { return r.text(); })
                        .catch(function(e) { return 'Error: ' + e.message; });
                })()
            """.trimIndent()) { result ->
                source = decodeJsString(result) ?: "Failed to load"
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewing = null }) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
                }
                Text(
                    text = url.substringAfterLast("/").take(40),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
            }

            DetailSection("Source Code")
            Text(
                text = source,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(12.dp)
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            DetailSection("Scripts & Stylesheets")

            if (items.isEmpty()) {
                EmptyNote("No sources found")
            } else {
                LazyColumn {
                    items(items) { (type, url) ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { viewing = type to url }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = type,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (type == "JS") MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.width(36.dp)
                            )
                            Text(
                                text = url,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
