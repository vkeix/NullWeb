package io.liriliri.eruda.devtools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

fun decodeJsString(result: String?): String? {
    return try {
        if (result == null || result == "null") null
        else JSONObject("{\"v\":$result}").getString("v")
    } catch (e: Exception) {
        null
    }
}

fun parsePairs(o: JSONObject?): List<Pair<String, String>> {
    val list = mutableListOf<Pair<String, String>>()
    o?.keys()?.forEach { key -> list.add(key to o.optString(key)) }
    return list
}

fun readStringArray(o: JSONObject, key: String): List<String> {
    val list = mutableListOf<String>()
    o.optJSONArray(key)?.let { arr ->
        for (i in 0 until arr.length()) list.add(arr.optString(i))
    }
    return list
}

data class ResourcesData(
    val local: List<Pair<String, String>>,
    val session: List<Pair<String, String>>,
    val cookies: List<Pair<String, String>>,
    val scripts: List<String>,
    val styles: List<String>,
    val iframes: List<String>,
    val images: List<String>
)

data class PageInfo(
    val url: String,
    val title: String,
    val charset: String,
    val doctype: String,
    val ua: String,
    val lang: String,
    val screen: String,
    val viewport: String
)

@Composable
fun DetailSection(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
fun SectionHeader(title: String, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Filled.Refresh, "Refresh", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun KVRow(k: String, v: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = k,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF64B5F6),
            modifier = Modifier.width(140.dp)
        )
        Text(
            text = v,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun EmptyNote(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
