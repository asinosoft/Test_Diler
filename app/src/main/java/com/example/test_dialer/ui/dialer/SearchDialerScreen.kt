package com.example.test_dialer.ui.dialer

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import android.telephony.SubscriptionManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_dialer.ui.recents.RecentsViewModel
import com.example.test_dialer.ui.recents.SearchDialerItem
import com.example.test_dialer.ui.theme.SamsungGreen
import com.example.test_dialer.ui.theme.SamsungSmsBlue
import com.example.test_dialer.util.formatPhoneNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SearchDialerScreen(
    viewModel: RecentsViewModel,
    onCall: (String, Int?) -> Unit,
    onSms: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val dialerQuery by viewModel.dialerQuery.collectAsState()
    val results by viewModel.filteredDialerResults.collectAsState()

    val defaultSimSlot = remember(context) {
        try {
            val subManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            val defaultSubId = SubscriptionManager.getDefaultVoiceSubscriptionId()
            if (subManager != null && defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                @Suppress("MissingPermission")
                val activeList = subManager.activeSubscriptionInfoList
                val matchedInfo = activeList?.find { it.subscriptionId == defaultSubId }
                if (matchedInfo != null) {
                    matchedInfo.simSlotIndex + 1
                } else 1
            } else 1
        } catch (e: Exception) {
            1
        }
    }

    var selectedSimSlot by remember { mutableIntStateOf(defaultSimSlot) }
    var isDialpadVisible by remember { mutableStateOf(true) }

    val targetSimBgColor = if (selectedSimSlot == 1) SamsungSmsBlue else SamsungGreen
    val animatedSimBgColor by animateColorAsState(
        targetValue = targetSimBgColor,
        animationSpec = tween(durationMillis = 200),
        label = "simBgColor"
    )

    BackHandler {
        onClose()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp)
        ) {
            // Top Search Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                OutlinedTextField(
                    value = dialerQuery,
                    onValueChange = { viewModel.onDialerQueryChange(it) },
                    placeholder = {
                        Text(
                            text = "Поиск по имени или номеру...",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Поиск",
                            tint = SamsungGreen
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (dialerQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clearDialerQuery() }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Очистить"
                                    )
                                }
                            }
                            IconButton(onClick = { isDialpadVisible = !isDialpadVisible }) {
                                Icon(
                                    imageVector = if (isDialpadVisible) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                    contentDescription = if (isDialpadVisible) "Скрыть клавиатуру" else "Показать клавиатуру",
                                    tint = SamsungGreen
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SamsungGreen,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            // Results List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (results.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (dialerQuery.isBlank()) "Введите номер или имя" else "Контакты не найдены",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = results,
                            key = { it.id }
                        ) { item ->
                            SearchDialerResultCard(
                                item = item,
                                context = context,
                                onCall = { num -> onCall(num, selectedSimSlot) },
                                onSms = onSms
                            )
                        }
                    }
                }
            }

            // Bottom Dialpad Component
            AnimatedVisibility(
                visible = isDialpadVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    shadowElevation = 12.dp,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // T9 Dialpad Buttons Grid (3x4)
                        val dialpadButtons = remember {
                            listOf(
                                Triple("1", "", ""),
                                Triple("2", "ABC", "АБВГ"),
                                Triple("3", "DEF", "ДЕЖЗ"),
                                Triple("4", "GHI", "ИЙКЛ"),
                                Triple("5", "JKL", "МНОПР"),
                                Triple("6", "MNO", "СТУФ"),
                                Triple("7", "PQRS", "ХЦЧШ"),
                                Triple("8", "TUV", "ЩЪЫЬ"),
                                Triple("9", "WXYZ", "ЭЮЯ"),
                                Triple("*", "", ""),
                                Triple("0", "+", ""),
                                Triple("#", "", "")
                            )
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for (row in 0 until 4) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    for (col in 0 until 3) {
                                        val index = row * 3 + col
                                        val (digit, lettersEn, lettersRu) = dialpadButtons[index]

                                        Surface(
                                            modifier = Modifier
                                                .size(width = 90.dp, height = 56.dp)
                                                .clip(RoundedCornerShape(20.dp))
                                                .clickable {
                                                    viewModel.appendDialerDigit(digit)
                                                },
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.background
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = digit,
                                                    fontSize = 22.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                                if (lettersEn.isNotEmpty() || lettersRu.isNotEmpty()) {
                                                    Text(
                                                        text = "$lettersEn $lettersRu".trim(),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Bottom Actions Row: Equal Distance Spacing + Lifted Above System Gesture Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Single Animated SIM Selector Toggle Button
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = animatedSimBgColor,
                                modifier = Modifier.clickable {
                                    selectedSimSlot = if (selectedSimSlot == 1) 2 else 1
                                }
                            ) {
                                Text(
                                    text = "SIM $selectedSimSlot",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }

                            // Center: Green Call FAB Button
                            FloatingActionButton(
                                onClick = {
                                    if (dialerQuery.isNotBlank()) {
                                        onCall(dialerQuery, selectedSimSlot)
                                    }
                                },
                                containerColor = SamsungGreen,
                                contentColor = Color.White,
                                shape = CircleShape,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Call,
                                    contentDescription = "Позвонить",
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            // Backspace Delete Button
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { viewModel.deleteDialerDigit() },
                                            onLongPress = { viewModel.clearDialerQuery() }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Backspace,
                                    contentDescription = "Удалить",
                                    tint = if (dialerQuery.isNotEmpty()) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchDialerResultCard(
    item: SearchDialerItem,
    context: Context,
    onCall: (String) -> Unit,
    onSms: (String) -> Unit
) {
    var photoBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(item.photoUri) {
        if (!item.photoUri.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(item.photoUri)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        photoBitmap = bitmap?.asImageBitmap()
                    }
                } catch (e: Exception) {
                    photoBitmap = null
                }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCall(item.number) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar Circle
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (photoBitmap != null) {
                        Image(
                            bitmap = photoBitmap!!,
                            contentDescription = "Аватар",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        val initial = item.name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
                        Text(
                            text = initial,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Contact Name & Number
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatPhoneNumber(item.number),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Quick Call & SMS Action Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSms(item.number) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "SMS",
                        tint = SamsungSmsBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = { onCall(item.number) }) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Позвонить",
                        tint = SamsungGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
