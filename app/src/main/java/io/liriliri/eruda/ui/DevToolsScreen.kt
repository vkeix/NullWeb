package io.liriliri.eruda.ui

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class DomNode(
    val tag: String,
    val id: String,
    val cls: String,
    val children: List<DomNode>
)

@Composable
fun DevToolsScreen(
    webView: WebView?,
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
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DevToolsTab("Console", selectedTab == 0) { selectedTab = 0 }
            Spacer(Modifier.width(24.dp))
            DevToolsTab("Elements", selectedTab == 1) { selectedTab = 1 }
        }

        Spacer(Modifier.height(8.dp))

        if (selectedTab == 0) {
            ConsoleTab()
        } else {
            ElementsTab(webView)
        }
    }
}

@Composable
private fun DevToolsTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onBackground
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .height(2.dp)
                .width(56.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent
                )
        )
    }
}

@Composable
private fun ConsoleTab() {
    val logs by DevToolsBus.logs.collectAsState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${logs.size} entries",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { DevToolsBus.clear() }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    "Clear console",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(logs) { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = timeFormat.format(Date(entry.time)),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
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
    }
}

@Composable
private fun ElementsTab(webView: WebView?) {
    var root by remember { mutableStateOf<DomNode?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(webView, refreshKey) {
        webView?.evaluateJavascript(DOM_SCRIPT) { result ->
            root = try {
                val decoded = JSONObject("{\"v\":$result}").getString("v")
                parseNode(JSONObject(decoded))
            } catch (e: Exception) {
                null
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DOM tree",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { refreshKey++ }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Refresh,
                    "Refresh",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (root == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No DOM captured",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val rows = mutableListOf<Triple<String, Int, DomNode>>()
            walk(root!!, "", 0, expanded, rows)

            LazyColumn(Modifier.fillMaxSize()) {
                items(rows, key = { it.first }) { (path, depth, node) ->
                    DomRow(path, depth, node, expanded)
                }
            }
        }
    }
}

@Composable
private fun DomRow(
    path: String,
    depth: Int,
    node: DomNode,
    expanded: MutableMap<String, Boolean>
) {
    val hasChildren = node.children.isNotEmpty()
    val isOpen = path.isEmpty() || expanded[path] == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (hasChildren) expanded[path] = !isOpen }
            .padding(start = (16 + depth * 16).dp, end = 16.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasChildren) {
            Icon(
                if (isOpen) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
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
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
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

private fun parseNode(o: JSONObject): DomNode {
    val children = mutableListOf<DomNode>()
    val arr = o.optJSONArray("n")
    if (arr != null) {
        for (i in 0 until arr.length()) {
            children.add(parseNode(arr.getJSONObject(i)))
        }
    }
    return DomNode(o.optString("t"), o.optString("id"), o.optString("c"), children)
}

private val DOM_SCRIPT = """
    (function(){
        function node(el, depth){
            var o = {
                t: (el.tagName || '').toLowerCase(),
                id: el.id || '',
                c: (typeof el.className === 'string') ? el.className : '',
                n: []
            };
            if (depth < 6 && el.children) {
                var max = Math.min(el.children.length, 60);
                for (var i = 0; i < max; i++) o.n.push(node(el.children[i], depth + 1));
            }
            return o;
        }
        return JSON.stringify(node(document.documentElement, 0));
    })();
"""
