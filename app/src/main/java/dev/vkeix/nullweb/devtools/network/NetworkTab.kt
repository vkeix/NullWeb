package dev.vkeix.nullweb.devtools.network

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vkeix.nullweb.DevToolsBus
import dev.vkeix.nullweb.devtools.DetailSection
import dev.vkeix.nullweb.devtools.EmptyNote
import dev.vkeix.nullweb.devtools.KVRow

private val FILTERS = listOf("all", "media", "xhr", "js", "css")

private fun matchesFilter(entry: DevToolsBus.NetEntry, filter: String): Boolean = when (filter) {
    "media" -> entry.type.startsWith("video") || entry.type.startsWith("audio") ||
        entry.type.contains("mpegurl") || entry.type.contains("octet-stream") ||
        listOf(".m3u8", ".mp4", ".mkv", ".ts", ".m4s", ".mp3", ".m4a").any { entry.url.contains(it) }
    "js" -> entry.type.contains("javascript")
    "css" -> entry.type.contains("css")
    "xhr" -> entry.type.contains("json") || entry.type.contains("xml")
    else -> true
}

@Composable
fun NetworkTab() {
    val entries by DevToolsBus.net.collectAsState()
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var filter by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }

    val selected = entries.firstOrNull { it.id == selectedId }
    if (selected != null) {
        NetDetailView(selected, onBack = { selectedId = null })
        return
    }

    val visible = entries.filter {
        matchesFilter(it, filter) && (query.isEmpty() || it.url.contains(query, ignoreCase = true))
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FILTERS.forEach { f ->
                Text(
                    text = f,
                    fontSize = 12.sp,
                    fontWeight = if (filter == f) FontWeight.Bold else FontWeight.Medium,
                    color = if (filter == f) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { filter = f }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "${visible.size}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = { DevToolsBus.clearNet() }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }

        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Filter URLs… (e.g. m3u8)", fontSize = 12.sp) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(44.dp)
        )

        LazyColumn(Modifier.weight(1f)) {
            items(visible) { entry ->
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
                        Text("${entry.time}ms", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text("${entry.size}B", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(entry.type, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun NetDetailView(entry: DevToolsBus.NetEntry, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

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
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(entry.url))
                Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Filled.ContentCopy, "Copy URL", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
