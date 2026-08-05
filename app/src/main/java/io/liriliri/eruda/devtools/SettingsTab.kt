package io.liriliri.eruda.devtools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsTab() {
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
        Text(text = label, color = MaterialTheme.colorScheme.onSurface)
    }
}
