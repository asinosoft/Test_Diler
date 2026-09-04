package com.asinosoft.dialer.ui.recents.components

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.zIndex
import com.asinosoft.dialer.R
import com.asinosoft.dialer.data.model.DialerOpenMode
import com.asinosoft.dialer.data.model.FavoriteTab
import com.asinosoft.dialer.ui.theme.SamsungGreen
import kotlin.math.roundToInt

private enum class SettingsTab(val title: String) {
    MAIN("Основное"),
    PHONE("Телефон"),
    FAVORITES("Избранное"),
    ABOUT("О приложении")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsDialog(
    selectedRowsCount: Int,
    maxPossibleRows: Int = 8,
    tabs: List<FavoriteTab> = emptyList(),
    dialerOpenMode: DialerOpenMode = DialerOpenMode.BUTTON_AND_DOUBLE_TAP,
    onRowsCountSelected: (Int) -> Unit,
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
                    SettingsTab.MAIN -> MainSettingsTab()

                    SettingsTab.PHONE -> PhoneSettingsTab(
                        dialerOpenMode = dialerOpenMode,
                        onDialerOpenModeSelected = onDialerOpenModeSelected
                    )

                    SettingsTab.FAVORITES -> FavoritesSettingsTab(
                        selectedRowsCount = selectedRowsCount,
                        maxPossibleRows = maxPossibleRows,
                        tabs = tabs,
                        onRowsCountSelected = onRowsCountSelected,
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
                    modifier = Modifier.fillMaxWidth()
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
private fun MainSettingsTab() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Пока нет настроек",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }
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
                    imageVector = Icons.Default.Phone,
                    contentDescription = null,
                    tint = SamsungGreen,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Телефон",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            Spacer(modifier = Modifier.height(14.dp))

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
private fun FavoritesSettingsTab(
    selectedRowsCount: Int,
    maxPossibleRows: Int,
    tabs: List<FavoriteTab>,
    onRowsCountSelected: (Int) -> Unit,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Избранное",
                    tint = SamsungGreen,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Избранное",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Строк избранного при старте ($selectedRowsCount)",
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
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = SamsungGreen,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = appName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Телефонный номеронабиратель",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Версия $versionName",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
        )
    }
}
