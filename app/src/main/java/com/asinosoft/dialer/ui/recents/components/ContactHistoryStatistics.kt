package com.asinosoft.dialer.ui.recents.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asinosoft.dialer.data.model.CallLogItem
import com.asinosoft.dialer.data.model.CallType
import com.asinosoft.dialer.ui.components.OneUiPopupMenu
import com.asinosoft.dialer.ui.components.OneUiPopupMenuItem
import com.asinosoft.dialer.ui.theme.IncomingGreen
import com.asinosoft.dialer.ui.theme.OutgoingBlue
import com.asinosoft.dialer.ui.theme.SamsungGreen
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class StatisticsPeriod(val title: String) {
    ALL_TIME("Все время"),
    THIS_MONTH("Этот месяц"),
    LAST_MONTH("Прошлый месяц"),
    CUSTOM_RANGE("Выбрать период")
}

enum class DiagramMetric(val title: String) {
    TIME("Время"),
    COUNT("Количество")
}

data class BarChartGroup(
    val label: String,
    val fullLabel: String,
    val incomingValue: Float,
    val outgoingValue: Float,
    val incomingDisplay: String,
    val outgoingDisplay: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactHistoryStatistics(
    historyLogs: List<CallLogItem>,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var selectedPeriod by remember { mutableStateOf(StatisticsPeriod.ALL_TIME) }
    var showPeriodMenu by remember { mutableStateOf(false) }

    // Constrain dates according to the oldest record in call history
    val oldestCallTimestamp = remember(historyLogs) {
        historyLogs.minOfOrNull { it.timestamp } ?: System.currentTimeMillis()
    }

    val oldestStartOfDayMillis = remember(oldestCallTimestamp) {
        Calendar.getInstance().apply {
            timeInMillis = oldestCallTimestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val currentEndOfDayMillis = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    var customStartDateMillis by remember(oldestStartOfDayMillis) {
        mutableStateOf<Long?>(null)
    }
    var customEndDateMillis by remember(currentEndOfDayMillis) {
        mutableStateOf<Long?>(null)
    }
    var showDateRangePickerDialog by remember { mutableStateOf(false) }

    var selectedMetric by remember { mutableStateOf(DiagramMetric.TIME) }
    var selectedGroupIndex by remember { mutableStateOf<Int?>(null) }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "chevronRotation"
    )

    // Filter call logs according to the selected period
    val filteredLogs = remember(historyLogs, selectedPeriod, customStartDateMillis, customEndDateMillis) {
        when (selectedPeriod) {
            StatisticsPeriod.ALL_TIME -> historyLogs
            StatisticsPeriod.THIS_MONTH -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val start = cal.timeInMillis
                historyLogs.filter { it.timestamp >= start }
            }
            StatisticsPeriod.LAST_MONTH -> {
                val calStart = Calendar.getInstance().apply {
                    add(Calendar.MONTH, -1)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val calEnd = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.MILLISECOND, -1)
                }
                val start = calStart.timeInMillis
                val end = calEnd.timeInMillis
                historyLogs.filter { it.timestamp in start..end }
            }
            StatisticsPeriod.CUSTOM_RANGE -> {
                val start = customStartDateMillis ?: oldestStartOfDayMillis
                val end = customEndDateMillis?.let {
                    Calendar.getInstance().apply {
                        timeInMillis = it
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                        set(Calendar.MILLISECOND, 999)
                    }.timeInMillis
                } ?: currentEndOfDayMillis
                historyLogs.filter { it.timestamp in start..end }
            }
        }
    }

    val incomingCalls = remember(filteredLogs) { filteredLogs.filter { it.type == CallType.INCOMING } }
    val outgoingCalls = remember(filteredLogs) { filteredLogs.filter { it.type == CallType.OUTGOING } }

    val incomingDurationSec = remember(incomingCalls) { incomingCalls.sumOf { it.duration } }
    val outgoingDurationSec = remember(outgoingCalls) { outgoingCalls.sumOf { it.duration } }
    val totalDurationSec = incomingDurationSec + outgoingDurationSec

    val incomingCount = remember(incomingCalls) { incomingCalls.sumOf { it.count } }
    val outgoingCount = remember(outgoingCalls) { outgoingCalls.sumOf { it.count } }
    val totalCount = incomingCount + outgoingCount

    val periodDisplayTitle = remember(selectedPeriod, customStartDateMillis, customEndDateMillis) {
        if (selectedPeriod == StatisticsPeriod.CUSTOM_RANGE && customStartDateMillis != null && customEndDateMillis != null) {
            val fmt = SimpleDateFormat("dd.MM", Locale.getDefault())
            "${fmt.format(Date(customStartDateMillis!!))} - ${fmt.format(Date(customEndDateMillis!!))}"
        } else {
            selectedPeriod.title
        }
    }

    // Build grouped bar chart intervals (by days or by months)
    val chartGroups = remember(filteredLogs, selectedPeriod, customStartDateMillis, customEndDateMillis, selectedMetric, oldestStartOfDayMillis) {
        buildChartGroups(
            logs = filteredLogs,
            period = selectedPeriod,
            metric = selectedMetric,
            customStartMillis = customStartDateMillis ?: oldestStartOfDayMillis,
            customEndMillis = customEndDateMillis ?: currentEndOfDayMillis,
            oldestStartMillis = oldestStartOfDayMillis
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (!isExpanded) {
                    Modifier.clickable { isExpanded = true }
                } else Modifier
            ),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header Row: «Статистика» + Chevron + Period Selector dropdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Статистика",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(chevronRotation)
                    )
                }

                if (isExpanded) {
                    Box {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showPeriodMenu = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = periodDisplayTitle,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = SamsungGreen,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Выбор периода",
                                    tint = SamsungGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        OneUiPopupMenu(
                            expanded = showPeriodMenu,
                            onDismissRequest = { showPeriodMenu = false },
                            alignEnd = true
                        ) {
                            StatisticsPeriod.entries.forEach { period ->
                                val isCurrent = selectedPeriod == period
                                OneUiPopupMenuItem(
                                    icon = Icons.Default.Check,
                                    label = period.title,
                                    labelColor = if (isCurrent) SamsungGreen else MaterialTheme.colorScheme.onSurface,
                                    iconTint = if (isCurrent) SamsungGreen else Color.Transparent,
                                    iconBackground = if (isCurrent) SamsungGreen.copy(alpha = 0.12f) else Color.Transparent,
                                    onClick = {
                                        showPeriodMenu = false
                                        if (period == StatisticsPeriod.CUSTOM_RANGE) {
                                            showDateRangePickerDialog = true
                                        } else {
                                            selectedPeriod = period
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Expandable Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(200)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(150))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                ) {
                    // Statistics Table (3 equal columns: Входящие | Исходящие | Всего)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            // Column Headers Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1.1 Входящие
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.CallReceived,
                                        contentDescription = "Входящие",
                                        tint = IncomingGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 1.2 Исходящие
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.CallMade,
                                        contentDescription = "Исходящие",
                                        tint = OutgoingBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 1.3 Всего
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Всего",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                                thickness = 1.dp
                            )

                            // 2.1 Строка: Время
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatExactDuration(incomingDurationSec),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatExactDuration(outgoingDurationSec),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = formatExactDuration(totalDurationSec),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f),
                                thickness = 1.dp
                            )

                            // 2.2 Строка: Количество
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = incomingCount.toString(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = outgoingCount.toString(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = totalCount.toString(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Toggle (Время / Количество)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Segmented Control
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.6f)
                        ) {
                            Row(
                                modifier = Modifier.padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                DiagramMetric.entries.forEach { metric ->
                                    val isSelected = selectedMetric == metric
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) SamsungGreen else Color.Transparent,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                selectedMetric = metric
                                                selectedGroupIndex = null
                                            }
                                    ) {
                                        Text(
                                            text = metric.title,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Grouped Bar Chart View
                    if (chartGroups.isNotEmpty() && chartGroups.any { it.incomingValue > 0f || it.outgoingValue > 0f }) {
                        GroupedBarChartView(
                            groups = chartGroups,
                            selectedIndex = selectedGroupIndex,
                            onSelectGroup = { idx ->
                                selectedGroupIndex = if (selectedGroupIndex == idx) null else idx
                            }
                        )
                    } else {
                        // Empty State for Diagram
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Нет данных за выбранный период",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Legend Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(IncomingGreen)
                            )
                            Text(
                                text = "Входящие: " + if (selectedMetric == DiagramMetric.TIME) formatExactDuration(incomingDurationSec) else "$incomingCount",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(OutgoingBlue)
                            )
                            Text(
                                text = "Исходящие: " + if (selectedMetric == DiagramMetric.TIME) formatExactDuration(outgoingDurationSec) else "$outgoingCount",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        }
    }

    // Material 3 Date Range Picker Dialog constrained by oldest call record
    if (showDateRangePickerDialog) {
        val selectableDates = remember(oldestStartOfDayMillis, currentEndOfDayMillis) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis in (oldestStartOfDayMillis - 86400000L)..(currentEndOfDayMillis + 86400000L)
                }

                override fun isSelectableYear(year: Int): Boolean {
                    val startYear = Calendar.getInstance().apply { timeInMillis = oldestStartOfDayMillis }.get(Calendar.YEAR)
                    val currYear = Calendar.getInstance().get(Calendar.YEAR)
                    return year in startYear..currYear
                }
            }
        }

        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = customStartDateMillis ?: oldestStartOfDayMillis,
            initialSelectedEndDateMillis = customEndDateMillis ?: currentEndOfDayMillis,
            selectableDates = selectableDates
        )

        DatePickerDialog(
            onDismissRequest = { showDateRangePickerDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis
                        val end = dateRangePickerState.selectedEndDateMillis ?: start
                        if (start != null) {
                            customStartDateMillis = minOf(start, end ?: start).coerceAtLeast(oldestStartOfDayMillis)
                            customEndDateMillis = maxOf(start, end ?: start).coerceAtMost(currentEndOfDayMillis)
                            selectedPeriod = StatisticsPeriod.CUSTOM_RANGE
                        }
                        showDateRangePickerDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = SamsungGreen)
                ) {
                    Text("Применить", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePickerDialog = false }) {
                    Text("Отмена")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = {
                    Text(
                        text = "Выберите период",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GroupedBarChartView(
    groups: List<BarChartGroup>,
    selectedIndex: Int?,
    onSelectGroup: (Int) -> Unit
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(groups.size) {
        if (groups.size > 12) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    val maxVal = remember(groups) {
        val maxInGroup = groups.maxOfOrNull { maxOf(it.incomingValue, it.outgoingValue) } ?: 0f
        if (maxInGroup <= 0f) 1f else maxInGroup
    }

    val selectedGroup = selectedIndex?.let { groups.getOrNull(it) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Selected Group Detail Pill (shows on tap)
        if (selectedGroup != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedGroup.fullLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Вх: ${selectedGroup.incomingDisplay}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = IncomingGreen
                        )
                        Text(
                            text = "Исх: ${selectedGroup.outgoingDisplay}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OutgoingBlue
                        )
                    }
                }
            }
        }

        // Bars Container
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            val chartHeight = 110.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalArrangement = if (groups.size <= 7) Arrangement.SpaceEvenly else Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                groups.forEachIndexed { index, group ->
                    val isGroupSelected = selectedIndex == index
                    val incHeightFraction = (group.incomingValue / maxVal).coerceIn(0f, 1f)
                    val outHeightFraction = (group.outgoingValue / maxVal).coerceIn(0f, 1f)

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelectGroup(index) }
                            .background(
                                if (isGroupSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent
                            )
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        // Pair of vertical bars side by side
                        Box(
                            modifier = Modifier
                                .height(chartHeight)
                                .width(28.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(24.dp),
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                // Incoming Bar (Green)
                                val incBarHeight = (chartHeight * incHeightFraction).coerceAtLeast(if (group.incomingValue > 0f) 4.dp else 0.dp)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(incBarHeight)
                                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                        .background(if (group.incomingValue > 0f) IncomingGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                )

                                // Outgoing Bar (Blue)
                                val outBarHeight = (chartHeight * outHeightFraction).coerceAtLeast(if (group.outgoingValue > 0f) 4.dp else 0.dp)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(outBarHeight)
                                        .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                        .background(if (group.outgoingValue > 0f) OutgoingBlue else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // X-axis Label
                        Text(
                            text = group.label,
                            fontSize = 10.sp,
                            fontWeight = if (isGroupSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isGroupSelected) SamsungGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/**
 * Builds groups by days or months depending on the selected period.
 */
private fun buildChartGroups(
    logs: List<CallLogItem>,
    period: StatisticsPeriod,
    metric: DiagramMetric,
    customStartMillis: Long,
    customEndMillis: Long,
    oldestStartMillis: Long
): List<BarChartGroup> {
    val isTime = metric == DiagramMetric.TIME

    return when (period) {
        StatisticsPeriod.THIS_MONTH -> {
            val cal = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val maxDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
            val monthName = SimpleDateFormat("LLLL", Locale.getDefault()).format(cal.time)

            (1..maxDay).map { day ->
                cal.set(Calendar.DAY_OF_MONTH, day)
                val dayStart = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val dayEnd = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)

                val dayLogs = logs.filter { it.timestamp in dayStart..dayEnd }
                buildGroup("$day", "$day $monthName", dayLogs, isTime)
            }
        }
        StatisticsPeriod.LAST_MONTH -> {
            val cal = Calendar.getInstance().apply {
                add(Calendar.MONTH, -1)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
            val monthName = SimpleDateFormat("LLLL", Locale.getDefault()).format(cal.time)

            (1..maxDay).map { day ->
                cal.set(Calendar.DAY_OF_MONTH, day)
                val dayStart = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val dayEnd = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)

                val dayLogs = logs.filter { it.timestamp in dayStart..dayEnd }
                buildGroup("$day", "$day $monthName", dayLogs, isTime)
            }
        }
        StatisticsPeriod.CUSTOM_RANGE, StatisticsPeriod.ALL_TIME -> {
            val start = if (period == StatisticsPeriod.ALL_TIME) oldestStartMillis else customStartMillis
            val end = if (period == StatisticsPeriod.ALL_TIME) System.currentTimeMillis() else customEndMillis
            val spanDays = ((end - start) / 86400000L).toInt().coerceAtLeast(1)

            if (spanDays <= 35) {
                // Group by days
                val cal = Calendar.getInstance().apply {
                    timeInMillis = start
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val result = mutableListOf<BarChartGroup>()
                val dayFormat = SimpleDateFormat("d", Locale.getDefault())
                val fullFormat = SimpleDateFormat("d MMM", Locale.getDefault())

                while (cal.timeInMillis <= end) {
                    val dayStart = cal.timeInMillis
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    val dayEnd = cal.timeInMillis

                    val dayLogs = logs.filter { it.timestamp in dayStart..dayEnd }
                    result.add(
                        buildGroup(
                            dayFormat.format(Date(dayStart)),
                            fullFormat.format(Date(dayStart)),
                            dayLogs,
                            isTime
                        )
                    )

                    cal.add(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                }
                result
            } else {
                // Group by months
                val cal = Calendar.getInstance().apply {
                    timeInMillis = start
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val result = mutableListOf<BarChartGroup>()
                val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
                val fullFormat = SimpleDateFormat("LLLL yyyy", Locale.getDefault())

                while (cal.timeInMillis <= end) {
                    val monthStart = cal.timeInMillis
                    val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    cal.set(Calendar.DAY_OF_MONTH, lastDay)
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    val monthEnd = cal.timeInMillis

                    val monthLogs = logs.filter { it.timestamp in monthStart..monthEnd }
                    result.add(
                        buildGroup(
                            monthFormat.format(Date(monthStart)).replaceFirstChar { it.uppercase() },
                            fullFormat.format(Date(monthStart)),
                            monthLogs,
                            isTime
                        )
                    )

                    cal.add(Calendar.MONTH, 1)
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                }
                result
            }
        }
    }
}

private fun buildGroup(
    label: String,
    fullLabel: String,
    logs: List<CallLogItem>,
    isTime: Boolean
): BarChartGroup {
    val incLogs = logs.filter { it.type == CallType.INCOMING }
    val outLogs = logs.filter { it.type == CallType.OUTGOING }

    val incSec = incLogs.sumOf { it.duration }
    val outSec = outLogs.sumOf { it.duration }

    val incCount = incLogs.sumOf { it.count }
    val outCount = outLogs.sumOf { it.count }

    val incVal = if (isTime) ((incSec + 59) / 60).toFloat() else incCount.toFloat()
    val outVal = if (isTime) ((outSec + 59) / 60).toFloat() else outCount.toFloat()

    val incDisplay = if (isTime) formatExactDuration(incSec) else "$incCount"
    val outDisplay = if (isTime) formatExactDuration(outSec) else "$outCount"

    return BarChartGroup(
        label = label,
        fullLabel = fullLabel,
        incomingValue = incVal,
        outgoingValue = outVal,
        incomingDisplay = incDisplay,
        outgoingDisplay = outDisplay
    )
}

/**
 * Formats duration in exact minutes and hours: e.g. "12ч 15м", "12м", "1ч 07м", "0м".
 */
fun formatExactDuration(totalSeconds: Long): String {
    val totalMinutes = if (totalSeconds > 0) ((totalSeconds + 59) / 60) else 0L
    if (totalMinutes <= 0) return "0м"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        if (minutes > 0) "${hours}ч ${minutes}м" else "${hours}ч"
    } else {
        "${minutes}м"
    }
}
