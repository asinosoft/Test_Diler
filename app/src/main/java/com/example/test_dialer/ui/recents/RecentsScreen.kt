package com.example.test_dialer.ui.recents

import android.text.format.DateUtils
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_dialer.ui.recents.components.AddFavoriteDialog
import com.example.test_dialer.ui.recents.components.ContactDetailDialog
import com.example.test_dialer.ui.recents.components.FavoriteContactCard
import com.example.test_dialer.ui.recents.components.FavoritesTopBar
import com.example.test_dialer.ui.recents.components.SwipeableCallLogCard
import com.example.test_dialer.ui.theme.SamsungGreen
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
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val callLogs by viewModel.filteredCallLogs.collectAsState(initial = emptyList())
    val favorites by viewModel.favorites.collectAsState()
    val selectedFavorite by viewModel.selectedFavorite.collectAsState()
    val isTopBarVisible by viewModel.isTopBarVisible.collectAsState()
    val isAddFavoriteOpen by viewModel.isAddFavoriteOpen.collectAsState()
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

    // Target row index for startup: 3rd row from the bottom of favorites
    val targetFavoriteRowIndex = remember(favoriteRows) {
        if (favoriteRows.isEmpty()) 0 else (favoriteRows.size - 3).coerceAtLeast(0)
    }

    // LazyColumn item index corresponding to target favorite row
    val initialItemIndex = remember(targetFavoriteRowIndex) {
        1 + targetFavoriteRowIndex
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tap outside floating top bar dismisses selection
                if (isTopBarVisible) {
                    viewModel.clearFavoriteSelection()
                }
            }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
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
                            onClick = { viewModel.loadCallLogs() },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Обновить",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // One UI Search Bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChange(it) },
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
                if (favoriteRows.isEmpty()) {
                    item(key = "empty_favorites") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openAddFavoriteDialog() }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp)
                                ) {
                                Text(
                                    text = "+ Добавить избранные контакты",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = SamsungGreen
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        items = favoriteRows,
                        key = { index, _ -> "fav_row_$index" }
                    ) { _, rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            for (i in 0 until 3) {
                                if (i < rowItems.size) {
                                    val contact = rowItems[i]
                                    val contactIndex = favorites.indexOfFirst { it.id == contact.id }
                                    val isTargetSlot = draggingContactId != null && dragToIndex == contactIndex && draggingContactId != contact.id

                                    FavoriteContactCard(
                                        contact = contact,
                                        isSelected = selectedFavorite?.id == contact.id || isTargetSlot,
                                        isDragging = draggingContactId == contact.id,
                                        dragVisualOffset = if (draggingContactId == contact.id) dragOffset else Offset.Zero,
                                        onCall = { num -> onCall(num, null) },
                                        onSms = onSms,
                                        onSelect = { viewModel.selectFavorite(it) },
                                        onContactClick = { viewModel.openContactDetail(it) },
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
                                                        startRow = 1 + (dragFromIndex - remainder) / 3
                                                        startCol = (dragFromIndex - remainder) % 3
                                                    }
                                                }

                                                val colShift = (dragOffset.x / cellWidthPx).roundToInt()
                                                val rowShift = (dragOffset.y / cellHeightPx).roundToInt()

                                                val totalRows = favoriteRows.size
                                                val targetRow = (startRow + rowShift).coerceIn(0, totalRows - 1)
                                                val targetCol = (startCol + colShift).coerceIn(0, 2)

                                                val newTargetIndex: Int
                                                if (remainder == 0) {
                                                    newTargetIndex = (targetRow * 3 + targetCol).coerceIn(0, favorites.size - 1)
                                                } else {
                                                    if (targetRow == 0) {
                                                        newTargetIndex = targetCol.coerceIn(0, remainder - 1)
                                                    } else {
                                                        newTargetIndex = (remainder + (targetRow - 1) * 3 + targetCol).coerceIn(0, favorites.size - 1)
                                                    }
                                                }

                                                if (newTargetIndex != dragToIndex) {
                                                    dragToIndex = newTargetIndex
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            if (dragFromIndex in favorites.indices && dragToIndex in favorites.indices && dragFromIndex != dragToIndex) {
                                                viewModel.reorderFavorites(dragFromIndex, dragToIndex)
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
                                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp)
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
            onSettingsClick = {
                Toast.makeText(context, "Настройки избранного", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Add Favorite Contact Bottom Sheet Dialog
        if (isAddFavoriteOpen) {
            AddFavoriteDialog(
                onDismiss = { viewModel.closeAddFavoriteDialog() },
                onContactSelect = { newContact ->
                    viewModel.addFavorite(newContact)
                }
            )
        }

        // Contact Detail Bottom Sheet Dialog
        contactDetailToShow?.let { detailState ->
            ContactDetailDialog(
                contact = detailState.contact,
                initialTab = detailState.initialTab,
                onDismiss = { viewModel.closeContactDetail() },
                onCall = onCall,
                onSms = onSms,
                onRemoveFavorite = { viewModel.removeFavorite(it) }
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
