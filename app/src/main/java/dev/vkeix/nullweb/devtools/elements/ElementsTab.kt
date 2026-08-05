package dev.vkeix.nullweb.devtools.elements

import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vkeix.nullweb.devtools.DetailSection
import dev.vkeix.nullweb.devtools.EmptyNote
import dev.vkeix.nullweb.devtools.KVRow
import dev.vkeix.nullweb.devtools.decodeJsString
import dev.vkeix.nullweb.devtools.parsePairs
import org.json.JSONObject

private data class DomNode(val tag: String, val id: String, val cls: String, val children: List<DomNode>)

private data class NodeInfo(
    val tag: String,
    val attrs: List<Pair<String, String>>,
    val inline: String,
    val comp: List<Pair<String, String>>,
    val rules: List<Pair<String, String>>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElementsTab(webView: WebView?) {
    var root by remember { mutableStateOf<DomNode?>(null) }
    var selectedPath by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<NodeInfo?>(null) }
    var actionNode by remember { mutableStateOf<Pair<String, DomNode>?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

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
                        .clickable { actionNode = path to node }
                        .padding(start = (4 + depth * 12).dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasChildren) {
                        IconButton(
                            onClick = { expanded[path] = !isOpen },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                if (isOpen) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else {
                        Spacer(Modifier.width(28.dp))
                    }
                    Text(
                        text = buildString {
                            append("<${node.tag}")
                            if (node.id.isNotEmpty()) append("#${node.id}")
                            if (node.cls.isNotEmpty()) append(".${node.cls.trim().split(Regex("\\s+")).joinToString(".")}")
                            append(">")
                        },
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 6.dp)
                    )
                }
            }
        }
    }

    actionNode?.let { (path, node) ->
        ModalBottomSheet(onDismissRequest = { actionNode = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = buildString {
                        append("<${node.tag}")
                        if (node.id.isNotEmpty()) append("#${node.id}")
                        if (node.cls.isNotEmpty()) append(".${node.cls.trim().split(Regex("\\s+")).first()}")
                        append(">")
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(Modifier.height(12.dp))

                SheetAction(Icons.Filled.Visibility, "Inspect styles & attributes") {
                    actionNode = null
                    selectedPath = path
                }
                SheetAction(Icons.Filled.Highlight, "Highlight on page") {
                    webView?.evaluateJavascript("__dtApi.highlight('$path')") {}
                    actionNode = null
                }
                SheetAction(Icons.Filled.ContentCopy, "Copy selector") {
                    clipboard.setText(AnnotatedString(buildSelector(root, path)))
                    Toast.makeText(context, "Selector copied", Toast.LENGTH_SHORT).show()
                    actionNode = null
                }
                SheetAction(Icons.Filled.ContentCopy, "Copy HTML") {
                    webView?.evaluateJavascript("__dtApi.html('$path')") { result ->
                        val html = decodeJsString(result) ?: ""
                        clipboard.setText(AnnotatedString(html))
                        Toast.makeText(context, "HTML copied", Toast.LENGTH_SHORT).show()
                    }
                    actionNode = null
                }
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun buildSelector(root: DomNode?, path: String): String {
    if (root == null) return ""
    val parts = mutableListOf(root.selectorPart())
    var node = root
    path.split(".").filter { it.isNotEmpty() }.forEach { idx ->
        val i = idx.toIntOrNull() ?: return@forEach
        node = node.children.getOrNull(i) ?: return parts.joinToString(" > ")
        parts.add(node.selectorPart())
    }
    return parts.joinToString(" > ")
}

private fun DomNode.selectorPart(): String = buildString {
    append(tag)
    if (id.isNotEmpty()) append("#").append(id)
    else if (cls.isNotEmpty()) append(".").append(cls.trim().split(Regex("\\s+")).first())
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
        if (info.attrs.isEmpty()) EmptyNote("Empty")
        else info.attrs.forEach { (k, v) -> KVRow(k, v) }

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
