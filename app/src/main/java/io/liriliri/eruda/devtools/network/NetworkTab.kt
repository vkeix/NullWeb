package io.liriliri.eruda.devtools.network

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

@Composable
fun NetworkTab() {
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
