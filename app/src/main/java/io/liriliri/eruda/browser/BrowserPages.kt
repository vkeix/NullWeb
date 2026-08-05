package io.liriliri.eruda.browser

import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.liriliri.eruda.store.BookmarkStore
import io.liriliri.eruda.store.DownloadStore
import io.liriliri.eruda.store.HistoryRanges
import io.liriliri.eruda.store.HistoryStore
import java.io.File

@Composable
fun HistoryScreen(
    entries: List<HistoryStore.Entry>,
    onBack: () -> Unit,
    onRemove: (HistoryStore.Entry) -> Unit,
    onRemoveMany: (List<HistoryStore.Entry>) -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    PageScaffold(
        title = "History",
        onBack = onBack,
        actions = {
            if (entries.isNotEmpty()) {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Outlined.Delete, "Delete history", tint = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    ) {
        if (entries.isEmpty()) {
            EmptyState("No history yet")
        } else {
            LazyColumn {
                HistoryRanges.labels.forEachIndexed { range, label ->
                    val section = entries.filter { HistoryRanges.bucket(it.time) == range }
                    if (section.isNotEmpty()) {
                        item(key = "header$range") {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        items(section, key = { it.time }) { entry ->
                            PageRow(
                                letter = entry.url.hostnameInitial(),
                                title = entry.title,
                                subtitle = entry.url,
                                onClose = { onRemove(entry) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        RangeDeleteDialog(
            onCancel = { showDeleteDialog = false },
            onDelete = { option ->
                val cutoff = HistoryRanges.deleteCutoff(option)
                onRemoveMany(entries.filter { it.time >= cutoff })
                showDeleteDialog = false
            }
        )
    }
}

@Composable
private fun RangeDeleteDialog(
    onCancel: () -> Unit,
    onDelete: (Int) -> Unit
) {
    var selected by remember { mutableStateOf(1) }
    val options = listOf("Last hour", "Today", "Last 7 days", "All")

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "Delete history",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                options.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = index }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == index,
                            onClick = { selected = index }
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(selected) }) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BookmarksScreen(
    bookmarks: List<BookmarkStore.Bookmark>,
    onBack: () -> Unit,
    onOpen: (BookmarkStore.Bookmark) -> Unit,
    onRemove: (BookmarkStore.Bookmark) -> Unit
) {
    PageScaffold(title = "Bookmarks", onBack = onBack) {
        if (bookmarks.isEmpty()) {
            EmptyState("No bookmarks yet")
        } else {
            LazyColumn {
                items(bookmarks, key = { it.url }) { bookmark ->
                    PageRow(
                        letter = bookmark.url.hostnameInitial(),
                        title = bookmark.title,
                        subtitle = bookmark.url,
                        onClose = { onRemove(bookmark) },
                        onClick = { onOpen(bookmark) }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen(
    entries: List<DownloadStore.Entry>,
    onBack: () -> Unit,
    onDelete: (DownloadStore.Entry) -> Unit
) {
    PageScaffold(title = "Downloads", onBack = onBack) {
        if (entries.isEmpty()) {
            EmptyState("No downloads yet")
        } else {
            LazyColumn {
                items(entries, key = { it.id }) { entry ->
                    DownloadRow(entry, onDelete)
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    entry: DownloadStore.Entry,
    onDelete: (DownloadStore.Entry) -> Unit
) {
    val file = remember(entry) {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), entry.name)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = entry.name.substringAfterLast(".").take(3).uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${formatSize(file.length())} • ${entry.url.substringAfter("://").substringBefore("/")}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { onDelete(entry) }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Delete download",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> "0 kB"
    bytes < 1024 * 1024 -> "${bytes / 1024} kB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onClearHistory: () -> Unit,
    onClearBookmarks: () -> Unit,
    onClearSearches: () -> Unit
) {
    PageScaffold(title = "Settings", onBack = onBack) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "CLEAR BROWSING DATA",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.size(12.dp))
            SettingsRow("Clear history", onClearHistory)
            Spacer(Modifier.size(8.dp))
            SettingsRow("Clear bookmarks", onClearBookmarks)
            Spacer(Modifier.size(8.dp))
            SettingsRow("Clear search history", onClearSearches)
        }
    }
}

@Composable
private fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
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
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            actions()
        }
        Box(Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
private fun PageRow(
    letter: String,
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = letter,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun String.hostnameInitial(): String {
    val host = substringAfter("://").substringBefore("/").substringBefore("?")
    return (host.firstOrNull()?.uppercase() ?: "?")
}
