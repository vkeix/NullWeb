package io.liriliri.eruda.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.liriliri.eruda.TabManager
import kotlin.math.roundToInt

private val GROUP_COLORS = listOf(
    0xFF7C4DFF, 0xFF00BFA5, 0xFFFF6D00, 0xFFD50000, 0xFF2979FF, 0xFF64DD17
)

private fun groupColor(index: Int): Color = Color(GROUP_COLORS[index % GROUP_COLORS.size])

private data class DragState(
    val draggedId: Long,
    val position: Offset,
    val hoverId: Long?
)

@Composable
fun TabSwitcherOverlay(
    tabs: List<TabManager.Tab>,
    thumbnails: Map<Long, Bitmap>,
    groups: List<TabManager.TabGroup>,
    activeTabIndex: Int,
    onTabClick: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    onGroupTabs: (Long, Long) -> Unit,
    onDissolveGroup: (Long) -> Unit,
    onClose: () -> Unit
) {
    var dragState by remember { mutableStateOf<DragState?>(null) }
    val tabBounds = remember { mutableStateMapOf<Long, Rect>() }
    val expandedGroups = remember { mutableSetOf<Long>() }
    val density = LocalDensity.current

    fun hitTest(pos: Offset, exclude: Long): Long? =
        tabBounds.entries.firstOrNull { it.key != exclude && it.value.contains(pos) }?.key

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f))
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${tabs.size} Tabs",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val emittedGroups = mutableSetOf<Long>()

                tabs.forEachIndexed { index, tab ->
                    val groupId = tab.groupId

                    if (groupId == null) {
                        item(key = "tab${tab.id}") {
                            TabCard(
                                tab = tab,
                                preview = thumbnails[tab.id],
                                isActive = index == activeTabIndex,
                                groupColor = null,
                                dimmed = dragState?.draggedId == tab.id,
                                hovered = dragState?.hoverId == tab.id,
                                modifier = Modifier
                                    .onGloballyPositioned { tabBounds[tab.id] = it.boundsInWindow() }
                                    .dragModifier(
                                        tabId = tab.id,
                                        getDragState = { dragState },
                                        setDragState = { dragState = it },
                                        tabBounds = tabBounds,
                                        hitTest = { pos -> hitTest(pos, tab.id) },
                                        onGroupTabs = onGroupTabs
                                    ),
                                onClick = { onTabClick(index) },
                                onClose = { onTabClose(index) }
                            )
                        }
                    } else if (groupId !in emittedGroups) {
                        emittedGroups.add(groupId)
                        val members = tabs.filter { it.groupId == groupId }
                        val group = groups.firstOrNull { it.id == groupId }
                        val color = groupColor(group?.colorIndex ?: 0)

                        if (groupId in expandedGroups) {
                            item(span = { GridItemSpan(2) }, key = "ghead$groupId") {
                                GroupHeader(
                                    color = color,
                                    count = members.size,
                                    onCollapse = { expandedGroups.remove(groupId) },
                                    onDissolve = {
                                        expandedGroups.remove(groupId)
                                        onDissolveGroup(groupId)
                                    }
                                )
                            }
                            members.forEach { member ->
                                val memberIndex = tabs.indexOfFirst { it.id == member.id }
                                item(key = "tab${member.id}") {
                                    TabCard(
                                        tab = member,
                                        preview = thumbnails[member.id],
                                        isActive = memberIndex == activeTabIndex,
                                        groupColor = color,
                                        dimmed = dragState?.draggedId == member.id,
                                        hovered = dragState?.hoverId == member.id,
                                        modifier = Modifier
                                            .onGloballyPositioned { tabBounds[member.id] = it.boundsInWindow() }
                                            .dragModifier(
                                                tabId = member.id,
                                                getDragState = { dragState },
                                                setDragState = { dragState = it },
                                                tabBounds = tabBounds,
                                                hitTest = { pos -> hitTest(pos, member.id) },
                                                onGroupTabs = onGroupTabs
                                            ),
                                        onClick = { onTabClick(memberIndex) },
                                        onClose = { onTabClose(memberIndex) }
                                    )
                                }
                            }
                        } else {
                            item(key = "group$groupId") {
                                GroupCard(
                                    color = color,
                                    members = members,
                                    previews = thumbnails,
                                    onClick = { expandedGroups.add(groupId) }
                                )
                            }
                        }
                    }
                }
            }
        }

        dragState?.let { ds ->
            val tab = tabs.firstOrNull { it.id == ds.draggedId }
            if (tab != null) {
                val w = with(density) { 160.dp.toPx() }
                val h = with(density) { 110.dp.toPx() }
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (ds.position.x - w / 2).roundToInt(),
                                (ds.position.y - h / 2).roundToInt()
                            )
                        }
                        .width(160.dp)
                        .height(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(groupColor(0))
                        .padding(10.dp)
                ) {
                    Text(
                        text = tab.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun Modifier.dragModifier(
    tabId: Long,
    getDragState: () -> DragState?,
    setDragState: (DragState?) -> Unit,
    tabBounds: Map<Long, Rect>,
    hitTest: (Offset) -> Long?,
    onGroupTabs: (Long, Long) -> Unit
): Modifier = this.then(
    Modifier.pointerInput(tabId) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                val origin = tabBounds[tabId]?.topLeft ?: Offset.Zero
                setDragState(DragState(tabId, origin + offset, null))
            },
            onDrag = { change, _ ->
                change.consume()
                val current = getDragState() ?: return@detectDragGesturesAfterLongPress
                val origin = tabBounds[tabId]?.topLeft ?: Offset.Zero
                val pos = origin + change.position
                setDragState(current.copy(position = pos, hoverId = hitTest(pos)))
            },
            onDragEnd = {
                val ds = getDragState()
                setDragState(null)
                if (ds?.hoverId != null) onGroupTabs(ds.draggedId, ds.hoverId)
            },
            onDragCancel = { setDragState(null) }
        )
    }
)

@Composable
private fun GroupHeader(
    color: Color,
    count: Int,
    onCollapse: () -> Unit,
    onDissolve: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Tab group • $count",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDissolve, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Link,
                contentDescription = "Ungroup",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onCollapse, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = "Collapse",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun GroupCard(
    color: Color,
    members: List<TabManager.Tab>,
    previews: Map<Long, Bitmap>,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, color, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(color)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            previews[members.first().id]?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${members.size}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(
                text = members.first().title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${members.size} tabs in group",
                fontSize = 11.sp,
                color = color
            )
        }
    }
}

@Composable
private fun TabCard(
    tab: TabManager.Tab,
    preview: Bitmap?,
    isActive: Boolean,
    groupColor: Color?,
    dimmed: Boolean,
    hovered: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    val borderColor = when {
        hovered -> groupColor(0)
        isActive -> MaterialTheme.colorScheme.primary
        groupColor != null -> groupColor
        else -> Color.Transparent
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(220.dp)
            .alpha(if (dimmed) 0.3f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        if (groupColor != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(groupColor)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = (tab.title.firstOrNull()?.uppercase() ?: "?"),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tab.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close tab",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = tab.url,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
