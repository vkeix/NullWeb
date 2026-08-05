package io.liriliri.eruda.devtools.console

import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.liriliri.eruda.DevToolsBus
import io.liriliri.eruda.devtools.decodeJsString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConsoleTab(webView: WebView?) {
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

        Spacer(Modifier.height(1.dp))

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
