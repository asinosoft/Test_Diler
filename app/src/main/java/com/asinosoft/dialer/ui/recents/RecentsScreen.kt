package com.asinosoft.dialer.ui.recents

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.asinosoft.dialer.data.model.FavoriteTab
import com.asinosoft.dialer.ui.dialer.SearchDialerScreen
import com.asinosoft.dialer.ui.recents.components.AddFavoriteDialog
import com.asinosoft.dialer.ui.recents.components.AppSettingsDialog
import com.asinosoft.dialer.ui.recents.components.ContactDetailDialog
import com.asinosoft.dialer.ui.recents.components.FavoriteContactCard
import com.asinosoft.dialer.ui.recents.components.FavoritesTopBar
import com.asinosoft.dialer.ui.recents.components.SwipeableCallLogCard
import com.asinosoft.dialer.ui.theme.SamsungGreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    viewModel: RecentsViewModel,
    onCall: (String, Int?) -> Unit,
    onSms: (String) -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val callLogs by viewModel.filteredCallLogs.collectAsState(initial = emptyList())
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

    val searchQuery by viewModel.searchQuery.collectAsState()
    val showOnlyMissed by viewModel.showOnlyMissed.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showHint by remember { mutableStateOf(true) }
    var initialScrollDone by remember { mutableStateOf(false) }

    var draggingContactId by remember { mutableStateOf<String?>(null) }
    var dragFromIndex by remember { mutableIntStateOf(-1) }
    var dragToIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    var favoritesBounds by remember { mutableStateOf(Rect.Zero) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadCallLogs()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val listState = rememberLazyListState()

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

    // Scroll to initial position AFTER call logs and favorites finish loading
    LaunchedEffect(isLoading, favorites, callLogs) {
        if (!isLoading && !initialScrollDone && favoriteRows.isNotEmpty()) {
            listState.scrollToItem(initialItemIndex, 0)
            initialScrollDone = true
        }
    }

    // Check if scrolled away by more than 33% of viewport / threshold
    val isScrolledFar by remember(initialItemIndex) {
        derivedStateOf {
            val currentIndex = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            val indexDiff = abs(currentIndex - initialItemIndex)
            indexDiff >= 2 || (indexDiff == 1 && offset > 200)
        }
    }

    // Handle system Back button: return to initial startup scroll position if scrolled far
    BackHandler(enabled = isScrolledFar) {
        coroutineScope.launch {
            listState.animateScrollToItem(initialItemIndex, 0)
        }
    }

    val groupedCallLogs = remember(callLogs) {
        callLogs.groupBy { formatDateHeader(it.timestamp) }
    }

    var lastTapTimestamp by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.firstOrNull { it.changedToDown() }
                        if (down != null) {
                            val now = System.currentTimeMillis()
                            val pos = down.position
                            if (now - lastTapTimestamp < 380L && (pos - lastTapPosition).getDistance() < 120f) {
                                viewModel.openSearchDialer()
                                lastTapTimestamp = 0L
                            } else {
                                lastTapTimestamp = now
                                lastTapPosition = pos
                            }
                        }
                    }
                }
            }
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
                    Modifier.pointerInput(favoritesBounds) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val down = event.changes.firstOrNull { it.changedToDown() }
                                if (down != null) {
                                    val pos = down.position
                                    if (favoritesBounds != Rect.Zero && !favoritesBounds.contains(
                                            pos
                                        )
                                    ) {
                                        viewModel.clearFavoriteSelection()
                                    }
                                }
                            }
                        }
                    }
                } else Modifier
            )
        ) { innerPadding ->
            LazyColumn(
                state = listState,
                userScrollEnabled = draggingContactId == null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "top_header") {
                    // Samsung One UI Top Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Последние",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Вызовы и сообщения",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.openAppSettings() },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Настройки",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // One UI Search Bar
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openSearchDialer(searchQuery) },
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = {
                                viewModel.onSearchQueryChange(it)
                                viewModel.openSearchDialer(it)
                            },
                            placeholder = {
                                Text(
                                    text = "Поиск по имени или номеру",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Поиск",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Очистить"
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Filter Chips (Все / Пропущенные)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = !showOnlyMissed,
                            onClick = { viewModel.setShowOnlyMissed(false) },
                            label = { Text("Все") },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SamsungGreen,
                                selectedLabelColor = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        FilterChip(
                            selected = showOnlyMissed,
                            onClick = { viewModel.setShowOnlyMissed(true) },
                            label = { Text("Пропущенные") },
                            shape = RoundedCornerShape(16.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SamsungGreen,
                                selectedLabelColor = Color.White
                            )
                        )
                    }

                    // Samsung One UI Gesture Hint Banner
                    if (showHint) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SamsungGreen.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
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
                                        .clickable { showHint = false }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Favorites Section Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Избранные контакты",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Text(
                            text = "Добавить",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SamsungGreen,
                            modifier = Modifier
                                .clickable { viewModel.openAddFavoriteDialog() }
                                .padding(vertical = 4.dp, horizontal = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
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
                                val rect = coordinates.boundsInWindow()
                                favoritesBounds = if (favoritesBounds == Rect.Zero) {
                                    rect
                                } else {
                                    Rect(
                                        left = minOf(favoritesBounds.left, rect.left),
                                        top = minOf(favoritesBounds.top, rect.top),
                                        right = maxOf(favoritesBounds.right, rect.right),
                                        bottom = maxOf(favoritesBounds.bottom, rect.bottom)
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
                    groupedCallLogs.forEach { (dateHeader, logsInDay) ->
                        if (dateHeader.isNotEmpty()) {
                            item(key = "header_$dateHeader") {
                                Text(
                                    text = dateHeader,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(
                                        start = 4.dp,
                                        top = 0.dp,
                                        bottom = 2.dp
                                    )
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
                                onItemClick = { viewModel.openContactDetailFromCallLog(it) }
                            )
                        }
                    }
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
                onRowsCountSelected = { count ->
                    viewModel.setFavoriteRowsCount(count)
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

        // Search & Dialpad Screen Overlay
        val isSearchDialerOpen by viewModel.isSearchDialerOpen.collectAsState()

        // Contact Detail Bottom Sheet Dialog (only render if search dialer is closed)
        if (!isSearchDialerOpen) {
            contactDetailToShow?.let { detailState ->
                ContactDetailDialog(
                    contact = detailState.contact,
                    initialTab = detailState.initialTab,
                    tabs = tabs,
                    onDismiss = { viewModel.closeContactDetail() },
                    onCall = onCall,
                    onSms = onSms,
                    onRemoveFavorite = { viewModel.removeFavorite(it) },
                    onUpdateContact = { viewModel.updateFavorite(it) },
                    onAddTab = { name ->
                        viewModel.addTab(name)
                        viewModel.tabs.value.lastOrNull() ?: FavoriteTab("default", name)
                    }
                )
            }
        }

        // Floating Dialpad Button (Only show on main screen when search dialer is closed)
        if (!isSearchDialerOpen) {
            FloatingActionButton(
                onClick = { viewModel.openSearchDialer() },
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
                onClose = { viewModel.closeSearchDialer() }
            )
        }
    }
}

private fun formatDateHeader(timestamp: Long): String {
    if (timestamp == 0L) return ""
    if (DateUtils.isToday(timestamp)) return "Сегодня"
    if (DateUtils.isToday(timestamp + 24 * 3600 * 1000L)) return "Вчера"

    val ruLocale = Locale.forLanguageTag("ru")
    val dateStr = SimpleDateFormat("d MMMM", ruLocale).format(Date(timestamp))
    val dayOfWeek = SimpleDateFormat("EEEE", ruLocale).format(Date(timestamp))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(ruLocale) else it.toString() }

    return "$dateStr, $dayOfWeek"
}
