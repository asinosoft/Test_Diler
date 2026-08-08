package com.example.test_dialer.ui.recents.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import com.example.test_dialer.data.model.FavoriteTab
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.test_dialer.data.model.CallLogItem
import com.example.test_dialer.data.model.CallType
import com.example.test_dialer.data.model.FavoriteContact
import com.example.test_dialer.data.repository.CallLogRepository
import com.example.test_dialer.ui.theme.IncomingGreen
import com.example.test_dialer.ui.theme.MissedRed
import com.example.test_dialer.ui.theme.OutgoingBlue
import com.example.test_dialer.ui.theme.SamsungGreen
import com.example.test_dialer.ui.theme.SamsungSmsBlue
import com.example.test_dialer.util.formatPhoneNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ContactPhoneNumber(
    val number: String,
    val label: String
)

@Composable
fun ContactDetailDialog(
    contact: FavoriteContact,
    initialTab: Int = 0,
    tabs: List<FavoriteTab> = emptyList(),
    onDismiss: () -> Unit,
    onCall: (String, Int?) -> Unit,
    onSms: (String) -> Unit,
    onRemoveFavorite: (FavoriteContact) -> Unit,
    onUpdateContact: (FavoriteContact) -> Unit = {},
    onAddTab: (String) -> FavoriteTab = { FavoriteTab("default", "Основные") }
) {
    val context = LocalContext.current
    var avatarBitmap by remember(contact.photoUri) { mutableStateOf<ImageBitmap?>(null) }
    var phoneNumbersList by remember(contact) {
        mutableStateOf(listOf(ContactPhoneNumber(number = contact.number, label = "Мобильный")))
    }
    var activeSimCount by remember { mutableIntStateOf(1) }
    var selectedTab by remember(initialTab, contact) { mutableIntStateOf(initialTab) }

    var historyLogs by remember { mutableStateOf<List<CallLogItem>>(emptyList()) }
    var isLoadingHistory by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                @Suppress("MissingPermission")
                val count = sm?.activeSubscriptionInfoCount ?: 1
                activeSimCount = if (count > 1) count else 1
            } catch (e: Exception) {
                activeSimCount = 1
            }
        }
    }

    var messengerAccountsList by remember(contact) { mutableStateOf<List<MessengerAccount>>(emptyList()) }

    LaunchedEffect(contact) {
        withContext(Dispatchers.IO) {
            val highResPhotoUri = getHighResContactPhotoUri(context, contact.number) ?: contact.photoUri
            if (!highResPhotoUri.isNullOrEmpty()) {
                try {
                    val uri = Uri.parse(highResPhotoUri)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        avatarBitmap = bitmap?.asImageBitmap()
                    }
                } catch (e: Exception) {
                    avatarBitmap = null
                }
            } else {
                avatarBitmap = null
            }

            // Query all phone numbers for this contact
            val loadedNumbers = loadContactPhoneNumbers(context, contact)
            if (loadedNumbers.isNotEmpty()) {
                phoneNumbersList = loadedNumbers
            }

            // Load messenger accounts for this contact
            messengerAccountsList = loadMessengerAccounts(context, contact)
        }
    }

    // Load call history when History tab selected
    LaunchedEffect(selectedTab, contact) {
        if (selectedTab == 1) {
            isLoadingHistory = true
            withContext(Dispatchers.IO) {
                try {
                    val repository = CallLogRepository(context)
                    val allLogs = repository.getCallLogs()
                    val cleanContactNum = contact.number.replace(Regex("[^0-9+]"), "")

                    historyLogs = allLogs.filter { log ->
                        val cleanLogNum = log.number.replace(Regex("[^0-9+]"), "")
                        (cleanContactNum.isNotBlank() && cleanLogNum.takeLast(7) == cleanContactNum.takeLast(7)) ||
                                (!log.name.isNullOrBlank() && log.name.equals(contact.name, ignoreCase = true))
                    }
                } catch (e: Exception) {
                    historyLogs = emptyList()
                }
            }
            isLoadingHistory = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BackHandler {
            onDismiss()
        }

        val avatarBgColor = remember(contact.name) {
            val colors = listOf(
                Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8),
                Color(0xFF9575CD), Color(0xFF7986CB), Color(0xFF64B5F6),
                Color(0xFF4FC3F7), Color(0xFF4DB6AC), Color(0xFF81C784),
                Color(0xFFAED581), Color(0xFFFF8A65), Color(0xFFA1887F)
            )
            val index = (contact.name.hashCode() and Int.MAX_VALUE) % colors.size
            colors[index]
        }

        val scrollState = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Full Screen Scrollable Container
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // TOP HERO PHOTO HEADER
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(avatarBgColor)
                ) {
                    val bitmap = avatarBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = contact.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Vibrant gradient placeholder with large letter
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            avatarBgColor,
                                            avatarBgColor.copy(alpha = 0.7f)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val initial = contact.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                            Text(
                                text = initial,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 80.sp
                            )
                        }
                    }

                    // Bottom Gradient Overlay for Smooth Card Blend
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.4f)
                                    )
                                )
                            )
                    )
                }

                // BOTTOM CONTENT CARD
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-24).dp),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Contact Name
                        Text(
                            text = contact.name,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // FLOATING TAB BAR (Контакт | История | Настройки)
                        FloatingTabBar(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it }
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // TAB CONTENT SWITCHING
                        when (selectedTab) {
                            0 -> {
                                // TAB 0: КОНТАКТ
                                ContactTabContent(
                                    phoneNumbersList = phoneNumbersList,
                                    messengerAccountsList = messengerAccountsList,
                                    activeSimCount = activeSimCount,
                                    contact = contact,
                                    context = context,
                                    onCall = onCall,
                                    onSms = onSms,
                                    onDismiss = onDismiss,
                                    onRemoveFavorite = onRemoveFavorite
                                )
                            }
                            1 -> {
                                // TAB 1: ИСТОРИЯ
                                HistoryTabContent(
                                    isLoading = isLoadingHistory,
                                    logs = historyLogs
                                )
                            }
                            2 -> {
                                // TAB 2: НАСТРОЙКИ
                                SettingsTabContent(
                                    contact = contact,
                                    context = context,
                                    tabs = tabs,
                                    onDismiss = onDismiss,
                                    onRemoveFavorite = onRemoveFavorite,
                                    onUpdateContact = onUpdateContact,
                                    onAddTab = onAddTab
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }

            // TOP FLOATING TOOLBAR OVER PHOTO (Back Button & Shadow Gradient)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingTabBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabs = listOf(
                Triple(0, "Контакт", Icons.Default.Person),
                Triple(1, "История", Icons.Default.History),
                Triple(2, "Настройки", Icons.Default.Settings)
            )

            tabs.forEach { (index, title, icon) ->
                val isSelected = selectedTab == index
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onTabSelected(index) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) SamsungGreen else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactTabContent(
    phoneNumbersList: List<ContactPhoneNumber>,
    messengerAccountsList: List<MessengerAccount>,
    activeSimCount: Int,
    contact: FavoriteContact,
    context: Context,
    onCall: (String, Int?) -> Unit,
    onSms: (String) -> Unit,
    onDismiss: () -> Unit,
    onRemoveFavorite: (FavoriteContact) -> Unit
) {
    val primaryNumber = phoneNumbersList.firstOrNull()?.number ?: contact.number

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ALL PHONE NUMBERS CARD
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column {
                phoneNumbersList.forEachIndexed { index, phoneItem ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = phoneItem.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatPhoneNumber(phoneItem.number),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (activeSimCount > 1) {
                                // SIM 1 Call Button
                                IconButton(
                                    onClick = {
                                        onDismiss()
                                        onCall(phoneItem.number, 1)
                                    },
                                    modifier = Modifier
                                        .size(37.6.dp)
                                        .clip(CircleShape)
                                        .background(SamsungSmsBlue.copy(alpha = 0.12f))
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Phone,
                                            contentDescription = "Вызов SIM 1",
                                            tint = SamsungSmsBlue,
                                            modifier = Modifier
                                                .size(26.dp)
                                                .align(Alignment.Center)
                                                .offset(x = (-1).dp, y = 2.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(top = 7.dp, end = 7.dp)
                                        ) {
                                            SimCardBadge(simNumber = 1)
                                        }
                                    }
                                }

                                // SIM 2 Call Button
                                IconButton(
                                    onClick = {
                                        onDismiss()
                                        onCall(phoneItem.number, 2)
                                    },
                                    modifier = Modifier
                                        .size(37.6.dp)
                                        .clip(CircleShape)
                                        .background(SamsungGreen.copy(alpha = 0.12f))
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Phone,
                                            contentDescription = "Вызов SIM 2",
                                            tint = SamsungGreen,
                                            modifier = Modifier
                                                .size(26.dp)
                                                .align(Alignment.Center)
                                                .offset(x = (-1).dp, y = 2.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(top = 7.dp, end = 7.dp)
                                        ) {
                                            SimCardBadge(simNumber = 2)
                                        }
                                    }
                                }
                            } else {
                                // Single Call Button
                                IconButton(
                                    onClick = {
                                        onDismiss()
                                        onCall(phoneItem.number, null)
                                    },
                                    modifier = Modifier
                                        .size(37.6.dp)
                                        .clip(CircleShape)
                                        .background(SamsungGreen.copy(alpha = 0.12f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Phone,
                                        contentDescription = "Вызов",
                                        tint = SamsungGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // SMS Button
                            IconButton(
                                onClick = {
                                    onDismiss()
                                    onSms(phoneItem.number)
                                },
                                modifier = Modifier
                                    .size(37.6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Message,
                                    contentDescription = "SMS",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // MESSENGER ACCOUNTS CARD
        if (messengerAccountsList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column {
                    messengerAccountsList.forEachIndexed { index, messenger ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = messenger.messengerName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = messenger.brandColor
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatPhoneNumber(messenger.accountDetail),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                // 1. Chat
                                if (messenger.chatIntent != null) {
                                    IconButton(
                                        onClick = {
                                            try {
                                                context.startActivity(messenger.chatIntent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Не удалось открыть чат ${messenger.messengerName}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(37.6.dp)
                                            .clip(CircleShape)
                                            .background(messenger.brandColor.copy(alpha = 0.15f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Message,
                                            contentDescription = "Чат",
                                            tint = messenger.brandColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // 2. Audio Call
                                if (messenger.audioCallIntent != null) {
                                    IconButton(
                                        onClick = {
                                            try {
                                                context.startActivity(messenger.audioCallIntent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Не удалось совершить звонок в ${messenger.messengerName}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(37.6.dp)
                                            .clip(CircleShape)
                                            .background(messenger.brandColor.copy(alpha = 0.15f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Phone,
                                            contentDescription = "Аудиовызов",
                                            tint = messenger.brandColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // 3. Video Call
                                if (messenger.videoCallIntent != null) {
                                    IconButton(
                                        onClick = {
                                            try {
                                                context.startActivity(messenger.videoCallIntent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Не удалось начать видеовызов в ${messenger.messengerName}", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(37.6.dp)
                                            .clip(CircleShape)
                                            .background(messenger.brandColor.copy(alpha = 0.15f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Videocam,
                                            contentDescription = "Видеовызов",
                                            tint = messenger.brandColor,
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

        Spacer(modifier = Modifier.height(16.dp))

        // Additional Options Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column {
                OptionRow(
                    icon = Icons.Default.ContentCopy,
                    label = "Скопировать номер",
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Phone Number", primaryNumber)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Номер скопирован", Toast.LENGTH_SHORT).show()
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                OptionRow(
                    icon = Icons.Default.Delete,
                    label = "Удалить из избранных",
                    labelColor = MaterialTheme.colorScheme.error,
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = {
                        onDismiss()
                        onRemoveFavorite(contact)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Quick Action Buttons Row (Call, SMS, Info) placed at the bottom
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionButtonItem(
                icon = Icons.Default.Phone,
                label = "Вызов",
                containerColor = SamsungGreen,
                contentColor = Color.White,
                onClick = {
                    onDismiss()
                    onCall(primaryNumber, null)
                }
            )

            ActionButtonItem(
                icon = Icons.AutoMirrored.Filled.Message,
                label = "SMS",
                containerColor = SamsungSmsBlue,
                contentColor = Color.White,
                onClick = {
                    onDismiss()
                    onSms(primaryNumber)
                }
            )

            ActionButtonItem(
                icon = Icons.Default.Person,
                label = "Инфо",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = {
                    openSystemContact(context, primaryNumber)
                }
            )
        }
    }
}

@Composable
private fun HistoryTabContent(
    isLoading: Boolean,
    logs: List<CallLogItem>
) {
    val groupedLogs = remember(logs) {
        logs.groupBy { formatDateHeader(it.timestamp) }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Загрузка истории...",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
            }
        } else if (logs.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "История вызовов отсутствует",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            groupedLogs.forEach { (dateHeader, logsInDay) ->
                if (dateHeader.isNotEmpty()) {
                    Text(
                        text = dateHeader,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, top = 12.dp, bottom = 4.dp)
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column {
                        logsInDay.forEachIndexed { index, item ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                    thickness = 1.dp,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val (icon, color, desc) = when (item.type) {
                                        CallType.INCOMING -> Triple(Icons.AutoMirrored.Filled.CallReceived, IncomingGreen, "Входящий")
                                        CallType.OUTGOING -> Triple(Icons.AutoMirrored.Filled.CallMade, OutgoingBlue, "Исходящий")
                                        CallType.MISSED -> Triple(Icons.AutoMirrored.Filled.CallMissed, MissedRed, "Пропущенный")
                                        CallType.REJECTED -> Triple(Icons.Default.CallEnd, MissedRed, "Отклоненный")
                                    }

                                    Icon(
                                        imageVector = icon,
                                        contentDescription = desc,
                                        tint = color,
                                        modifier = Modifier.size(20.dp)
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column {
                                        Text(
                                            text = formatTimeOnly(item.timestamp),
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (item.type == CallType.MISSED || item.type == CallType.REJECTED) MissedRed else MaterialTheme.colorScheme.onSurface
                                        )

                                        Spacer(modifier = Modifier.height(3.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            SimCardBadge(simNumber = item.simNumber)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = formatPhoneNumber(item.number),
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = formatCallDuration(item.duration),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    textAlign = TextAlign.End
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
private fun SettingsTabContent(
    contact: FavoriteContact,
    context: Context,
    tabs: List<FavoriteTab>,
    onDismiss: () -> Unit,
    onRemoveFavorite: (FavoriteContact) -> Unit,
    onUpdateContact: (FavoriteContact) -> Unit,
    onAddTab: (String) -> FavoriteTab
) {
    var isFavorite by remember { mutableStateOf(true) }
    var selectedTabId by remember(contact.tabId) { mutableStateOf(contact.tabId) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Card 1: Favorite Toggle & Tab Choice
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Избранное",
                            tint = SamsungGreen,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = "В избранных контактах",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Switch(
                        checked = isFavorite,
                        onCheckedChange = { checked ->
                            isFavorite = checked
                            if (!checked) {
                                onDismiss()
                                onRemoveFavorite(contact)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SamsungGreen
                        )
                    )
                }

                if (isFavorite) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    var dropdownExpanded by remember { mutableStateOf(false) }
                    var showCreateTabDialog by remember { mutableStateOf(false) }
                    var newTabNameInput by remember { mutableStateOf("") }

                    val currentTabName = tabs.find { it.id == selectedTabId }?.name ?: "Основные"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dropdownExpanded = true }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FolderSpecial,
                                contentDescription = "Вкладка",
                                tint = SamsungGreen,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Вкладка избранного",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentTabName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SamsungGreen
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Выбрать",
                                    tint = SamsungGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false }
                            ) {
                                tabs.forEach { tab ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = tab.name,
                                                fontWeight = if (tab.id == selectedTabId) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            dropdownExpanded = false
                                            selectedTabId = tab.id
                                            onUpdateContact(contact.copy(tabId = tab.id))
                                        }
                                    )
                                }

                                HorizontalDivider()

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Создать",
                                                tint = SamsungGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "+ Создать новую",
                                                fontWeight = FontWeight.Bold,
                                                color = SamsungGreen
                                            )
                                        }
                                    },
                                    onClick = {
                                        dropdownExpanded = false
                                        newTabNameInput = ""
                                        showCreateTabDialog = true
                                    }
                                )
                            }
                        }
                    }

                    if (showCreateTabDialog) {
                        AlertDialog(
                            onDismissRequest = { showCreateTabDialog = false },
                            title = { Text("Новая вкладка избранного", fontWeight = FontWeight.Bold) },
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
                                            val createdTab = onAddTab(newTabNameInput.trim())
                                            selectedTabId = createdTab.id
                                            onUpdateContact(contact.copy(tabId = createdTab.id))
                                            showCreateTabDialog = false
                                        }
                                    }
                                ) {
                                    Text("Создать", color = SamsungGreen, fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showCreateTabDialog = false }) {
                                    Text("Отмена")
                                }
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 2: Contact Options
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Column {
                OptionRow(
                    icon = Icons.Default.ContentCopy,
                    label = "Скопировать данные контакта",
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Contact Info", "${contact.name}: ${contact.number}")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Данные скопированы", Toast.LENGTH_SHORT).show()
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                OptionRow(
                    icon = Icons.Default.Person,
                    label = "Открыть в контактах устройства",
                    onClick = {
                        openSystemContact(context, contact.number)
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                OptionRow(
                    icon = Icons.Default.Delete,
                    label = "Удалить из списка избранных",
                    labelColor = MaterialTheme.colorScheme.error,
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = {
                        onDismiss()
                        onRemoveFavorite(contact)
                    }
                )
            }
        }
    }
}

@Composable
private fun ActionButtonItem(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            modifier = Modifier.size(60.dp),
            shape = CircleShape,
            color = containerColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    label: String,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    iconTint: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = labelColor
        )
    }
}

private suspend fun loadContactPhoneNumbers(
    context: Context,
    contact: FavoriteContact
): List<ContactPhoneNumber> = withContext(Dispatchers.IO) {
    val numbersList = mutableListOf<ContactPhoneNumber>()
    val addedCleanNumbers = mutableSetOf<String>()

    try {
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.LABEL
            ),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?",
            arrayOf(contact.name),
            "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC"
        )

        cursor?.use { c ->
            val numberIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
            val labelIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)

            while (c.moveToNext()) {
                val num = if (numberIdx != -1) c.getString(numberIdx) else ""
                val type = if (typeIdx != -1) c.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
                val customLabel = if (labelIdx != -1) c.getString(labelIdx) else null

                val cleanNum = num.replace(Regex("[^0-9+]"), "")
                if (cleanNum.isNotBlank() && !addedCleanNumbers.contains(cleanNum)) {
                    addedCleanNumbers.add(cleanNum)
                    val labelStr = getPhoneTypeLabel(type, customLabel)
                    numbersList.add(ContactPhoneNumber(number = num, label = labelStr))
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    val cleanMain = contact.number.replace(Regex("[^0-9+]"), "")
    if (cleanMain.isNotBlank() && !addedCleanNumbers.contains(cleanMain)) {
        numbersList.add(0, ContactPhoneNumber(number = contact.number, label = "Мобильный"))
    } else if (numbersList.isEmpty() && contact.number.isNotBlank()) {
        numbersList.add(ContactPhoneNumber(number = contact.number, label = "Мобильный"))
    }

    numbersList
}

private fun getPhoneTypeLabel(type: Int, customLabel: String?): String {
    return when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Мобильный"
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Домашний"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Рабочий"
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "Основной"
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK -> "Рабочий факс"
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> "Домашний факс"
        ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> "Пейджер"
        ContactsContract.CommonDataKinds.Phone.TYPE_OTHER -> "Другой"
        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> customLabel ?: "Другой"
        else -> "Мобильный"
    }
}

private class DetailSimCardShape(private val cutSizeDp: Float = 2.5f) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cut = density.density * cutSizeDp
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width - cut, 0f)
            lineTo(size.width, cut)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun SimCardBadge(simNumber: Int) {
    val simBgColor = if (simNumber == 2) SamsungGreen else SamsungSmsBlue

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 10.dp, height = 12.dp)
            .clip(DetailSimCardShape(cutSizeDp = 2.5f))
            .background(simBgColor)
    ) {
        Text(
            text = "$simNumber",
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            style = TextStyle(
                platformStyle = PlatformTextStyle(
                    includeFontPadding = false
                )
            ),
            modifier = Modifier.offset(y = (-0.5).dp)
        )
    }
}

private fun openSystemContact(context: Context, contactNumber: String) {
    try {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(contactNumber))
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CONTACTS)
            }
            context.startActivity(intent)
        } catch (ex: Exception) {
            Toast.makeText(context, "Не удалось открыть информацию о контакте", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun formatDateHeader(timestamp: Long): String {
    if (timestamp == 0L) return ""
    if (android.text.format.DateUtils.isToday(timestamp)) return "Сегодня"
    if (android.text.format.DateUtils.isToday(timestamp + 24 * 3600 * 1000L)) return "Вчера"

    val ruLocale = Locale.forLanguageTag("ru")
    val dateStr = SimpleDateFormat("d MMMM", ruLocale).format(Date(timestamp))
    val dayOfWeek = SimpleDateFormat("EEEE", ruLocale).format(Date(timestamp))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(ruLocale) else it.toString() }

    return "$dateStr, $dayOfWeek"
}

private fun formatTimeOnly(timestamp: Long): String {
    if (timestamp == 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun formatCallDate(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val ruLocale = Locale.forLanguageTag("ru")
    return SimpleDateFormat("d MMMM, HH:mm", ruLocale).format(Date(timestamp))
}

private fun getHighResContactPhotoUri(context: Context, contactNumber: String): String? {
    if (contactNumber.isBlank()) return null
    return try {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(contactNumber)
        )
        val projection = arrayOf(
            ContactsContract.PhoneLookup.PHOTO_URI,
            ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
        )
        val cursor = context.contentResolver.query(uri, projection, null, null, null)
        var photoUri: String? = null
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val fullIndex = c.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                val thumbIndex = c.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)
                photoUri = if (fullIndex != -1) c.getString(fullIndex) else null
                if (photoUri.isNullOrEmpty() && thumbIndex != -1) {
                    photoUri = c.getString(thumbIndex)
                }
            }
        }
        photoUri
    } catch (e: Exception) {
        null
    }
}

private fun formatCallDuration(seconds: Long): String {
    if (seconds <= 0L) return "Без ответа"
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "$m мин $s сек" else "$s сек"
}

private data class MessengerAccount(
    val id: String,
    val packageName: String,
    val messengerName: String,
    val accountDetail: String,
    val brandColor: Color,
    val chatIntent: Intent?,
    val audioCallIntent: Intent?,
    val videoCallIntent: Intent?
)

private fun isPackageInstalled(context: Context, packageName: String): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: Exception) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            launchIntent != null
        } catch (ex: Exception) {
            false
        }
    }
}

private fun loadMessengerAccounts(context: Context, contact: FavoriteContact): List<MessengerAccount> {
    val list = mutableListOf<MessengerAccount>()
    val number = contact.number
    if (number.isBlank()) return list
    val cleanDigits = number.replace(Regex("[^0-9]"), "")
    if (cleanDigits.isBlank()) return list

    val installedWhatsApp = isPackageInstalled(context, "com.whatsapp") || isPackageInstalled(context, "com.whatsapp.w4b")
    val installedTelegram = isPackageInstalled(context, "org.telegram.messenger")
    val installedViber = isPackageInstalled(context, "com.viber.voip")
    val installedSignal = isPackageInstalled(context, "org.thoughtcrime.securesms")
    val installedSkype = isPackageInstalled(context, "com.skype.raider") || isPackageInstalled(context, "com.skype.android")
    val installedVK = isPackageInstalled(context, "com.vk.im") || isPackageInstalled(context, "com.vkontakte.android")
    val installedMAX = isPackageInstalled(context, "ru.max.messenger") || isPackageInstalled(context, "com.max.app") || isPackageInstalled(context, "ru.vk.max")
    val installedMessenger = isPackageInstalled(context, "com.facebook.orca")
    val installedSnapchat = isPackageInstalled(context, "com.snapchat.android")
    val installedWeChat = isPackageInstalled(context, "com.tencent.mm")

    if (installedWhatsApp) {
        list.add(
            MessengerAccount(
                id = "whatsapp",
                packageName = "com.whatsapp",
                messengerName = "WhatsApp",
                accountDetail = number,
                brandColor = Color(0xFF25D366),
                chatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanDigits")),
                audioCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanDigits")),
                videoCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$cleanDigits"))
            )
        )
    }

    if (installedTelegram) {
        list.add(
            MessengerAccount(
                id = "telegram",
                packageName = "org.telegram.messenger",
                messengerName = "Telegram",
                accountDetail = number,
                brandColor = Color(0xFF24A1DE),
                chatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+$cleanDigits")),
                audioCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+$cleanDigits")),
                videoCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+$cleanDigits"))
            )
        )
    }

    if (installedViber) {
        list.add(
            MessengerAccount(
                id = "viber",
                packageName = "com.viber.voip",
                messengerName = "Viber",
                accountDetail = number,
                brandColor = Color(0xFF7360F2),
                chatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("viber://chat?number=+$cleanDigits")),
                audioCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse("viber://calls?number=+$cleanDigits")),
                videoCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse("viber://calls?number=+$cleanDigits"))
            )
        )
    }

    if (installedSignal) {
        list.add(
            MessengerAccount(
                id = "signal",
                packageName = "org.thoughtcrime.securesms",
                messengerName = "Signal",
                accountDetail = number,
                brandColor = Color(0xFF3A76F0),
                chatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://signal.me/#p/+$cleanDigits")),
                audioCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://signal.me/#p/+$cleanDigits")),
                videoCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://signal.me/#p/+$cleanDigits"))
            )
        )
    }

    if (installedSkype) {
        list.add(
            MessengerAccount(
                id = "skype",
                packageName = "com.skype.raider",
                messengerName = "Skype",
                accountDetail = number,
                brandColor = Color(0xFF00AFF0),
                chatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("skype:+$cleanDigits?chat")),
                audioCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse("skype:+$cleanDigits?call")),
                videoCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse("skype:+$cleanDigits?call&video=true"))
            )
        )
    }

    if (installedVK) {
        list.add(
            MessengerAccount(
                id = "vk",
                packageName = "com.vk.im",
                messengerName = "VK Messenger",
                accountDetail = number,
                brandColor = Color(0xFF0077FF),
                chatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://vk.me/+$cleanDigits")),
                audioCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://vk.me/+$cleanDigits")),
                videoCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://vk.me/+$cleanDigits"))
            )
        )
    }

    if (installedMAX) {
        list.add(
            MessengerAccount(
                id = "max",
                packageName = "ru.max.messenger",
                messengerName = "MAX",
                accountDetail = number,
                brandColor = Color(0xFF2A5885),
                chatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://max.ru/+$cleanDigits")),
                audioCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://max.ru/+$cleanDigits")),
                videoCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://max.ru/+$cleanDigits"))
            )
        )
    }

    if (installedMessenger) {
        list.add(
            MessengerAccount(
                id = "messenger",
                packageName = "com.facebook.orca",
                messengerName = "Messenger",
                accountDetail = number,
                brandColor = Color(0xFF0084FF),
                chatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.me/+$cleanDigits")),
                audioCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.me/+$cleanDigits")),
                videoCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.me/+$cleanDigits"))
            )
        )
    }

    if (installedSnapchat) {
        list.add(
            MessengerAccount(
                id = "snapchat",
                packageName = "com.snapchat.android",
                messengerName = "Snapchat",
                accountDetail = number,
                brandColor = Color(0xFFE5C100),
                chatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://snapchat.com/add/+$cleanDigits")),
                audioCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://snapchat.com/add/+$cleanDigits")),
                videoCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://snapchat.com/add/+$cleanDigits"))
            )
        )
    }

    if (installedWeChat) {
        list.add(
            MessengerAccount(
                id = "wechat",
                packageName = "com.tencent.mm",
                messengerName = "WeChat",
                accountDetail = number,
                brandColor = Color(0xFF07C160),
                chatIntent = Intent(Intent.ACTION_VIEW, Uri.parse("weixin://dl/chat")),
                audioCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse("weixin://dl/chat")),
                videoCallIntent = Intent(Intent.ACTION_VIEW, Uri.parse("weixin://dl/chat"))
            )
        )
    }

    return list
}
