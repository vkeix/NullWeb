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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
    0xFFF2D888, 0xFFFCB6A0, 0xFFF8B8C6, 0xFFF3C0DD, 0xFFD9C7F7,
    0xFFAEDCFA, 0xFFA5DDE9, 0xFFAEE5C8, 0xFFC9C9C9
)

private fun groupColor(index: Int): Color = Color(GROUP_COLORS[index % GROUP_COLORS.size])

private val GROUP_TEXT = Color(0xFF1F1F1F)
private val CELL_BG = Color(0xFF141414)

private data class DragState(
    val draggedId: Long,
    val position: Offset,
    val hover: DropTarget?
)

private sealed class DropTarget {
    data class Tab(val id: Long) : DropTarget()
    data class Group(val id: Long) : DropTarget()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherOverlay(
    tabs: List<TabManager.Tab>,
    thumbnails: Map<Long, Bitmap>,
    groups: List<TabManager.TabGroup>,
    activeTabIndex: Int,
    onTabClick: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    onCreateGroup: (Long, Long, String, Int) -> Unit,
    onAddToGroup: (Long, Long) -> Unit,
    onDissolveGroup: (Long) -> Unit,
    onClose: () -> Unit
) {
    var dragState by remember { mutableStateOf<DragState?>(null) }
    var pendingGroup by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var openGroupId by remember { mutableStateOf<Long?>(null) }
    val tabBounds = remember { mutableStateMapOf<Long, Rect>() }
    val groupBounds = remember { mutableStateMapOf<Long, Rect>() }

    fun hitTest(pos: Offset, excludeTab: Long): DropTarget? {
        tabBounds.entries.firstOrNull { it.key != excludeTab && it.value.contains(pos) }?.let {
            return DropTarget.Tab(it.key)
        }
        groupBounds.entries.firstOrNull { it.value.contains(pos) }?.let {
            return DropTarget.Group(it.key)
        }
        return null
    }

    fun indexOf(tabId: Long) = tabs.indexOfFirst { it.id == tabId }

    val handleDrop: (Long, DropTarget) -> Unit = { dragged, target ->
        when (target) {
            is DropTarget.Group -> onAddToGroup(dragged, target.id)
            is DropTarget.Tab -> {
                val t = tabs.firstOrNull { it.id == target.id }
                if (t?.groupId != null) {
                    onAddToGroup(dragged, t.groupId!!)
                } else {
                    openGroupId = null
                    pendingGroup = dragged to target.id
                }
            }
        }
    }

    val dragFor: (Long) -> Modifier = { id ->
        Modifier
            .onGloballyPositioned { tabBounds[id] = it.boundsInWindow() }
            .dragModifier(
                tabId = id,
                getDragState = { dragState },
                setDragState = { dragState = it },
                tabBounds = tabBounds,
                hitTest = { pos -> hitTest(pos, id) },
                onDrop = handleDrop
            )
    }

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
                                borderColor = when {
                                    dragState?.hover == DropTarget.Tab(tab.id) -> groupColor(0)
                                    index == activeTabIndex -> MaterialTheme.colorScheme.primary
                                    else -> null
                                },
                                dimmed = dragState?.draggedId == tab.id,
                                modifier = dragFor(tab.id),
                                onClick = { onTabClick(index) },
                                onClose = { onTabClose(index) }
                            )
                        }
                    } else if (groupId !in emittedGroups) {
                        emittedGroups.add(groupId)
                        val members = tabs.filter { it.groupId == groupId }
                        val group = groups.firstOrNull { it.id == groupId }

                        item(key = "group$groupId") {
                            GroupCard(
                                name = group?.name ?: "Group",
                                color = groupColor(group?.colorIndex ?: 0),
                                members = members,
                                previews = thumbnails,
                                hovered = dragState?.hover == DropTarget.Group(groupId),
                                modifier = Modifier.onGloballyPositioned {
                                    groupBounds[groupId] = it.boundsInWindow()
                                },
                                onClick = { openGroupId = groupId }
                            )
                        }
                    }
                }
            }
        }

        dragState?.let { DragPreview(it, tabs, Offset.Zero) }
    }

    pendingGroup?.let { pair ->
        CreateGroupSheet(
            defaultName = "Group ${groups.size + 1}",
            onSave = { name, colorIndex ->
                onCreateGroup(pair.first, pair.second, name, colorIndex)
                pendingGroup = null
            },
            onDismiss = { pendingGroup = null }
        )
    }

    openGroupId?.let { gid ->
        val group = groups.firstOrNull { it.id == gid }
        val members = tabs.filter { it.groupId == gid }
        if (group == null || members.isEmpty()) {
            openGroupId = null
        } else {
            GroupSheet(
                group = group,
                members = members,
                allTabs = tabs,
                thumbnails = thumbnails,
                activeTabIndex = activeTabIndex,
                indexOf = ::indexOf,
                dragState = dragState,
                dragFor = dragFor,
                onTabClick = { index ->
                    openGroupId = null
                    onTabClick(index)
                },
                onTabClose = onTabClose,
                onDissolve = {
                    openGroupId = null
                    onDissolveGroup(gid)
                },
                onDismiss = { openGroupId = null }
            )
        }
    }
}

@Composable
private fun DragPreview(
    ds: DragState,
    tabs: List<TabManager.Tab>,
    origin: Offset
) {
    val density = LocalDensity.current
    val tab = tabs.firstOrNull { it.id == ds.draggedId } ?: return
    val w = with(density) { 150.dp.toPx() }
    val h = with(density) { 100.dp.toPx() }
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (ds.position.x - origin.x - w / 2).roundToInt(),
                    (ds.position.y - origin.y - h / 2).roundToInt()
                )
            }
            .width(150.dp)
            .height(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(10.dp)
    ) {
        Text(
            text = tab.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun Modifier.dragModifier(
    tabId: Long,
    getDragState: () -> DragState?,
    setDragState: (DragState?) -> Unit,
    tabBounds: Map<Long, Rect>,
    hitTest: (Offset) -> DropTarget?,
    onDrop: (Long, DropTarget) -> Unit
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
                setDragState(current.copy(position = pos, hover = hitTest(pos)))
            },
            onDragEnd = {
                val ds = getDragState()
                setDragState(null)
                if (ds?.hover != null) onDrop(ds.draggedId, ds.hover)
            },
            onDragCancel = { setDragState(null) }
        )
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateGroupSheet(
    defaultName: String,
    onSave: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    var colorIndex by remember { mutableStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Create tab group",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onSave(name.ifBlank { defaultName }, colorIndex) }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Save",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    (0 until 5).forEach { i ->
                        ColorSwatch(i, colorIndex) { colorIndex = it }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    (5 until GROUP_COLORS.size).forEach { i ->
                        ColorSwatch(i, colorIndex) { colorIndex = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(index: Int, selected: Int, onSelect: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (selected == index) {
                    Modifier.border(
                        3.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(10.dp)
                    )
                } else Modifier
            )
            .background(groupColor(index))
            .clickable { onSelect(index) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupSheet(
    group: TabManager.TabGroup,
    members: List<TabManager.Tab>,
    allTabs: List<TabManager.Tab>,
    thumbnails: Map<Long, Bitmap>,
    activeTabIndex: Int,
    indexOf: (Long) -> Int,
    dragState: DragState?,
    dragFor: (Long) -> Modifier,
    onTabClick: (Int) -> Unit,
    onTabClose: (Int) -> Unit,
    onDissolve: () -> Unit,
    onDismiss: () -> Unit
) {
    val color = groupColor(group.colorIndex)
    var menuOpen by remember { mutableStateOf(false) }
    var sheetOrigin by remember { mutableStateOf(Offset.Zero) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Box(
            modifier = Modifier.onGloballyPositioned {
                sheetOrigin = it.boundsInWindow().topLeft
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 40.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(color)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = group.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Group options",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Ungroup") },
                                onClick = {
                                    menuOpen = false
                                    onDissolve()
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                members.chunked(2).forEach { rowMembers ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowMembers.forEach { member ->
                            TabCard(
                                tab = member,
                                preview = thumbnails[member.id],
                                borderColor = if (indexOf(member.id) == activeTabIndex)
                                    MaterialTheme.colorScheme.primary else null,
                                dimmed = dragState?.draggedId == member.id,
                                modifier = dragFor(member.id).weight(1f),
                                onClick = { onTabClick(indexOf(member.id)) },
                                onClose = { onTabClose(indexOf(member.id)) }
                            )
                        }
                        if (rowMembers.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            dragState?.let { DragPreview(it, allTabs, sheetOrigin) }
        }
    }
}

@Composable
private fun GroupCard(
    name: String,
    color: Color,
    members: List<TabManager.Tab>,
    previews: Map<Long, Bitmap>,
    hovered: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                if (hovered) 3.dp else 2.dp,
                if (hovered) groupColor(0) else color,
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = GROUP_TEXT,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.MoreVert,
                contentDescription = null,
                tint = GROUP_TEXT,
                modifier = Modifier.size(16.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val cells = members.take(4) + List((4 - members.size.coerceAtMost(4)).coerceAtLeast(0)) { null }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                GroupCell(cells[0], previews, Modifier.weight(1f))
                GroupCell(cells[1], previews, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                GroupCell(cells[2], previews, Modifier.weight(1f))
                GroupCell(cells[3], previews, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GroupCell(
    tab: TabManager.Tab?,
    previews: Map<Long, Bitmap>,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(CELL_BG)
    ) {
        tab?.let { t ->
            previews[t.id]?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun TabCard(
    tab: TabManager.Tab,
    preview: Bitmap?,
    borderColor: Color?,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .alpha(if (dimmed) 0.3f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (borderColor != null) Modifier.border(2.dp, borderColor, RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (tab.title.firstOrNull()?.uppercase() ?: "?"),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = tab.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 10.dp)
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CELL_BG),
            contentAlignment = Alignment.Center
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}
