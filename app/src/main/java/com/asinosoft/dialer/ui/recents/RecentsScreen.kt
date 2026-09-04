package com.asinosoft.dialer.ui.recents

import android.annotation.SuppressLint
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.content.Context
import android.telephony.SubscriptionManager
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.asinosoft.dialer.data.model.FavoriteTab
import com.asinosoft.dialer.ui.components.FloatingStickyDateHeader
import com.asinosoft.dialer.ui.components.LazyListVerticalScrollbar
import com.asinosoft.dialer.ui.dialer.SearchDialerScreen
import com.asinosoft.dialer.ui.recents.components.AddFavoriteDialog
import com.asinosoft.dialer.ui.recents.components.AppSettingsDialog
import com.asinosoft.dialer.ui.recents.components.AvatarBitmapCache
import com.asinosoft.dialer.ui.recents.components.CallFilterDialog
import com.asinosoft.dialer.ui.recents.components.CallLogAddContactDialog
import com.asinosoft.dialer.ui.recents.components.CallLogAddToExistingContactDialog
import com.asinosoft.dialer.ui.recents.components.ContactDetailDialog
import com.asinosoft.dialer.ui.recents.components.FavoriteContactCard
import com.asinosoft.dialer.ui.recents.components.FavoritesTopBar
import com.asinosoft.dialer.ui.recents.components.SwipeableCallLogCard
import com.asinosoft.dialer.ui.recents.components.UnsavedNumberChoiceDialog
import com.asinosoft.dialer.ui.theme.SamsungGreen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@SuppressLint("FrequentlyChangingValue")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    viewModel: RecentsViewModel,
    onCall: (String, Int?) -> Unit,
    onSms: (String) -> Unit
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val callLogs by viewModel.recentCalls.collectAsState(initial = emptyList())
    val allFavorites by viewModel.favorites.collectAsState()
    val favorites by viewModel.activeTabFavorites.collectAsState(initial = emptyList())
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val selectedFavorite by viewModel.selectedFavorite.collectAsState()
    val isTopBarVisible by viewModel.isTopBarVisible.collectAsState()
    val isAddFavoriteOpen by viewModel.isAddFavoriteOpen.collectAsState()
    val isAppSettingsOpen by viewModel.isAppSettingsOpen.collectAsState()
    val favoriteRowsCount by viewModel.favoriteRowsCount.collectAsState()
    val contactDetailToShow by viewModel.contactDetailToShow.collectAsState()
    val unsavedNumberFlow by viewModel.unsavedNumberFlow.collectAsState()

    val hasLoadedCallLogs by viewModel.hasLoadedCallLogs.collectAsState()
    val showHint by viewModel.showSwipeHint.collectAsState()
    var initialScrollDone by remember { mutableStateOf(false) }
    var listReady by remember { mutableStateOf(hasLoadedCallLogs || callLogs.isNotEmpty()) }

    LaunchedEffect(hasLoadedCallLogs, callLogs) {
        if (hasLoadedCallLogs || callLogs.isNotEmpty()) {
            listReady = true
        }
    }

    val activeSimCount = remember(context) {
        try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            @Suppress("MissingPermission")
            val count = sm?.activeSubscriptionInfoCount ?: 1
            if (count > 1) count else 1
        } catch (_: Exception) {
            1
        }
    }

    val callTypeFilter by viewModel.callTypeFilter.collectAsState()
    val simFilter by viewModel.simFilter.collectAsState()
    val isFilterActive by viewModel.isFilterActive.collectAsState()
    var showCallFilterDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    var draggingContactId by remember { mutableStateOf<String?>(null) }
    var dragFromIndex by remember { mutableIntStateOf(-1) }
    var dragToIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    // Ref (not Compose state) — updating bounds during scroll must not recompose the screen
    val favoritesBoundsRef = remember { object { var value: Rect = Rect.Zero } }

    LaunchedEffect(isTopBarVisible) {
        if (isTopBarVisible) favoritesBoundsRef.value = Rect.Zero
    }

    val favoriteRows = remember(favorites) {
        if (favorites.isEmpty()) {
            emptyList()
        } else {
            val remainder = favorites.size % 3
            if (remainder == 0) {
                favorites.chunked(3)
            } else {
                val topRow = favorites.take(remainder)
                val restRows = favorites.drop(remainder).chunked(3)
                listOf(topRow) + restRows
            }
        }
    }

    val maxRowsAcrossAllTabs = remember(allFavorites, tabs, favoriteRowsCount) {
        val tabGroupRows = tabs.map { tab ->
            val count = allFavorites.count { it.tabId == tab.id }
            if (count == 0) 0 else (count + 2) / 3
        }
        val maxFromTabs = tabGroupRows.maxOrNull() ?: 0
        maxOf(maxFromTabs, favoriteRowsCount)
    }

    val targetGridRowIndex = remember(maxRowsAcrossAllTabs, favoriteRowsCount) {
        (maxRowsAcrossAllTabs - favoriteRowsCount).coerceAtLeast(0)
    }

    // LazyColumn item index corresponding to target favorite row
    val initialItemIndex = remember(targetGridRowIndex) {
        1 + targetGridRowIndex
    }

    val initialItemIndexState = rememberUpdatedState(initialItemIndex)
    val listStateRef = rememberUpdatedState(listState)

    // After a call, MainActivity resumes — pull newest CallLog entries immediately; on stop reset scroll
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                viewModel.hasLoadedCallLogs.value
            ) {
                viewModel.loadCallLogs(showLoading = false)
            }
            if (event == Lifecycle.Event.ON_STOP) {
                coroutineScope.launch {
                    listStateRef.value.scrollToItem(initialItemIndexState.value, 0)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Position list once; prefetch avatars first so first frames don't decode mid-scroll
    LaunchedEffect(hasLoadedCallLogs, initialItemIndex, favoriteRows.size) {
        if (!hasLoadedCallLogs || initialScrollDone) return@LaunchedEffect

        val callLogAvatarPx = with(density) { 48.dp.roundToPx() }.coerceAtLeast(1)
        val favoriteAvatarPx = with(density) { 72.dp.roundToPx() }.coerceAtLeast(1)

        AvatarBitmapCache.prefetch(
            context = context,
            uris = favorites.map { it.photoUri },
            targetPx = favoriteAvatarPx
        )
        AvatarBitmapCache.prefetch(
            context = context,
            uris = callLogs.take(24).map { it.photoUri },
            targetPx = callLogAvatarPx
        )

        val target = if (favoriteRows.isNotEmpty()) initialItemIndex else 0
        listState.scrollToItem(target.coerceAtLeast(0), 0)
        initialScrollDone = true
        listReady = true
    }

    // Warm nearby avatars only — full journal can be huge; rest via scroll-window prefetch
    LaunchedEffect(listReady, callLogs, favorites) {
        if (!listReady) return@LaunchedEffect
        val callLogAvatarPx = with(density) { 48.dp.roundToPx() }.coerceAtLeast(1)
        val favoriteAvatarPx = with(density) { 72.dp.roundToPx() }.coerceAtLeast(1)
        AvatarBitmapCache.prefetch(
            context = context,
            uris = favorites.map { it.photoUri },
            targetPx = favoriteAvatarPx
        )
        AvatarBitmapCache.prefetch(
            context = context,
            uris = callLogs.take(48).map { it.photoUri },
            targetPx = callLogAvatarPx
        )
    }

    // Prefetch a window ahead of the visible range while scrolling
    LaunchedEffect(listReady, callLogs) {
        if (!listReady || callLogs.isEmpty()) return@LaunchedEffect
        val callLogAvatarPx = with(density) { 48.dp.roundToPx() }.coerceAtLeast(1)
        snapshotFlow {
            val info = listState.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()?.index ?: 0
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: first
            first to last
        }
            .distinctUntilChanged()
            .collect { (first, last) ->
                // Approximate call-log indices (favorites/headers sit above)
                val approxStart = (first - 30).coerceAtLeast(0)
                val approxEnd = (last + 40).coerceAtMost(callLogs.lastIndex)
                if (approxStart <= approxEnd) {
                    val slice = callLogs.subList(approxStart, approxEnd + 1).map { it.photoUri }
                    AvatarBitmapCache.prefetch(context, slice, callLogAvatarPx)
                }
            }
    }

    // Handle system Back button: return to initial startup scroll position if scrolled at least 1 row away (above or below)
    val isScrolledAway = listState.firstVisibleItemIndex != initialItemIndex

    BackHandler {
        if (isScrolledAway) {
            coroutineScope.launch {
                listState.animateScrollToItem(initialItemIndex, 0)
            }
        } else {
            coroutineScope.launch {
                listState.scrollToItem(initialItemIndex, 0)
            }
            (context as? ComponentActivity)?.finish()
        }
    }

    val groupedCallLogs = remember(callLogs) {
        callLogs.groupBy { formatDateHeader(it.timestamp) }
    }

    var lastTapTimestamp by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }
    val dialerOpenMode by viewModel.dialerOpenMode.collectAsState()

    // Search & Dialpad Screen Overlay
    var isSearchDialerOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (dialerOpenMode.allowsDoubleTap) {
                    Modifier.pointerInput(dialerOpenMode) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val down = event.changes.firstOrNull { it.changedToDown() }
                                if (down != null) {
                                    val now = System.currentTimeMillis()
                                    val pos = down.position
                                    if (now - lastTapTimestamp < 380L &&
                                        (pos - lastTapPosition).getDistance() < 120f
                                    ) {
                                        isSearchDialerOpen = true
                                        lastTapTimestamp = 0L
                                    } else {
                                        lastTapTimestamp = now
                                        lastTapPosition = pos
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (isTopBarVisible) {
                    viewModel.clearFavoriteSelection()
                }
            }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.then(
                if (isTopBarVisible) {
                    Modifier.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val down = event.changes.firstOrNull { it.changedToDown() }
                                if (down != null) {
                                    val pos = down.position
                                    val bounds = favoritesBoundsRef.value
                                    if (bounds != Rect.Zero && !bounds.contains(pos)) {
                                        viewModel.clearFavoriteSelection()
                                    }
                                }
                            }
                        }
                    }
                } else Modifier
            )
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                LazyColumn(
                    state = listState,
                    userScrollEnabled = draggingContactId == null && listReady,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (listReady) 1f else 0f },
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "favorites_title") {
                        // Favorites Section Title & Settings
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Star Icon + "Избранное"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Избранное",
                                    tint = SamsungGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Избранное",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }

                            // Right: "+ Добавить" + Settings Icon
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = SamsungGreen.copy(alpha = 0.12f),
                                    modifier = Modifier.clickable { viewModel.openAddFavoriteDialog() }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = null,
                                            tint = SamsungGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Добавить",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SamsungGreen
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.openAppSettings() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Настройки",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Favorites Grid Rows (Each row is a separate item)
                    if (favoriteRows.size < maxRowsAcrossAllTabs) {
                        val extraSpacers = maxRowsAcrossAllTabs - favoriteRows.size
                        items(extraSpacers) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(3f)
                            )
                        }
                    }

                    itemsIndexed(items = favoriteRows) { _, rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(6f)
                                .onGloballyPositioned { coordinates ->
                                    if (!isTopBarVisible) return@onGloballyPositioned
                                    val rect = coordinates.boundsInWindow()
                                    val current = favoritesBoundsRef.value
                                    favoritesBoundsRef.value = if (current == Rect.Zero) {
                                        rect
                                    } else {
                                        Rect(
                                            left = minOf(current.left, rect.left),
                                            top = minOf(current.top, rect.top),
                                            right = maxOf(current.right, rect.right),
                                            bottom = maxOf(current.bottom, rect.bottom)
                                        )
                                    }
                                },
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (i in 0 until 3) {
                                if (i < rowItems.size) {
                                    val contact = rowItems[i]
                                    val contactIndex =
                                        favorites.indexOfFirst { it.id == contact.id }
                                    val isTargetSlot =
                                        draggingContactId != null && dragToIndex == contactIndex && draggingContactId != contact.id

                                    FavoriteContactCard(
                                        contact = contact,
                                        isSelected = selectedFavorite?.id == contact.id || isTargetSlot,
                                        isDragging = draggingContactId == contact.id,
                                        dragVisualOffset = if (draggingContactId == contact.id) dragOffset else Offset.Zero,
                                        onCall = { num, sim -> onCall(num, sim) },
                                        onSms = onSms,
                                        onSelect = { viewModel.selectFavorite(it) },
                                        onContactClick = { clickedContact ->
                                            if (isTopBarVisible) {
                                                viewModel.clearFavoriteSelection()
                                            } else {
                                                viewModel.openContactDetail(clickedContact)
                                            }
                                        },
                                        onDragStart = {
                                            val idx = favorites.indexOfFirst { it.id == contact.id }
                                            if (idx != -1) {
                                                draggingContactId = contact.id
                                                dragFromIndex = idx
                                                dragToIndex = idx
                                                dragOffset = Offset.Zero
                                            }
                                        },
                                        onDrag = { delta ->
                                            dragOffset += delta
                                            if (dragFromIndex != -1 && favorites.size > 1) {
                                                val cellWidthPx = with(density) { 110.dp.toPx() }
                                                val cellHeightPx = with(density) { 110.dp.toPx() }

                                                val remainder = favorites.size % 3

                                                val startRow: Int
                                                val startCol: Int
                                                if (remainder == 0) {
                                                    startRow = dragFromIndex / 3
                                                    startCol = dragFromIndex % 3
                                                } else {
                                                    if (dragFromIndex < remainder) {
                                                        startRow = 0
                                                        startCol = dragFromIndex
                                                    } else {
                                                        startRow =
                                                            1 + (dragFromIndex - remainder) / 3
                                                        startCol = (dragFromIndex - remainder) % 3
                                                    }
                                                }

                                                val colShift =
                                                    (dragOffset.x / cellWidthPx).roundToInt()
                                                val rowShift =
                                                    (dragOffset.y / cellHeightPx).roundToInt()

                                                val totalRows = favoriteRows.size
                                                val targetRow =
                                                    (startRow + rowShift).coerceIn(0, totalRows - 1)
                                                val targetCol = (startCol + colShift).coerceIn(0, 2)
                                                val newTargetIndex: Int = if (remainder == 0) {
                                                    (targetRow * 3 + targetCol).coerceIn(
                                                        0,
                                                        favorites.size - 1
                                                    )
                                                } else {
                                                    if (targetRow == 0) {
                                                        targetCol.coerceIn(0, remainder - 1)
                                                    } else {
                                                        (remainder + (targetRow - 1) * 3 + targetCol).coerceIn(
                                                            0,
                                                            favorites.size - 1
                                                        )
                                                    }
                                                }

                                                if (newTargetIndex != dragToIndex) {
                                                    dragToIndex = newTargetIndex
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            if (dragFromIndex in favorites.indices && dragToIndex in favorites.indices && dragFromIndex != dragToIndex) {
                                                viewModel.reorderFavorites(
                                                    dragFromIndex,
                                                    dragToIndex
                                                )
                                            }
                                            draggingContactId = null
                                            dragFromIndex = -1
                                            dragToIndex = -1
                                            dragOffset = Offset.Zero
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // Favorite Tabs Selector Bar (if tabs.size > 1)
                    if (tabs.size > 1) {
                        item(key = "favorite_tabs_selector") {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 0.dp, bottom = 0.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                itemsIndexed(
                                    items = tabs,
                                    key = { _, tab -> tab.id }
                                ) { index, tab ->
                                    if (index > 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    val isSelected = tab.id == activeTabId
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) SamsungGreen else MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.clickable { viewModel.selectTab(tab.id) }
                                    ) {
                                        Text(
                                            text = tab.name,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(
                                                alpha = 0.8f
                                            ),
                                            modifier = Modifier.padding(
                                                horizontal = 10.dp,
                                                vertical = 3.dp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                if (callLogs.isEmpty()) {
                    item(key = "empty_call_logs") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Вызовы не найдены",
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    // Samsung One UI Gesture Hint Banner (над списком вызовов / "Сегодня")
                    if (showHint) {
                        item(key = "gesture_hint_banner") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = SamsungGreen.copy(alpha = 0.12f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Подсказка",
                                            tint = SamsungGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "👉 Свайп вправо — звонок | 👈 влево — SMS",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Закрыть",
                                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { viewModel.dismissSwipeHint() }
                                    )
                                }
                            }
                        }
                    }

                    groupedCallLogs.entries.forEachIndexed { dateIndex, (dateHeader, logsInDay) ->
                        if (dateHeader.isNotEmpty()) {
                            stickyHeader(key = "header_$dateHeader") { _ ->
                                FloatingStickyDateHeader(
                                    text = dateHeader,
                                    onFilterClick = if (dateIndex == 0) {
                                        { showCallFilterDialog = true }
                                    } else null,
                                    isFilterActive = isFilterActive
                                )
                            }
                        }

                        items(
                            items = logsInDay,
                            key = { it.id }
                        ) { item ->
                            SwipeableCallLogCard(
                                item = item,
                                onCall = { num -> onCall(num, null) },
                                onSms = onSms,
                                onCallWithSim = { num, slot -> onCall(num, slot) },
                                onAvatarClick = { logItem ->
                                    if (logItem.name != null) {
                                        viewModel.openContactDetailFromCallLog(logItem, initialTab = 0)
                                    } else {
                                        viewModel.openUnsavedNumberContactFlow(logItem.number)
                                    }
                                },
                                onBodyClick = {
                                    viewModel.openContactDetailFromCallLog(it, initialTab = 1)
                                },
                                onBlockNumber = { viewModel.blockCallLogNumber(it) },
                                onDeleteGroup = { viewModel.deleteCallLogGroup(it) },
                                onClearContactCalls = { viewModel.clearContactCallLogs(it) }
                            )
                        }
                    }
                }
                }

                LazyListVerticalScrollbar(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .zIndex(5f)
                        .graphicsLayer { alpha = if (listReady) 1f else 0f }
                )

                if (!listReady) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = SamsungGreen
                    )
                }
            }
        }

        // Top Floating Action Bar
        FavoritesTopBar(
            isVisible = isTopBarVisible,
            selectedContact = selectedFavorite,
            onDeleteClick = { viewModel.removeFavorite(it) },
            onAddClick = { viewModel.openAddFavoriteDialog() },
            onSettingsClick = { viewModel.openAppSettings() },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
        )

        // App Settings Bottom Sheet Dialog
        if (isAppSettingsOpen) {
            AppSettingsDialog(
                selectedRowsCount = favoriteRowsCount,
                maxPossibleRows = 8,
                tabs = tabs,
                dialerOpenMode = dialerOpenMode,
                onRowsCountSelected = { count ->
                    viewModel.setFavoriteRowsCount(count)
                },
                onDialerOpenModeSelected = { mode ->
                    viewModel.setDialerOpenMode(mode)
                },
                onAddTab = { viewModel.addTab(it) },
                onRenameTab = { id, name -> viewModel.renameTab(id, name) },
                onDeleteTab = { viewModel.deleteTab(it) },
                onDismiss = { viewModel.closeAppSettings() }
            )
        }

        // Add Favorite Contact Bottom Sheet Dialog
        if (isAddFavoriteOpen) {
            AddFavoriteDialog(
                onDismiss = { viewModel.closeAddFavoriteDialog() },
                onContactSelect = { newContact ->
                    viewModel.addFavorite(newContact)
                }
            )
        }

        // Unsaved number from call log: choose / create / add to existing
        unsavedNumberFlow?.let { flow ->
            when (val step = flow.step) {
                UnsavedNumberFlowStep.Choose -> {
                    UnsavedNumberChoiceDialog(
                        phoneNumber = flow.phoneNumber,
                        onCreateNew = { viewModel.unsavedNumberChooseCreateNew() },
                        onAddToExisting = { viewModel.unsavedNumberChoosePickExisting() },
                        onDismiss = { viewModel.closeUnsavedNumberContactFlow() }
                    )
                }

                UnsavedNumberFlowStep.CreateNew -> {
                    CallLogAddContactDialog(
                        phoneNumber = flow.phoneNumber,
                        onSave = { name, phones, emails, birthday, photo ->
                            viewModel.saveNewContactFromCallLog(
                                phoneNumber = flow.phoneNumber,
                                displayName = name,
                                phones = phones,
                                emails = emails,
                                birthdayDateString = birthday,
                                photoBitmap = photo
                            )
                        },
                        onDismiss = { viewModel.unsavedNumberBackToChoose() }
                    )
                }

                UnsavedNumberFlowStep.PickExisting -> {
                    AddFavoriteDialog(
                        title = "Поиск контакта",
                        dismissOnSelect = false,
                        onDismiss = { viewModel.unsavedNumberBackToChoose() },
                        onContactSelect = { viewModel.unsavedNumberSelectExistingContact(it) }
                    )
                }

                is UnsavedNumberFlowStep.EditExisting -> {
                    CallLogAddToExistingContactDialog(
                        contact = step.contact,
                        phoneNumberToAdd = flow.phoneNumber,
                        onSave = { original, updated, phones, emails, birthday, photo ->
                            viewModel.saveExistingContactWithNumberFromCallLog(
                                original = original,
                                updated = updated,
                                phones = phones,
                                emails = emails,
                                birthdayDateString = birthday,
                                photoBitmap = photo
                            )
                        },
                        onDismiss = { viewModel.unsavedNumberBackToPickExisting() }
                    )
                }
            }
        }

        // Contact Detail Dialog
        contactDetailToShow?.let { detailState ->
            ContactDetailDialog(
                contact = detailState.contact,
                initialTab = detailState.initialTab,
                isFavorite = detailState.isFavorite,
                tabs = tabs,
                onDismiss = { viewModel.closeContactDetail() },
                onCall = onCall,
                onSms = onSms,
                onRemoveFavorite = { viewModel.removeFavorite(it) },
                onToggleFavorite = { contact, favorite ->
                    viewModel.setContactFavorite(contact, favorite)
                },
                onUpdateContact = { viewModel.updateFavorite(it) },
                onSaveEditedContact = { original, updated, phones, emails, birthday, photo ->
                    viewModel.saveEditedContact(
                        original = original,
                        updated = updated,
                        phones = phones,
                        emails = emails,
                        birthdayDateString = birthday,
                        photoBitmap = photo
                    )
                },
                onDeleteContact = { viewModel.deleteContact(it) },
                onAddTab = { name ->
                    viewModel.addTab(name)
                    viewModel.tabs.value.lastOrNull() ?: FavoriteTab("default", name)
                }
            )
        }

        // Call Log Filter Dialog
        if (showCallFilterDialog) {
            CallFilterDialog(
                initialTypeFilter = callTypeFilter,
                initialSimFilter = simFilter,
                activeSimCount = activeSimCount,
                onApply = { type, sim ->
                    viewModel.setCallFilters(type, sim)
                },
                onDismiss = { showCallFilterDialog = false }
            )
        }

        // Floating Dialpad Button (Only show on main screen when search dialer is closed)
        if (!isSearchDialerOpen && dialerOpenMode.showsFab) {
            FloatingActionButton(
                onClick = { isSearchDialerOpen = true },
                containerColor = SamsungGreen,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 24.dp, end = 20.dp)
                    .zIndex(8f)
            ) {
                Icon(
                    imageVector = Icons.Default.Dialpad,
                    contentDescription = "Номеронабиратель",
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // Search & Dialpad Screen Overlay
        if (isSearchDialerOpen) {
            SearchDialerScreen(
                viewModel = viewModel,
                onCall = onCall,
                onSms = onSms,
                onClose = { isSearchDialerOpen = false }
            )
        }
    }
}

private val dateHeaderDayFormatter = ThreadLocal.withInitial {
    SimpleDateFormat("d MMMM", Locale.forLanguageTag("ru"))
}
private val dateHeaderWeekdayFormatter = ThreadLocal.withInitial {
    SimpleDateFormat("EEEE", Locale.forLanguageTag("ru"))
}

private fun formatDateHeader(timestamp: Long): String {
    if (timestamp == 0L) return ""
    if (DateUtils.isToday(timestamp)) return "Сегодня"
    if (DateUtils.isToday(timestamp + 24 * 3600 * 1000L)) return "Вчера"

    val ruLocale = Locale.forLanguageTag("ru")
    val dateStr = dateHeaderDayFormatter.get()!!.format(Date(timestamp))
    val dayOfWeek = dateHeaderWeekdayFormatter.get()!!.format(Date(timestamp))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(ruLocale) else it.toString() }

    return "$dateStr, $dayOfWeek"
}
