package io.liriliri.eruda.devtools

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.liriliri.eruda.SnippetStore
import io.liriliri.eruda.devtools.console.ConsoleTab
import io.liriliri.eruda.devtools.elements.ElementsTab
import io.liriliri.eruda.devtools.info.InfoTab
import io.liriliri.eruda.devtools.network.NetworkTab
import io.liriliri.eruda.devtools.resources.ResourcesTab
import io.liriliri.eruda.devtools.settings.SettingsTab
import io.liriliri.eruda.devtools.snippets.SnippetsTab
import io.liriliri.eruda.devtools.sources.SourcesTab

private val TABS = listOf(
    "Console",
    "Elements",
    "Network",
    "Resources",
    "Sources",
    "Info",
    "Snippets",
    "Settings"
)

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
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
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
            3 -> ResourcesTab(webView) { selectedTab = 4 }
            4 -> SourcesTab(webView)
            5 -> InfoTab(webView)
            6 -> SnippetsTab(webView, snippetStore)
            7 -> SettingsTab()
        }
    }
}
