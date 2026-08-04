package io.liriliri.eruda.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BrowserToolbar(
    committedUrl: String,
    tabCount: Int,
    suggestions: List<String>,
    onUrlSubmit: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onHomeClick: () -> Unit,
    onNewTabClick: () -> Unit,
    onTabCountClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    var isOmniboxFocused by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(visible = !isOmniboxFocused) {
                Text(
                    text = "G",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .clickable { onHomeClick() }
                        .padding(end = 16.dp)
                )
            }

            Omnibox(
                committedUrl = committedUrl,
                suggestions = suggestions,
                onUrlSubmit = onUrlSubmit,
                onQueryChange = onQueryChange,
                onFocusChanged = { isOmniboxFocused = it },
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(12.dp))

            AnimatedVisibility(visible = !isOmniboxFocused) {
                Row {
                    IconButton(onClick = onNewTabClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "New tab",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    TabCountBox(count = tabCount, onClick = onTabCountClick)

                    IconButton(onClick = onMenuClick, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Menu",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Omnibox(
    committedUrl: String,
    suggestions: List<String>,
    onUrlSubmit: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // "Draft" text is what the user is currently typing
    var draftText by rememberSaveable { mutableStateOf(committedUrl) }
    var isFocused by remember { mutableStateOf(false) }
    
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    // Sync from external URL changes (e.g. navigating via links)
    // Only sync if we aren't currently focused/editing
    LaunchedEffect(committedUrl, isFocused) {
        if (!isFocused) {
            draftText = committedUrl
        }
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { focusRequester.requestFocus() }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (draftText.isEmpty()) {
                Text(
                    text = "Search or type URL",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            BasicTextField(
                value = draftText,
                onValueChange = { newText ->
                    draftText = newText
                    onQueryChange(newText)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        isFocused = state.isFocused
                        onFocusChanged(state.isFocused)
                        
                        if (state.isFocused) {
                            draftText = "" // CLEAR on focus
                        } else {
                            // BLUR: Revert to committed URL if user didn't submit
                            draftText = committedUrl
                        }
                    },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onGo = {
                        onUrlSubmit(draftText)
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                ),
                singleLine = true
            )
        }

        // Suggestions Dropdown
        DropdownMenu(
            expanded = isFocused && suggestions.isNotEmpty(),
            onDismissRequest = { },
            offset = DpOffset(0.dp, 8.dp)
        ) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { 
                        Text(
                            text = suggestion, 
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        ) 
                    },
                    onClick = {
                        onUrlSubmit(suggestion)
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                )
            }
        }
    }
}

@Composable
private fun TabCountBox(count: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.5.dp, MaterialTheme.colorScheme.onBackground, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "∞" else count.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BrowserToolbarPreview() {
    MaterialTheme {
        BrowserToolbar(
            committedUrl = "github.com",
            tabCount = 1,
            suggestions = listOf("github", "github copilot"),
            onUrlSubmit = {},
            onQueryChange = {},
            onHomeClick = {},
            onNewTabClick = {},
            onTabCountClick = {},
            onMenuClick = {}
        )
    }
}
