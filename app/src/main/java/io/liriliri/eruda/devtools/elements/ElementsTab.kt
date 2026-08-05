package io.liriliri.eruda.devtools.elements

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.liriliri.eruda.devtools.decodeJsString
import io.liriliri.eruda.devtools.parsePairs
import org.json.JSONObject

private data class DomNode(val tag: String, val id: String, val cls: String, val children: List<DomNode>)

private data class NodeInfo(
    val tag: String,
    val attrs: List<Pair<String, String>>,
    val inline: String,
    val comp: List<Pair<String, String>>,
    val rules: List<Pair<String, String>>
)

@Composable
fun ElementsTab(webView: WebView?) {
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
                        .clickable { selectedPath = path }
                        .padding(start = (12 + depth * 16).dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasChildren) {
                        IconButton(
                            onClick = { expanded[path] = !isOpen },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                if (isOpen) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                                contentDescription = "Toggle",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        Spacer(Modifier.width(32.dp))
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
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
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
