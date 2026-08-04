package io.liriliri.eruda.ui

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.liriliri.eruda.DevToolsBus
import io.liriliri.eruda.SnippetStore
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DomNode(val tag: String, val id: String, val cls: String, val children: List<DomNode>)

private data class NodeInfo(
    val tag: String,
    val attrs: List<Pair<String, String>>,
    val inline: String,
    val comp: List<Pair<String, String>>,
    val rules: List<Pair<String, String>>
)

private data class ResourcesData(
    val local: List<Pair<String, String>>,
    val session: List<Pair<String, String>>,
    val cookies: List<Pair<String, String>>,
    val scripts: List<String>,
    val styles: List<String>,
    val iframes: List<String>,
    val images: List<String>
)

private data class PageInfo(
    val url: String, val title: String, val charset: String, val doctype: String,
    val ua: String, val lang: String, val screen: String, val viewport: String
)

private val TABS = listOf("Console", "Elements", "Network", "Resources", "Sources", "Info", "Snippets", "Settings")

@Composable
fun DevToolsScreen(
    webView: WebView?,
    snippetStore: SnippetStore,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                text = "Developer tools",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            TABS.forEachIndexed { index, label ->
                Text(
                    text = label,
                    fontSize = 14.sp,
                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                    color = if (selectedTab == index) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { selectedTab = index }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)

        when (selectedTab) {
            0 -> ConsoleTab(webView)
            1 -> ElementsTab(webView)
            2 -> NetworkTab()
            3 -> ResourcesTab(webView)
            4 -> SourcesTab(webView)
            5 -> InfoTab(webView)
            6 -> SnippetsTab(webView, snippetStore)
            7 -> SettingsTab()
        }
    }
}

private fun decodeJsString(result: String?): String? {
    return try {
        if (result == null || result == "null") null
        else JSONObject("{\"v\":$result}").getString("v")
    } catch (e: Exception) {
        null
    }
}

private fun parsePairs(o: JSONObject?): List<Pair<String, String>> {
    val list = mutableListOf<Pair<String, String>>()
    o?.keys()?.forEach { key -> list.add(key to o.optString(key)) }
    return list
}

// ---------------- Console ----------------

@Composable
private fun ConsoleTab(webView: WebView?) {
    val logs by DevToolsBus.logs.collectAsState()
    var filter by remember { mutableStateOf("all") }
    var code by remember { mutableStateOf("") }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val visible = logs.filter { filter == "all" || it.level == filter }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("all", "info", "warn", "error").forEach { f ->
                Text(
                    text = f,
                    fontSize = 12.sp,
                    fontWeight = if (filter == f) FontWeight.Bold else FontWeight.Medium,
                    color = if (filter == f) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { filter = f }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { DevToolsBus.clearLogs() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }

        LazyColumn(Modifier.weight(1f)) {
            items(visible) { entry ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Text(
                        text = timeFormat.format(Date(entry.time)),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = entry.message,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = when (entry.level) {
                            "error" -> Color(0xFFEF5350)
                            "warn" -> Color(0xFFFFB300)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.surfaceVariant)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = code,
                onValueChange = { code = it },
                placeholder = { Text("Execute JavaScript…", fontSize = 13.sp, fontFamily = FontFamily.Monospace) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            )
            IconButton(onClick = { code = "" }) {
                Icon(Icons.Filled.Close, "Cancel", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = {
                if (code.isBlank()) return@IconButton
                val expr = "(function(){try{var __r=(function(){${code}})();return String(__r);}catch(e){return 'Error: '+e.message;}})()"
                webView?.evaluateJavascript(expr) { result ->
                    DevToolsBus.pushLog("log", "← " + (decodeJsString(result) ?: "undefined"))
                }
            }) {
                Icon(Icons.Filled.PlayArrow, "Execute", tint = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}

// ---------------- Elements ----------------

@Composable
private fun ElementsTab(webView: WebView?) {
    var root by remember { mutableStateOf<DomNode?>(null) }
    var selectedPath by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<NodeInfo?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(webView, refreshKey) {
        webView?.evaluateJavascript("window.__dtApi ? __dtApi.dom() : 'null'") { result ->
            root = try {
                val decoded = decodeJsString(result) ?: return@evaluateJavascript
                parseDomNode(JSONObject(decoded))
            } catch (e: Exception) {
                null
            }
        }
    }

    LaunchedEffect(selectedPath) {
        val path = selectedPath
        if (path == null) {
            info = null
        } else {
            webView?.evaluateJavascript("__dtApi.node('$path')") { result ->
                info = try {
                    val decoded = decodeJsString(result) ?: return@evaluateJavascript
                    val o = JSONObject(decoded)
                    val rules = mutableListOf<Pair<String, String>>()
                    o.optJSONArray("rules")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val r = arr.getJSONObject(i)
                            rules.add(r.optString("sel") to r.optString("css"))
                        }
                    }
                    NodeInfo(
                        tag = o.optString("tag"),
                        attrs = parsePairs(o.optJSONObject("attrs")),
                        inline = o.optString("inline"),
                        comp = parsePairs(o.optJSONObject("comp")),
                        rules = rules
                    )
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    val currentInfo = info
    if (selectedPath != null && currentInfo != null) {
        NodeDetailView(currentInfo, onBack = { selectedPath = null })
        return
    }

    val rows = mutableListOf<Triple<String, Int, DomNode>>()
    root?.let { walk(it, "", 0, expanded, rows) }

    if (root == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No DOM captured", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows, key = { it.first }) { (path, depth, node) ->
                val hasChildren = node.children.isNotEmpty()
                val isOpen = path.isEmpty() || expanded[path] == true
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (hasChildren) expanded[path] = !isOpen
                            selectedPath = path
                        }
                        .padding(start = (12 + depth * 16).dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasChildren) {
                        Icon(
                            if (isOpen) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Spacer(Modifier.width(16.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = buildString {
                            append("<${node.tag}")
                            if (node.id.isNotEmpty()) append("#${node.id}")
                            if (node.cls.isNotEmpty()) append(".${node.cls.trim().split(Regex("\\s+")).joinToString(".")}")
                            append(">")
                        },
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
}

@Composable
private fun NodeDetailView(info: NodeInfo, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                text = "<${info.tag}>",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        DetailSection("Attributes")
        if (info.attrs.isEmpty()) {
            EmptyNote("Empty")
        } else {
            info.attrs.forEach { (k, v) -> KVRow(k, v) }
        }

        DetailSection("Styles")
        if (info.inline.isNotEmpty()) KVRow("element.style", info.inline)
        info.rules.forEach { (sel, css) -> KVRow(sel, css) }
        if (info.inline.isEmpty() && info.rules.isEmpty()) EmptyNote("Empty")

        DetailSection("Computed Style")
        info.comp.forEach { (k, v) -> KVRow(k, v) }
    }
}

// ---------------- Network ----------------

@Composable
private fun NetworkTab() {
    val entries by DevToolsBus.net.collectAsState()
    var selectedId by remember { mutableStateOf<Int?>(null) }

    val selected = entries.firstOrNull { it.id == selectedId }
    if (selected != null) {
        NetDetailView(selected, onBack = { selectedId = null })
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${entries.size} requests",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { DevToolsBus.clearNet() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(entries) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedId = entry.id }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entry.method,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (entry.status in 200..399) MaterialTheme.colorScheme.onSurface else Color(0xFFEF5350),
                            modifier = Modifier.width(48.dp)
                        )
                        Text(
                            text = entry.status.toString(),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (entry.status in 200..399) MaterialTheme.colorScheme.onSurface else Color(0xFFEF5350),
                            modifier = Modifier.width(40.dp)
                        )
                        Text(
                            text = entry.url.substringAfter("://").take(60),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row {
                        Text(
                            text = "${entry.time}ms",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "${entry.size}B",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = entry.type,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetDetailView(entry: DevToolsBus.NetEntry, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                text = entry.url,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        DetailSection("Response Headers")
        if (entry.resHeaders.isEmpty()) EmptyNote("Empty")
        entry.resHeaders.forEach { (k, v) -> KVRow(k, v) }

        DetailSection("Request Headers")
        if (entry.reqHeaders.isEmpty()) EmptyNote("Empty")
        entry.reqHeaders.forEach { (k, v) -> KVRow(k, v) }

        DetailSection("Response")
        Text(
            text = entry.body.ifEmpty { "Empty" },
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(12.dp)
        )
    }
}

// ---------------- Resources ----------------

@Composable
private fun ResourcesTab(webView: WebView?) {
    var data by remember { mutableStateOf<ResourcesData?>(null)
    }
    var refreshKey by remember { mutableStateOf(0) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeader("Local Storage", onRefresh = { refreshKey++ })
        if (d.local.isEmpty()) EmptyNote("Empty")
        d.local.forEach { (k, v) -> KVRow(k, v) }

        SectionHeader("Session Storage", onRefresh = { refreshKey++ })
        if (d.session.isEmpty()) EmptyNote("Empty")
        d.session.forEach { (k, v) -> KVRow(k, v) }

        SectionHeader("Cookie", onRefresh = { refreshKey++ })
        if (d.cookies.isEmpty()) EmptyNote("Empty")
        d.cookies.forEach { (k, v) -> KVRow(k, v) }

        SectionHeader("Script", onRefresh = { refreshKey++ })
        if (d.scripts.isEmpty()) EmptyNote("Empty")
        d.scripts.forEach { LinkRow(it) }

        SectionHeader("Stylesheet", onRefresh = { refreshKey++ })
        if (d.styles.isEmpty()) EmptyNote("Empty")
        d.styles.forEach { LinkRow(it) }

        SectionHeader("Iframe", onRefresh = { refreshKey++ })
        if (d.iframes.isEmpty()) EmptyNote("Empty")
        d.iframes.forEach { LinkRow(it) }

        SectionHeader("Image", onRefresh = { refreshKey++ })
        if (d.images.isEmpty()) EmptyNote("Empty")
        d.images.forEach { LinkRow(it) }
    }
}

private fun readStringArray(o: JSONObject, key: String): List<String> {
    val list = mutableListOf<String>()
    o.optJSONArray(key)?.let { arr ->
        for (i in 0 until arr.length()) list.add(arr.optString(i))
    }
    return list
}

// ---------------- Sources ----------------

@Composable
private fun SourcesTab(webView: WebView?) {
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
            } catch (e: Exception) {
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

// ---------------- Info ----------------

@Composable
private fun InfoTab(webView: WebView?) {
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

// ---------------- Snippets ----------------

@Composable
private fun SnippetsTab(webView: WebView?, store: SnippetStore) {
    var snippets by remember { mutableStateOf(store.all()) }
    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        if (editing) {
            TextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Snippet name") },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            TextField(
                value = code,
                onValueChange = { code = it },
                placeholder = { Text("console.log('hello')", fontFamily = FontFamily.Monospace) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Text(
                    text = "Save",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.clickable {
                        if (name.isNotBlank()) {
                            store.save(name.trim(), code)
                            snippets = store.all()
                            editing = false
                            name = ""
                            code = ""
                        }
                    }
                )
                Spacer(Modifier.width(24.dp))
                Text(
                    text = "Cancel",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { editing = false }
                )
            }
        } else {
            Text(
                text = "NEW SNIPPET",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { editing = true }
            )
        }

        Spacer(Modifier.height(16.dp))

        snippets.forEach { snippet ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = snippet.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = snippet.code,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = {
                    webView?.evaluateJavascript("(function(){try{${snippet.code}}catch(e){}})()") {}
                }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.PlayArrow, "Run", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = {
                    store.remove(snippet.name)
                    snippets = store.all()
                }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ---------------- Settings ----------------

@Composable
private fun SettingsTab() {
    var overrideConsole by remember { mutableStateOf(DevToolsBus.overrideConsole) }
    var catchErrors by remember { mutableStateOf(DevToolsBus.catchGlobalErrors) }
    var clearOnNav by remember { mutableStateOf(DevToolsBus.clearOnNavigate) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        SettingToggle("Override console", overrideConsole) {
            overrideConsole = it
            DevToolsBus.overrideConsole = it
        }
        SettingToggle("Catch global errors", catchErrors) {
            catchErrors = it
            DevToolsBus.catchGlobalErrors = it
        }
        SettingToggle("Clear data on navigation", clearOnNav) {
            clearOnNav = it
            DevToolsBus.clearOnNavigate = it
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Console & error settings apply on next page load.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ---------------- Shared ----------------

@Composable
private fun DetailSection(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
private fun SectionHeader(title: String, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Refresh, "Refresh", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun KVRow(k: String, v: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = k,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF64B5F6),
            modifier = Modifier.width(140.dp)
        )
        Text(
            text = v,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun LinkRow(url: String) {
    Text(
        text = url,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

private fun parseDomNode(o: JSONObject): DomNode {
    val children = mutableListOf<DomNode>()
    o.optJSONArray("n")?.let { arr ->
        for (i in 0 until arr.length()) children.add(parseDomNode(arr.getJSONObject(i)))
    }
    return DomNode(o.optString("t"), o.optString("id"), o.optString("c"), children)
}

private fun walk(
    node: DomNode,
    path: String,
    depth: Int,
    expanded: Map<String, Boolean>,
    out: MutableList<Triple<String, Int, DomNode>>
) {
    out.add(Triple(path, depth, node))
    if (path.isEmpty() || expanded[path] == true) {
        node.children.forEachIndexed { i, child ->
            walk(child, "$path.$i", depth + 1, expanded, out)
        }
    }
}
