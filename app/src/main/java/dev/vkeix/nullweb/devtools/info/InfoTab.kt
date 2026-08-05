package dev.vkeix.nullweb.devtools.info

import android.webkit.WebView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vkeix.nullweb.devtools.SectionHeader
import dev.vkeix.nullweb.devtools.KVRow
import dev.vkeix.nullweb.devtools.EmptyNote
import dev.vkeix.nullweb.devtools.decodeJsString
import org.json.JSONObject

@Composable
fun InfoTab(webView: WebView?) {
    var info by remember { mutableStateOf<PageInfo?>(null) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(webView, refreshKey) {
        webView?.evaluateJavascript("window.__dtApi ? __dtApi.info() : 'null'") { result ->
            info = try {
                val decoded = decodeJsString(result) ?: return@evaluateJavascript
                val o = JSONObject(decoded)
                PageInfo(
                    url = o.optString("url"), title = o.optString("title"),
                    charset = o.optString("charset"), doctype = o.optString("doctype"),
                    ua = o.optString("ua"), lang = o.optString("lang"),
                    screen = o.optString("screen"), viewport = o.optString("viewport")
                )
            } catch (e: Exception) {
                null
            }
        }
    }

    val i = info
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeader("Page", onRefresh = { refreshKey++ })
        if (i == null) {
            EmptyNote("No data")
        } else {
            KVRow("url", i.url)
            KVRow("title", i.title)
            KVRow("charset", i.charset)
            KVRow("doctype", i.doctype)
            KVRow("language", i.lang)
            KVRow("screen", i.screen)
            KVRow("viewport", i.viewport)
            KVRow("user-agent", i.ua)
        }
    }
}

private data class PageInfo(
    val url: String,
    val title: String,
    val charset: String,
    val doctype: String,
    val ua: String,
    val lang: String,
    val screen: String,
    val viewport: String
)
