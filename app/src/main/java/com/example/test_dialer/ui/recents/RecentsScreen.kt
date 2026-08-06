package com.example.test_dialer.ui.recents

import android.text.format.DateUtils
import android.widget.Toast
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_dialer.ui.recents.components.AddFavoriteDialog
import com.example.test_dialer.ui.recents.components.FavoritesSection
import com.example.test_dialer.ui.recents.components.FavoritesTopBar
import com.example.test_dialer.ui.recents.components.SwipeableCallLogCard
import com.example.test_dialer.ui.theme.SamsungGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    viewModel: RecentsViewModel,
    onCall: (String) -> Unit,
    onSms: (String) -> Unit
) {
    val context = LocalContext.current
    val callLogs by viewModel.filteredCallLogs.collectAsState(initial = emptyList())
    val favorites by viewModel.favorites.collectAsState()
    val selectedFavorite by viewModel.selectedFavorite.collectAsState()
    val isTopBarVisible by viewModel.isTopBarVisible.collectAsState()
    val isAddFavoriteOpen by viewModel.isAddFavoriteOpen.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val showOnlyMissed by viewModel.showOnlyMissed.collectAsState()
    var showHint by remember { mutableStateOf(true) }

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
                // 5.3: Tap outside floating top bar dismisses selection
                if (isTopBarVisible) {
                    viewModel.clearFavoriteSelection()
                }
            }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
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

                    // 1: Favorites Section
                    FavoritesSection(
                        favorites = favorites,
                        selectedContact = selectedFavorite,
                        onCall = onCall,
                        onSms = onSms,
                        onSelectContact = { viewModel.selectFavorite(it) },
                        onAddFavoriteClick = { viewModel.openAddFavoriteDialog() }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }

                if (callLogs.isEmpty()) {
                    item {
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
                                onCall = onCall,
                                onSms = onSms
                            )
                        }
                    }
                }
            }
        }

        // 5.2: Top Floating Action Bar
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

        // 5.2.2: Add Favorite Contact Bottom Sheet Dialog
        if (isAddFavoriteOpen) {
            AddFavoriteDialog(
                onDismiss = { viewModel.closeAddFavoriteDialog() },
                onContactSelect = { newContact ->
                    viewModel.addFavorite(newContact)
                }
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
