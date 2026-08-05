package dev.vkeix.nullweb.devtools.snippets

import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vkeix.nullweb.store.SnippetStore
import dev.vkeix.nullweb.devtools.shared.DetailSection

@Composable
fun SnippetsTab(webView: WebView?, snippetStore: SnippetStore) {
    var snippets by remember { mutableStateOf(snippetStore.all()) }
    var showEditor by remember { mutableStateOf(false) }
    var editingSnippet by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        DetailSection("Code Snippets")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${snippets.size} snippets",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                editingSnippet = null
                showEditor = true
            }) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Add snippet",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (showEditor) {
            SnippetEditor(
                initialName = editingSnippet?.first ?: "",
                initialCode = editingSnippet?.second ?: "",
                onSave = { name, code ->
                    snippetStore.save(name, code)
                    snippets = snippetStore.all()
                    showEditor = false
                    editingSnippet = null
                },
                onCancel = {
                    showEditor = false
                    editingSnippet = null
                }
            )
        } else {
            snippets.forEach { (name, code) ->
                SnippetRow(
                    name = name,
                    code = code,
                    onRun = {
                        webView?.evaluateJavascript(code, null)
                    },
                    onEdit = {
                        editingSnippet = name to code
                        showEditor = true
                    },
                    onDelete = {
                        snippetStore.delete(name)
                        snippets = snippetStore.all()
                    }
                )
            }
        }
    }
}

@Composable
private fun SnippetEditor(
    initialName: String,
    initialCode: String,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var code by remember { mutableStateOf(initialCode) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        TextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Snippet name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        TextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Code") },
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            maxLines = 6
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onSave(name, code) }) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun SnippetRow(
    name: String,
    code: String,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = code,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onRun) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Run",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
