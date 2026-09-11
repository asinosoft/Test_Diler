package com.asinosoft.dialer.ui.recents.components

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.asinosoft.dialer.R
import com.asinosoft.dialer.data.model.DialerOpenMode
import com.asinosoft.dialer.data.model.FavoriteTab
import com.asinosoft.dialer.data.model.FavoritesViewMode
import com.asinosoft.dialer.data.repository.QuickRepliesManager
import com.asinosoft.dialer.ui.theme.SamsungGreen
import com.asinosoft.dialer.util.AboutSupportHelper
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class SettingsTab(val title: String) {
    PHONE("Телефон"),
    FAVORITES("Избранное"),
    ABOUT("О приложении")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsDialog(
    selectedRowsCount: Int,
    favoritesViewMode: FavoritesViewMode = FavoritesViewMode.GRID,
    maxPossibleRows: Int = 8,
    tabs: List<FavoriteTab> = emptyList(),
    dialerOpenMode: DialerOpenMode = DialerOpenMode.BUTTON_AND_DOUBLE_TAP,
    onRowsCountSelected: (Int) -> Unit,
    onFavoritesViewModeSelected: (FavoritesViewMode) -> Unit = {},
    onDialerOpenModeSelected: (DialerOpenMode) -> Unit = {},
    onAddTab: (String) -> Unit = {},
    onRenameTab: (String, String) -> Unit = { _, _ -> },
    onDeleteTab: (String) -> Unit = {},
    onReorderTabs: (List<FavoriteTab>) -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    var showAddTabDialog by remember { mutableStateOf(false) }
    var newTabNameInput by remember { mutableStateOf("") }

    var tabToRename by remember { mutableStateOf<FavoriteTab?>(null) }
    var renameTabInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Настройки",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SecondaryScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                contentColor = SamsungGreen,
                edgePadding = 0.dp,
                divider = {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                }
            ) {
                SettingsTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = tab.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTabIndex == index) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Medium
                                }
                            )
                        },
                        selectedContentColor = SamsungGreen,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 280.dp, max = 480.dp)
            ) {
                when (SettingsTab.entries[selectedTabIndex]) {
                    SettingsTab.PHONE -> PhoneSettingsTab(
                        dialerOpenMode = dialerOpenMode,
                        onDialerOpenModeSelected = onDialerOpenModeSelected
                    )

                    SettingsTab.FAVORITES -> FavoritesSettingsTab(
                        selectedRowsCount = selectedRowsCount,
                        favoritesViewMode = favoritesViewMode,
                        maxPossibleRows = maxPossibleRows,
                        tabs = tabs,
                        onRowsCountSelected = onRowsCountSelected,
                        onFavoritesViewModeSelected = onFavoritesViewModeSelected,
                        onAddTabClick = {
                            newTabNameInput = ""
                            showAddTabDialog = true
                        },
                        onRenameTabClick = { tab ->
                            tabToRename = tab
                            renameTabInput = tab.name
                        },
                        onDeleteTab = onDeleteTab,
                        onReorderTabs = onReorderTabs
                    )

                    SettingsTab.ABOUT -> AboutSettingsTab()
                }
            }
        }
    }

    if (showAddTabDialog) {
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        AlertDialog(
            onDismissRequest = { showAddTabDialog = false },
            title = {
                Text(text = "Новая вкладка", fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = newTabNameInput,
                    onValueChange = { newTabNameInput = it },
                    label = { Text("Название вкладки") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTabNameInput.isNotBlank()) {
                            onAddTab(newTabNameInput.trim())
                            showAddTabDialog = false
                        }
                    }
                ) {
                    Text("Создать", color = SamsungGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTabDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    tabToRename?.let { tab ->
        AlertDialog(
            onDismissRequest = { tabToRename = null },
            title = {
                Text(text = "Переименовать вкладку", fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = renameTabInput,
                    onValueChange = { renameTabInput = it },
                    label = { Text("Название вкладки") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameTabInput.isNotBlank()) {
                            onRenameTab(tab.id, renameTabInput.trim())
                            tabToRename = null
                        }
                    }
                ) {
                    Text("Сохранить", color = SamsungGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { tabToRename = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun PhoneSettingsTab(
    dialerOpenMode: DialerOpenMode,
    onDialerOpenModeSelected: (DialerOpenMode) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Dialpad,
                    contentDescription = null,
                    tint = SamsungGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Открывать номеронабиратель",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Как открывать набор номера с главного экрана",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            DialerOpenModeOption(
                label = "Кнопка",
                selected = dialerOpenMode == DialerOpenMode.BUTTON,
                onClick = { onDialerOpenModeSelected(DialerOpenMode.BUTTON) }
            )
            DialerOpenModeOption(
                label = "Кнопка / двойное нажатие на экране",
                selected = dialerOpenMode == DialerOpenMode.BUTTON_AND_DOUBLE_TAP,
                onClick = { onDialerOpenModeSelected(DialerOpenMode.BUTTON_AND_DOUBLE_TAP) }
            )
            DialerOpenModeOption(
                label = "Двойное нажатие на экране",
                selected = dialerOpenMode == DialerOpenMode.DOUBLE_TAP,
                onClick = { onDialerOpenModeSelected(DialerOpenMode.DOUBLE_TAP) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(14.dp))

            var showQuickRepliesEditor by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showQuickRepliesEditor = true }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Message,
                        contentDescription = null,
                        tint = SamsungGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Редактировать ответы",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Быстрые сообщения при отклонении вызова",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }

            if (showQuickRepliesEditor) {
                QuickRepliesEditorDialog(
                    onDismiss = { showQuickRepliesEditor = false }
                )
            }
        }
    }
}

@Composable
private fun DialerOpenModeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) SamsungGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
internal fun FavoritesSettingsTab(
    selectedRowsCount: Int,
    favoritesViewMode: FavoritesViewMode,
    maxPossibleRows: Int,
    tabs: List<FavoriteTab>,
    onRowsCountSelected: (Int) -> Unit,
    onFavoritesViewModeSelected: (FavoritesViewMode) -> Unit,
    onAddTabClick: () -> Unit,
    onRenameTabClick: (FavoriteTab) -> Unit,
    onDeleteTab: (String) -> Unit,
    onReorderTabs: (List<FavoriteTab>) -> Unit = {}
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    var editableTabs by remember(tabs) { mutableStateOf(tabs) }
    var draggingTabIndex by remember { mutableStateOf<Int?>(null) }
    var tabDragOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(tabs) {
        editableTabs = tabs
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // View Mode Selector (Grid vs List)
            Text(
                text = "Вид отображения",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Формат карточек контактов на главном экране",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Option 1: Grid (квадратики 3x3)
                val isGrid = favoritesViewMode == FavoritesViewMode.GRID
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onFavoritesViewModeSelected(FavoritesViewMode.GRID) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isGrid) SamsungGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(
                        width = if (isGrid) 2.dp else 1.dp,
                        color = if (isGrid) SamsungGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            repeat(3) {
                                Surface(
                                    modifier = Modifier.size(14.dp),
                                    shape = RoundedCornerShape(3.dp),
                                    color = if (isGrid) SamsungGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                ) {}
                            }
                        }
                        Text(
                            text = "Сетка",
                            fontSize = 14.sp,
                            fontWeight = if (isGrid) FontWeight.Bold else FontWeight.Medium,
                            color = if (isGrid) SamsungGreen else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "3 столбца",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                // Option 2: List (строки как в журнале со свайпами)
                val isList = favoritesViewMode == FavoritesViewMode.LIST
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onFavoritesViewModeSelected(FavoritesViewMode.LIST) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isList) SamsungGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(
                        width = if (isList) 2.dp else 1.dp,
                        color = if (isList) SamsungGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            repeat(2) {
                                Surface(
                                    modifier = Modifier.size(width = 46.dp, height = 6.dp),
                                    shape = RoundedCornerShape(2.dp),
                                    color = if (isList) SamsungGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                ) {}
                            }
                        }
                        Text(
                            text = "Список",
                            fontSize = 14.sp,
                            fontWeight = if (isList) FontWeight.Bold else FontWeight.Medium,
                            color = if (isList) SamsungGreen else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Со свайпами",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            val rowsLabel = if (favoritesViewMode == FavoritesViewMode.LIST) {
                "Строк избранного списка при старте ($selectedRowsCount)"
            } else {
                "Строк избранной сетки при старте ($selectedRowsCount)"
            }

            Text(
                text = rowsLabel,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val maxRows = maxPossibleRows.coerceAtLeast(1).coerceAtMost(8)
                for (count in 1..maxRows) {
                    val isSelected = count == selectedRowsCount
                    Surface(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onRowsCountSelected(count) },
                        shape = CircleShape,
                        color = if (isSelected) SamsungGreen else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$count",
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Вкладки (${editableTabs.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                TextButton(onClick = onAddTabClick) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Добавить",
                        modifier = Modifier.size(18.dp),
                        tint = SamsungGreen
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Добавить",
                        color = SamsungGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            editableTabs.forEachIndexed { index, tab ->
                val isDragging = draggingTabIndex == index

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .graphicsLayer {
                            if (isDragging) {
                                translationY = tabDragOffsetY
                                shadowElevation = 10f
                                scaleX = 1.02f
                                scaleY = 1.02f
                            }
                        }
                        .zIndex(if (isDragging) 10f else 1f)
                        .pointerInput(index, editableTabs.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    draggingTabIndex = index
                                    tabDragOffsetY = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    tabDragOffsetY += dragAmount.y
                                    val currentList = editableTabs.toMutableList()
                                    val currentIndex = draggingTabIndex ?: index
                                    val rowHeightPx = with(density) { 52.dp.toPx() }
                                    val shift = (tabDragOffsetY / rowHeightPx).roundToInt()
                                    val targetIndex = (currentIndex + shift).coerceIn(0, currentList.size - 1)

                                    if (targetIndex != currentIndex) {
                                        val item = currentList.removeAt(currentIndex)
                                        currentList.add(targetIndex, item)
                                        editableTabs = currentList
                                        onReorderTabs(currentList)
                                        tabDragOffsetY -= (targetIndex - currentIndex) * rowHeightPx
                                        draggingTabIndex = targetIndex
                                    }
                                },
                                onDragEnd = {
                                    draggingTabIndex = null
                                    tabDragOffsetY = 0f
                                },
                                onDragCancel = {
                                    draggingTabIndex = null
                                    tabDragOffsetY = 0f
                                }
                            )
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    tonalElevation = if (isDragging) 4.dp else 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = "Перетащить",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = tab.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onRenameTabClick(tab) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Переименовать",
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            if (editableTabs.size > 1) {
                                IconButton(
                                    onClick = { onDeleteTab(tab.id) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Удалить",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSettingsTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showLicenses by remember { mutableStateOf(false) }
    var isPreparingSupport by remember { mutableStateOf(false) }

    val versionName = remember {
        try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }
    val appName = stringResource(R.string.app_name)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Версия $versionName",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                AboutActionRow(
                    icon = Icons.Default.ThumbUp,
                    title = "Сказать спасибо",
                    subtitle = "Понравилось приложение, оставьте отзыв в Google Play!",
                    onClick = { AboutSupportHelper.openPlayStoreListing(context) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                AboutActionRow(
                    icon = Icons.Default.Share,
                    title = "Посоветовать друзьям",
                    subtitle = "Посоветуйте приложение своим друзьям",
                    onClick = { AboutSupportHelper.shareApp(context, appName) }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                AboutActionRow(
                    icon = Icons.Default.Email,
                    title = "Написать в поддержку",
                    subtitle = if (isPreparingSupport) {
                        "Подготовка отчёта…"
                    } else {
                        "Открыть письмо на cdm.asinosoft@gmail.com"
                    },
                    enabled = !isPreparingSupport,
                    onClick = {
                        scope.launch {
                            isPreparingSupport = true
                            try {
                                AboutSupportHelper.openSupportEmail(context, appName)
                            } finally {
                                isPreparingSupport = false
                            }
                        }
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                AboutActionRow(
                    icon = Icons.Default.Description,
                    title = "Лицензии третьих сторон",
                    subtitle = "Открытые компоненты, используемые в приложении",
                    onClick = { showLicenses = true }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                AboutActionRow(
                    icon = Icons.Default.Policy,
                    title = "Политика конфиденциальности",
                    subtitle = "asinosoft.ru",
                    onClick = { AboutSupportHelper.openPrivacyPolicy(context) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showLicenses) {
        ThirdPartyLicensesDialog(onDismiss = { showLicenses = false })
    }
}

@Composable
private fun AboutActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) SamsungGreen else SamsungGreen.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.45f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ThirdPartyLicensesDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 16.dp, top = 36.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Text(
                        text = "Лицензии третьих сторон",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Это приложение использует сторонние библиотеки с открытым исходным кодом. " +
                            "Ниже приведены сведения о них и тексты соответствующих лицензий.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    LicenseSection(
                        title = "AndroidX / Jetpack",
                        components = "Core KTX, Activity, Lifecycle, AppCompat components, " +
                            "Jetpack Compose UI, Compose Foundation, Compose Material3, " +
                            "Compose Material Icons",
                        licenseName = "Apache License 2.0"
                    )
                    LicenseSection(
                        title = "CameraX",
                        components = "camera-camera2, camera-lifecycle, camera-view",
                        licenseName = "Apache License 2.0"
                    )
                    LicenseSection(
                        title = "Google ML Kit",
                        components = "Barcode Scanning",
                        licenseName = "Apache License 2.0 / условия Google ML Kit"
                    )
                    LicenseSection(
                        title = "libphonenumber",
                        components = "Google libphonenumber — форматирование и разбор номеров телефонов",
                        licenseName = "Apache License 2.0"
                    )
                    LicenseSection(
                        title = "Kotlin",
                        components = "Kotlin Standard Library, Kotlin Coroutines",
                        licenseName = "Apache License 2.0"
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Apache License 2.0",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = APACHE_LICENSE_2_NOTICE,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun LicenseSection(
    title: String,
    components: String,
    licenseName: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = components,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = licenseName,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = SamsungGreen
            )
        }
    }
}

private val APACHE_LICENSE_2_NOTICE = """
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Полный текст лицензии: https://www.apache.org/licenses/LICENSE-2.0
""".trimIndent()

/**
 * Диалог редактирования быстрых текстовых ответов при отклонении вызова
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickRepliesEditorDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    var replies by remember {
        mutableStateOf(QuickRepliesManager.getQuickReplies(context))
    }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    var editingReplyIndex by remember { mutableStateOf<Int?>(null) }
    var editingText by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var newReplyText by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top One UI Bar: Arrow back + Title + Add Button
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, end = 16.dp, top = 42.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Назад",
                                    tint = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Быстрые ответы",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        IconButton(
                            onClick = {
                                newReplyText = ""
                                showAddDialog = true
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SamsungGreen.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Добавить ответ",
                                tint = SamsungGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // List of Replies
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    replies.forEachIndexed { index, reply ->
                        val isDragging = draggingIndex == index

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    if (isDragging) {
                                        translationY = dragOffsetY
                                        shadowElevation = 12f
                                        scaleX = 1.02f
                                        scaleY = 1.02f
                                    }
                                }
                                .zIndex(if (isDragging) 10f else 1f)
                                .pointerInput(index, replies.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            draggingIndex = index
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            val currentList = replies.toMutableList()
                                            val currentIndex = draggingIndex ?: index
                                            val rowHeightPx = with(density) { 60.dp.toPx() }
                                            val shift = (dragOffsetY / rowHeightPx).roundToInt()
                                            val targetIndex =
                                                (currentIndex + shift).coerceIn(0, currentList.size - 1)

                                            if (targetIndex != currentIndex) {
                                                val item = currentList.removeAt(currentIndex)
                                                currentList.add(targetIndex, item)
                                                replies = currentList
                                                QuickRepliesManager.saveQuickReplies(context, currentList)
                                                dragOffsetY -= (targetIndex - currentIndex) * rowHeightPx
                                                draggingIndex = targetIndex
                                            }
                                        },
                                        onDragEnd = {
                                            draggingIndex = null
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggingIndex = null
                                            dragOffsetY = 0f
                                        }
                                    )
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                            tonalElevation = if (isDragging) 4.dp else 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Перетащить",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = reply,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            editingReplyIndex = index
                                            editingText = reply
                                            showEditDialog = true
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Редактировать",
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    if (replies.size > 1) {
                                        IconButton(
                                            onClick = {
                                                val updated = replies.toMutableList().apply { removeAt(index) }
                                                replies = updated
                                                QuickRepliesManager.saveQuickReplies(context, updated)
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Удалить",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Edit Dialog
    if (showEditDialog && editingReplyIndex != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Изменить ответ", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                OutlinedTextField(
                    value = editingText,
                    onValueChange = { editingText = it },
                    label = { Text("Текст ответа") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val text = editingText.trim()
                        if (text.isNotBlank()) {
                            val idx = editingReplyIndex ?: return@TextButton
                            val updated = replies.toMutableList().apply { set(idx, text) }
                            replies = updated
                            QuickRepliesManager.saveQuickReplies(context, updated)
                        }
                        showEditDialog = false
                    }
                ) {
                    Text("Сохранить", color = SamsungGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Add Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Новый ответ", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                OutlinedTextField(
                    value = newReplyText,
                    onValueChange = { newReplyText = it },
                    label = { Text("Текст ответа") },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val text = newReplyText.trim()
                        if (text.isNotBlank()) {
                            val updated = replies + text
                            replies = updated
                            QuickRepliesManager.saveQuickReplies(context, updated)
                        }
                        showAddDialog = false
                    }
                ) {
                    Text("Добавить", color = SamsungGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}
