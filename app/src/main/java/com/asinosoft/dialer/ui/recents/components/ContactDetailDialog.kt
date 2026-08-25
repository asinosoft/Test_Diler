package com.asinosoft.dialer.ui.recents.components

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.asinosoft.dialer.data.model.CallLogItem
import com.asinosoft.dialer.data.model.CallType
import com.asinosoft.dialer.data.model.FavoriteContact
import com.asinosoft.dialer.data.model.FavoriteTab
import com.asinosoft.dialer.data.repository.CallLogRepository
import com.asinosoft.dialer.data.repository.ContactsRepository
import com.asinosoft.dialer.ui.theme.IncomingGreen
import com.asinosoft.dialer.ui.theme.MissedRed
import com.asinosoft.dialer.ui.theme.OutgoingBlue
import com.asinosoft.dialer.ui.theme.SamsungGreen
import com.asinosoft.dialer.ui.theme.SamsungSmsBlue
import com.asinosoft.dialer.util.formatPhoneNumber
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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
                val sm =
                    context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

                @Suppress("MissingPermission")
                val count = sm?.activeSubscriptionInfoCount ?: 1
                activeSimCount = if (count > 1) count else 1
            } catch (_: Exception) {
                activeSimCount = 1
            }
        }
    }

    var messengerAccountsList by remember(contact) {
        mutableStateOf<List<MessengerAccount>>(
            emptyList()
        )
    }
    var emailsList by remember(contact) { mutableStateOf<List<ContactEmail>>(emptyList()) }
    var birthdayInfo by remember(contact) { mutableStateOf<ContactBirthday?>(null) }
    var importantDatesList by remember(contact) {
        mutableStateOf<List<ContactImportantDate>>(
            emptyList()
        )
    }

    LaunchedEffect(contact) {
        withContext(Dispatchers.IO) {
            val highResPhotoUri =
                getHighResContactPhotoUri(context, contact.number) ?: contact.photoUri
            if (!highResPhotoUri.isNullOrEmpty()) {
                try {
                    val uri = highResPhotoUri.toUri()
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        avatarBitmap = bitmap?.asImageBitmap()
                    }
                } catch (_: Exception) {
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

            // Load emails for this contact
            emailsList = loadContactEmails(context, contact)

            // Load birthday for this contact
            birthdayInfo = loadContactBirthday(context, contact)

            // Load custom important dates
            importantDatesList =
                getImportantDates(context, getContactCustomKey(contact), contact.number)
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
                        (cleanContactNum.isNotBlank() && cleanLogNum.takeLast(7) == cleanContactNum.takeLast(
                            7
                        )) ||
                                (!log.name.isNullOrBlank() && log.name.equals(
                                    contact.name,
                                    ignoreCase = true
                                ))
                    }
                } catch (_: Exception) {
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
                            val initial =
                                contact.name.trim().firstOrNull()?.uppercaseChar()?.toString()
                                    ?: "?"
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
                                    onUpdateMessengerAccounts = { messengerAccountsList = it },
                                    onUpdatePhoneNumbers = { phoneNumbersList = it },
                                    onUpdateEmails = { emailsList = it },
                                    emailsList = emailsList,
                                    birthdayInfo = birthdayInfo,
                                    importantDatesList = importantDatesList,
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
                                    phoneNumbersList = phoneNumbersList,
                                    messengerAccountsList = messengerAccountsList,
                                    onUpdateMessengerAccounts = { messengerAccountsList = it },
                                    emailsList = emailsList,
                                    activeSimCount = activeSimCount,
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

            var showEditContactDialog by remember { mutableStateOf(false) }

            // TOP FLOATING TOOLBAR OVER PHOTO (Back Button & Three Dots Menu)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(start = 12.dp, end = 12.dp, top = 38.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Button
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

                    // Three Dots Menu
                    Box {
                        var topMenuExpanded by remember { mutableStateOf(false) }

                        IconButton(
                            onClick = { topMenuExpanded = true },
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.35f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Еще",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = topMenuExpanded,
                            onDismissRequest = { topMenuExpanded = false }
                        ) {
                            // 1. Поделиться
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Поделиться",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Поделиться", fontSize = 14.sp)
                                    }
                                },
                                onClick = {
                                    topMenuExpanded = false
                                    try {
                                        val shareText = "${contact.name}\n${contact.number}"
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                            type = "text/plain"
                                        }
                                        context.startActivity(
                                            Intent.createChooser(
                                                sendIntent,
                                                "Поделиться контактом"
                                            )
                                        )
                                    } catch (_: Exception) {
                                        Toast.makeText(
                                            context,
                                            "Не удалось поделиться контактом",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )

                            // 2. Изменить
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Изменить",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Изменить", fontSize = 14.sp)
                                    }
                                },
                                onClick = {
                                    topMenuExpanded = false
                                    showEditContactDialog = true
                                }
                            )

                            HorizontalDivider()

                            // 3. Удалить
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Удалить",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            "Удалить",
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                },
                                onClick = {
                                    topMenuExpanded = false
                                    onDismiss()
                                    onRemoveFavorite(contact)
                                }
                            )
                        }
                    }
                }
            }

            if (showEditContactDialog) {
                EditContactDialog(
                    contact = contact,
                    phoneNumbersList = phoneNumbersList,
                    emailsList = emailsList,
                    birthdayInfo = birthdayInfo,
                    importantDatesList = importantDatesList,
                    messengerAccountsList = messengerAccountsList,
                    avatarBitmap = avatarBitmap,
                    context = context,
                    onSave = { newName, newPhones, newEmails, newBirthday, newImportantDates, updatedMessengers, hiddenSet, newBitmap ->
                        val updatedContact = contact.copy(
                            name = newName,
                            number = newPhones.firstOrNull()?.number ?: contact.number
                        )
                        onUpdateContact(updatedContact)
                        phoneNumbersList = newPhones
                        emailsList = newEmails
                        birthdayInfo = newBirthday
                        importantDatesList = newImportantDates
                        messengerAccountsList = updatedMessengers
                        if (newBitmap != null) {
                            avatarBitmap = newBitmap
                        }
                        saveHiddenMessengers(context, getContactCustomKey(contact), hiddenSet)
                        saveCustomMessengerLinks(
                            context,
                            getContactCustomKey(contact),
                            updatedMessengers
                        )
                        saveImportantDates(
                            context,
                            getContactCustomKey(contact),
                            contact.number,
                            newImportantDates
                        )
                        showEditContactDialog = false
                    },
                    onDismiss = { showEditContactDialog = false }
                )
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
    onUpdateMessengerAccounts: (List<MessengerAccount>) -> Unit = {},
    onUpdatePhoneNumbers: (List<ContactPhoneNumber>) -> Unit = {},
    onUpdateEmails: (List<ContactEmail>) -> Unit = {},
    emailsList: List<ContactEmail>,
    birthdayInfo: ContactBirthday?,
    importantDatesList: List<ContactImportantDate> = emptyList(),
    activeSimCount: Int,
    contact: FavoriteContact,
    context: Context,
    onCall: (String, Int?) -> Unit,
    onSms: (String) -> Unit,
    onDismiss: () -> Unit,
    onRemoveFavorite: (FavoriteContact) -> Unit
) {
    var editablePhoneList by remember(phoneNumbersList) { mutableStateOf(phoneNumbersList) }
    var draggingPhoneIndex by remember { mutableStateOf<Int?>(null) }
    var phoneDragOffsetY by remember { mutableFloatStateOf(0f) }

    var editableMessengerList by remember(messengerAccountsList) {
        mutableStateOf(
            messengerAccountsList
        )
    }
    var draggingMessengerIndex by remember { mutableStateOf<Int?>(null) }
    var messengerDragOffsetY by remember { mutableFloatStateOf(0f) }

    var editableEmailList by remember(emailsList) { mutableStateOf(emailsList) }
    var draggingEmailIndex by remember { mutableStateOf<Int?>(null) }
    var emailDragOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(messengerAccountsList) {
        editableMessengerList = messengerAccountsList
    }
    LaunchedEffect(phoneNumbersList) {
        editablePhoneList = phoneNumbersList
    }
    LaunchedEffect(emailsList) {
        editableEmailList = emailsList
    }

    var showAddCustomLinkDialog by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    val primaryNumber = editablePhoneList.firstOrNull()?.number ?: contact.number

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
                editablePhoneList.forEachIndexed { index, phoneItem ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    val isPhoneDragging = draggingPhoneIndex == index

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                if (isPhoneDragging) {
                                    translationY = phoneDragOffsetY
                                    shadowElevation = 12f
                                    scaleX = 1.02f
                                    scaleY = 1.02f
                                }
                            }
                            .zIndex(if (isPhoneDragging) 10f else 1f)
                            .pointerInput(index, editablePhoneList.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        draggingPhoneIndex = index
                                        phoneDragOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        phoneDragOffsetY += dragAmount.y
                                        val currentList = editablePhoneList.toMutableList()
                                        val currentIndex = draggingPhoneIndex ?: index
                                        val rowHeightPx = with(density) { 62.dp.toPx() }
                                        val shift = (phoneDragOffsetY / rowHeightPx).roundToInt()
                                        val targetIndex =
                                            (currentIndex + shift).coerceIn(0, currentList.size - 1)

                                        if (targetIndex != currentIndex) {
                                            val item = currentList.removeAt(currentIndex)
                                            currentList.add(targetIndex, item)
                                            editablePhoneList = currentList
                                            onUpdatePhoneNumbers(currentList)
                                            savePhoneNumbersOrder(
                                                context,
                                                getContactCustomKey(contact),
                                                currentList
                                            )
                                            phoneDragOffsetY -= (targetIndex - currentIndex) * rowHeightPx
                                            draggingPhoneIndex = targetIndex
                                        }
                                    },
                                    onDragEnd = {
                                        draggingPhoneIndex = null
                                        phoneDragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggingPhoneIndex = null
                                        phoneDragOffsetY = 0f
                                    }
                                )
                            }
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    copyToClipboard(context, "Phone Number", phoneItem.number)
                                }
                        ) {
                            Text(
                                text = phoneItem.label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatPhoneNumber(phoneItem.number),
                                fontSize = 15.sp,
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
        val hiddenSet = remember(messengerAccountsList) {
            getHiddenMessengers(
                context,
                getContactCustomKey(contact)
            )
        }
        val visibleMessengerList = editableMessengerList.filter { messenger ->
            val key = if (messenger.isCustomLink) messenger.id else messenger.packageName
            !hiddenSet.contains(key)
        }

        if (visibleMessengerList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column {
                    visibleMessengerList.forEachIndexed { index, messenger ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        val isMessengerDragging = draggingMessengerIndex == index

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    if (isMessengerDragging) {
                                        translationY = messengerDragOffsetY
                                        shadowElevation = 12f
                                        scaleX = 1.02f
                                        scaleY = 1.02f
                                    }
                                }
                                .zIndex(if (isMessengerDragging) 10f else 1f)
                                .pointerInput(index, editableMessengerList.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            draggingMessengerIndex = index
                                            messengerDragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            messengerDragOffsetY += dragAmount.y
                                            val currentList = editableMessengerList.toMutableList()
                                            val currentIndex = draggingMessengerIndex ?: index
                                            val rowHeightPx = with(density) { 62.dp.toPx() }
                                            val shift =
                                                (messengerDragOffsetY / rowHeightPx).roundToInt()
                                            val targetIndex = (currentIndex + shift).coerceIn(
                                                0,
                                                currentList.size - 1
                                            )

                                            if (targetIndex != currentIndex) {
                                                val item = currentList.removeAt(currentIndex)
                                                currentList.add(targetIndex, item)
                                                editableMessengerList = currentList
                                                onUpdateMessengerAccounts(currentList)
                                                saveMessengerAccountsOrder(
                                                    context,
                                                    getContactCustomKey(contact),
                                                    currentList
                                                )
                                                messengerDragOffsetY -= (targetIndex - currentIndex) * rowHeightPx
                                                draggingMessengerIndex = targetIndex
                                            }
                                        },
                                        onDragEnd = {
                                            draggingMessengerIndex = null
                                            messengerDragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggingMessengerIndex = null
                                            messengerDragOffsetY = 0f
                                        }
                                    )
                                }
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        copyToClipboard(
                                            context,
                                            "Messenger Account",
                                            messenger.accountDetail
                                        )
                                    }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MessengerBrandBadge(
                                        item = InstalledMessengerItem(
                                            packageName = messenger.packageName,
                                            messengerName = messenger.messengerName,
                                            brandColor = messenger.brandColor
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = messenger.messengerName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = messenger.brandColor
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (messenger.isCustomLink) messenger.accountDetail else formatPhoneNumber(
                                        messenger.accountDetail
                                    ),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                // 1. Chat
                                if (messenger.chatIntent != null) {
                                    IconButton(
                                        onClick = {
                                            try {
                                                context.startActivity(messenger.chatIntent)
                                            } catch (_: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    "Не удалось открыть ссылку ${messenger.messengerName}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
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
                                if (!messenger.isCustomLink && messenger.audioCallIntent != null) {
                                    IconButton(
                                        onClick = {
                                            try {
                                                context.startActivity(messenger.audioCallIntent)
                                            } catch (_: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    "Не удалось совершить звонок в ${messenger.messengerName}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
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
                                if (!messenger.isCustomLink && messenger.videoCallIntent != null) {
                                    IconButton(
                                        onClick = {
                                            try {
                                                context.startActivity(messenger.videoCallIntent)
                                            } catch (_: Exception) {
                                                Toast.makeText(
                                                    context,
                                                    "Не удалось начать видеовызов в ${messenger.messengerName}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
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

                    // Bottom "+" Button inside Messenger Card
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = { showAddCustomLinkDialog = true },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SamsungGreen.copy(alpha = 0.12f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Добавить ссылку",
                                tint = SamsungGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // E-MAIL CARD
        if (editableEmailList.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Column {
                    editableEmailList.forEachIndexed { index, emailItem ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        val isEmailDragging = draggingEmailIndex == index

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    if (isEmailDragging) {
                                        translationY = emailDragOffsetY
                                        shadowElevation = 12f
                                        scaleX = 1.02f
                                        scaleY = 1.02f
                                    }
                                }
                                .zIndex(if (isEmailDragging) 10f else 1f)
                                .pointerInput(index, editableEmailList.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            draggingEmailIndex = index
                                            emailDragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            emailDragOffsetY += dragAmount.y
                                            val currentList = editableEmailList.toMutableList()
                                            val currentIndex = draggingEmailIndex ?: index
                                            val rowHeightPx = with(density) { 62.dp.toPx() }
                                            val shift =
                                                (emailDragOffsetY / rowHeightPx).roundToInt()
                                            val targetIndex = (currentIndex + shift).coerceIn(
                                                0,
                                                currentList.size - 1
                                            )

                                            if (targetIndex != currentIndex) {
                                                val item = currentList.removeAt(currentIndex)
                                                currentList.add(targetIndex, item)
                                                editableEmailList = currentList
                                                onUpdateEmails(currentList)
                                                saveEmailOrder(
                                                    context,
                                                    getContactCustomKey(contact),
                                                    currentList
                                                )
                                                emailDragOffsetY -= (targetIndex - currentIndex) * rowHeightPx
                                                draggingEmailIndex = targetIndex
                                            }
                                        },
                                        onDragEnd = {
                                            draggingEmailIndex = null
                                            emailDragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggingEmailIndex = null
                                            emailDragOffsetY = 0f
                                        }
                                    )
                                }
                                .padding(horizontal = 18.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        copyToClipboard(context, "Email", emailItem.email)
                                    }
                            ) {
                                Text(
                                    text = emailItem.label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = emailItem.email,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Yellow Email Action Button
                            IconButton(
                                onClick = {
                                    try {
                                        val intent = Intent(
                                            Intent.ACTION_SENDTO,
                                            "mailto:${emailItem.email}".toUri()
                                        )
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        Toast.makeText(
                                            context,
                                            "Не удалось открыть почту",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier
                                    .size(37.6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFB300).copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Email,
                                    contentDescription = "Написать письмо",
                                    tint = Color(0xFFFFB300),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // BIRTHDAY CARD
        if (birthdayInfo != null) {
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "День рождения",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = birthdayInfo.formattedDate,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (!birthdayInfo.ageText.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = birthdayInfo.ageText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // CUSTOM IMPORTANT DATES CARDS (STYLED LIKE BIRTHDAY CARD)
        importantDatesList.forEach { dateItem ->
            val parsedDate =
                remember(dateItem.dateString) { parseBirthdayString(dateItem.dateString) }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = dateItem.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = parsedDate.formattedDate,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (!parsedDate.ageText.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = parsedDate.ageText,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
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
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
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

    if (showAddCustomLinkDialog) {
        AddCustomMessengerLinkDialog(
            context = context,
            onAddCustomLink = { newAccount ->
                val updatedList = editableMessengerList + newAccount
                editableMessengerList = updatedList
                onUpdateMessengerAccounts(updatedList)
                saveCustomMessengerLinks(context, getContactCustomKey(contact), updatedList)
            },
            onDismiss = { showAddCustomLinkDialog = false }
        )
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
                                        CallType.INCOMING -> Triple(
                                            Icons.AutoMirrored.Filled.CallReceived,
                                            IncomingGreen,
                                            "Входящий"
                                        )

                                        CallType.OUTGOING -> Triple(
                                            Icons.AutoMirrored.Filled.CallMade,
                                            OutgoingBlue,
                                            "Исходящий"
                                        )

                                        CallType.MISSED -> Triple(
                                            Icons.AutoMirrored.Filled.CallMissed,
                                            MissedRed,
                                            "Пропущенный"
                                        )

                                        CallType.REJECTED -> Triple(
                                            Icons.Default.CallEnd,
                                            MissedRed,
                                            "Отклоненный"
                                        )
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
                                                color = MaterialTheme.colorScheme.onSurface.copy(
                                                    alpha = 0.6f
                                                )
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
    phoneNumbersList: List<ContactPhoneNumber>,
    messengerAccountsList: List<MessengerAccount>,
    onUpdateMessengerAccounts: (List<MessengerAccount>) -> Unit = {},
    emailsList: List<ContactEmail>,
    activeSimCount: Int,
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
                                                text = "Создать новую",
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
                            title = {
                                Text(
                                    "Новая вкладка избранного",
                                    fontWeight = FontWeight.Bold
                                )
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
                                            val createdTab = onAddTab(newTabNameInput.trim())
                                            selectedTabId = createdTab.id
                                            onUpdateContact(contact.copy(tabId = createdTab.id))
                                            showCreateTabDialog = false
                                        }
                                    }
                                ) {
                                    Text(
                                        "Создать",
                                        color = SamsungGreen,
                                        fontWeight = FontWeight.Bold
                                    )
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

        // Card 1.5: Настройка свайпов
        var showPickerForRight by remember { mutableStateOf(false) }
        var showPickerForLeft by remember { mutableStateOf(false) }

        val contactKey = remember(contact) { getContactCustomKey(contact) }
        var swipeRightAction by remember(contact) {
            mutableStateOf(
                getCustomSwipeAction(
                    context,
                    contactKey,
                    isRight = true
                )
            )
        }
        var swipeLeftAction by remember(contact) {
            mutableStateOf(
                getCustomSwipeAction(
                    context,
                    contactKey,
                    isRight = false
                )
            )
        }

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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Настройка свайпов",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Row 1: Свайп вправо
                val rightVisuals = remember(swipeRightAction) {
                    getActionVisuals(
                        swipeRightAction,
                        defaultIsRight = true
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Свайп вправо",
                            tint = SamsungGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Свайп вправо",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = swipeRightAction?.label ?: "Вызов по умолчанию",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }

                    IconButton(
                        onClick = { showPickerForRight = true },
                        modifier = Modifier
                            .size(37.6.dp)
                            .clip(CircleShape)
                            .background(rightVisuals.color.copy(alpha = 0.15f))
                    ) {
                        if (rightVisuals.simNumber != null) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = rightVisuals.icon,
                                    contentDescription = "Изменить свайп вправо",
                                    tint = rightVisuals.color,
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
                                    SimCardBadge(simNumber = rightVisuals.simNumber)
                                }
                            }
                        } else {
                            Icon(
                                imageVector = rightVisuals.icon,
                                contentDescription = "Изменить свайп вправо",
                                tint = rightVisuals.color,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                // Row 2: Свайп влево
                val leftVisuals = remember(swipeLeftAction) {
                    getActionVisuals(
                        swipeLeftAction,
                        defaultIsRight = false
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Свайп влево",
                            tint = SamsungSmsBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Свайп влево",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = swipeLeftAction?.label ?: "SMS по умолчанию",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }

                    IconButton(
                        onClick = { showPickerForLeft = true },
                        modifier = Modifier
                            .size(37.6.dp)
                            .clip(CircleShape)
                            .background(leftVisuals.color.copy(alpha = 0.15f))
                    ) {
                        if (leftVisuals.simNumber != null) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    imageVector = leftVisuals.icon,
                                    contentDescription = "Изменить свайп влево",
                                    tint = leftVisuals.color,
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
                                    SimCardBadge(simNumber = leftVisuals.simNumber)
                                }
                            }
                        } else {
                            Icon(
                                imageVector = leftVisuals.icon,
                                contentDescription = "Изменить свайп влево",
                                tint = leftVisuals.color,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showPickerForRight) {
            SwipeActionPickerDialog(
                contact = contact,
                phoneNumbersList = phoneNumbersList,
                messengerAccountsList = messengerAccountsList,
                onUpdateMessengerAccounts = onUpdateMessengerAccounts,
                emailsList = emailsList,
                activeSimCount = activeSimCount,
                context = context,
                onActionSelected = { action ->
                    swipeRightAction = action
                    saveCustomSwipeAction(
                        context,
                        contactKey,
                        contact.number,
                        isRight = true,
                        action = action
                    )
                    showPickerForRight = false
                },
                onDismiss = { showPickerForRight = false }
            )
        }

        if (showPickerForLeft) {
            SwipeActionPickerDialog(
                contact = contact,
                phoneNumbersList = phoneNumbersList,
                messengerAccountsList = messengerAccountsList,
                onUpdateMessengerAccounts = onUpdateMessengerAccounts,
                emailsList = emailsList,
                activeSimCount = activeSimCount,
                context = context,
                onActionSelected = { action ->
                    swipeLeftAction = action
                    saveCustomSwipeAction(
                        context,
                        contactKey,
                        contact.number,
                        isRight = false,
                        action = action
                    )
                    showPickerForLeft = false
                },
                onDismiss = { showPickerForLeft = false }
            )
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
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText(
                            "Contact Info",
                            "${contact.name}: ${contact.number}"
                        )
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
                val type =
                    if (typeIdx != -1) c.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_OTHER
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

    val contactKey = getContactCustomKey(contact)
    val savedOrder = getSavedPhoneNumbersOrder(context, contactKey)
    if (savedOrder.isNotEmpty()) {
        val cleanSaved = savedOrder.map { it.replace(Regex("[^0-9+]"), "") }
        numbersList.sortBy { item ->
            val clean = item.number.replace(Regex("[^0-9+]"), "")
            val idx = cleanSaved.indexOf(clean)
            if (idx != -1) idx else 999
        }
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
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(contactNumber)
        )
        val intent = Intent(Intent.ACTION_VIEW, uri)
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_APP_CONTACTS)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Не удалось открыть информацию о контакте", Toast.LENGTH_SHORT)
                .show()
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
    } catch (_: Exception) {
        null
    }
}

private fun formatCallDuration(seconds: Long): String {
    if (seconds <= 0L) return "Без ответа"
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "$m мин $s сек" else "$s сек"
}

private fun copyToClipboard(context: Context, label: String, textToCopy: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, textToCopy)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Скопировано!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private data class MessengerAccount(
    val id: String,
    val packageName: String,
    val messengerName: String,
    val accountDetail: String,
    val brandColor: Color,
    val chatIntent: Intent?,
    val audioCallIntent: Intent? = null,
    val videoCallIntent: Intent? = null,
    val isCustomLink: Boolean = false
)

private fun isPackageInstalled(context: Context, packageName: String): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: Exception) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            launchIntent != null
        } catch (_: Exception) {
            false
        }
    }
}

private fun loadMessengerAccounts(
    context: Context,
    contact: FavoriteContact
): List<MessengerAccount> {
    val repository = ContactsRepository(context)
    val list = repository.getMessengerActions(contact)
        .map { action ->
            MessengerAccount(
                id = action.id,
                packageName = action.packageName,
                messengerName = action.messengerName,
                accountDetail = action.accountDetail,
                brandColor = action.brandColor,
                chatIntent = action.chatIntent,
                audioCallIntent = action.audioCallIntent,
                videoCallIntent = action.videoCallIntent
            )
        }
        .toMutableList()

    val contactKey = getContactCustomKey(contact)
    val customSavedLinks = getSavedCustomMessengerLinks(context, contactKey)
    if (customSavedLinks.isNotEmpty()) {
        list.addAll(customSavedLinks)
    }

    val savedOrder = getSavedMessengerAccountsOrder(context, contactKey)
    if (savedOrder.isNotEmpty()) {
        list.sortBy { messenger ->
            val idx = savedOrder.indexOf(messenger.id)
            if (idx != -1) idx else 999
        }
    }

    return list
}

data class ContactImportantDate(
    val id: String = "date_${System.currentTimeMillis()}",
    val label: String,
    val dateString: String
)

private fun saveImportantDates(
    context: Context,
    contactKey: String,
    contactNumber: String,
    dates: List<ContactImportantDate>
) {
    try {
        val prefs = context.getSharedPreferences("contact_custom_orders", Context.MODE_PRIVATE)
        val cleanNum = contactNumber.replace(Regex("[^0-9+]"), "")

        val array = JSONArray()
        dates.filter { it.label.isNotBlank() || it.dateString.isNotBlank() }.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("label", item.label)
                put("dateString", item.dateString)
            }
            array.put(obj)
        }

        prefs.edit {
            putString("important_dates_$contactKey", array.toString())
            if (cleanNum.isNotBlank()) {
                putString("important_dates_$cleanNum", array.toString())
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun getImportantDates(
    context: Context,
    contactKey: String,
    fallbackNumber: String? = null
): List<ContactImportantDate> {
    try {
        val prefs = context.getSharedPreferences("contact_custom_orders", Context.MODE_PRIVATE)

        // 1. Try with contactKey
        var jsonString = prefs.getString("important_dates_$contactKey", null)

        // 2. Try with clean fallbackNumber
        if (jsonString.isNullOrEmpty() && !fallbackNumber.isNullOrBlank()) {
            val cleanNum = fallbackNumber.replace(Regex("[^0-9+]"), "")
            if (cleanNum.isNotBlank()) {
                jsonString = prefs.getString("important_dates_$cleanNum", null)
            }
        }

        // 3. Fallback: match by last 7 digits across all saved keys
        if (jsonString.isNullOrEmpty()) {
            val searchNum =
                (if (!fallbackNumber.isNullOrBlank()) fallbackNumber else contactKey).replace(
                    Regex("[^0-9]"), ""
                )
            if (searchNum.length >= 7) {
                val last7 = searchNum.takeLast(7)
                val allKeys = prefs.all.keys.filter { it.startsWith("important_dates_") }
                for (k in allKeys) {
                    val cleanKeyDigits = k.replace(Regex("[^0-9]"), "")
                    if (cleanKeyDigits.length >= 7 && cleanKeyDigits.takeLast(7) == last7) {
                        jsonString = prefs.getString(k, null)
                        if (!jsonString.isNullOrEmpty()) break
                    }
                }
            }
        }

        if (jsonString.isNullOrEmpty()) return emptyList()

        val array = JSONArray(jsonString)
        val list = mutableListOf<ContactImportantDate>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                ContactImportantDate(
                    id = if (obj.has("id")) obj.getString("id") else "date_$i",
                    label = obj.getString("label"),
                    dateString = obj.getString("dateString")
                )
            )
        }
        return list
    } catch (_: Exception) {
        return emptyList()
    }
}

private fun saveHiddenMessengers(context: Context, contactKey: String, hiddenIds: Set<String>) {
    try {
        val prefs = context.getSharedPreferences("contact_custom_orders", Context.MODE_PRIVATE)
        val array = JSONArray()
        hiddenIds.forEach { array.put(it) }
        prefs.edit { putString("hidden_messengers_$contactKey", array.toString()) }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun getHiddenMessengers(context: Context, contactKey: String): Set<String> {
    try {
        val prefs = context.getSharedPreferences("contact_custom_orders", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("hidden_messengers_$contactKey", null) ?: return emptySet()
        val array = JSONArray(jsonString)
        val set = mutableSetOf<String>()
        for (i in 0 until array.length()) {
            set.add(array.getString(i))
        }
        return set
    } catch (_: Exception) {
        return emptySet()
    }
}

private fun saveCustomMessengerLinks(
    context: Context,
    contactKey: String,
    links: List<MessengerAccount>
) {
    try {
        val prefs = context.getSharedPreferences("contact_custom_orders", Context.MODE_PRIVATE)
        val array = JSONArray()
        links.filter { it.isCustomLink }.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("packageName", item.packageName)
                put("messengerName", item.messengerName)
                put("accountDetail", item.accountDetail)
            }
            array.put(obj)
        }
        prefs.edit { putString("custom_msg_links_$contactKey", array.toString()) }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun getSavedCustomMessengerLinks(
    context: Context,
    contactKey: String
): List<MessengerAccount> {
    try {
        val prefs = context.getSharedPreferences("contact_custom_orders", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("custom_msg_links_$contactKey", null) ?: return emptyList()
        val array = JSONArray(jsonString)
        val list = mutableListOf<MessengerAccount>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val pkg = obj.getString("packageName")
            val name = obj.getString("messengerName")
            val detail = obj.getString("accountDetail")
            val color = getMessengerBrandColor(name)

            list.add(
                MessengerAccount(
                    id = obj.getString("id"),
                    packageName = pkg,
                    messengerName = name,
                    accountDetail = detail,
                    brandColor = color,
                    chatIntent = Intent(Intent.ACTION_VIEW, Uri.parse(detail)),
                    isCustomLink = true
                )
            )
        }
        return list
    } catch (_: Exception) {
        return emptyList()
    }
}

private fun getMessengerBrandColor(messengerName: String): Color {
    return when {
        messengerName.contains("WhatsApp", true) -> Color(0xFF25D366)
        messengerName.contains("Telegram", true) -> Color(0xFF24A1DE)
        messengerName.contains("Viber", true) -> Color(0xFF7360F2)
        messengerName.contains("Signal", true) -> Color(0xFF3A76F0)
        messengerName.contains("Skype", true) -> Color(0xFF00AFF0)
        messengerName.contains("MAX", true) -> Color(0xFF2A5885)
        messengerName.contains("VK", true) -> Color(0xFF0077FF)
        messengerName.contains("Messenger", true) -> Color(0xFF0084FF)
        messengerName.contains("Snapchat", true) -> Color(0xFFE5C100)
        messengerName.contains("WeChat", true) -> Color(0xFF07C160)
        else -> SamsungGreen
    }
}

private fun getApplicationIconBitmap(context: Context, packageName: String): ImageBitmap? {
    return try {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        val bitmap = if (drawable is android.graphics.drawable.BitmapDrawable) {
            drawable.bitmap
        } else {
            val bmp = createBitmap(
                drawable.intrinsicWidth.coerceAtLeast(1),
                drawable.intrinsicHeight.coerceAtLeast(1)
            )
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bmp
        }
        bitmap.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun MessengerBrandBadge(item: InstalledMessengerItem) {
    val context = LocalContext.current
    val appIcon = remember(item.packageName) {
        getApplicationIconBitmap(context, item.packageName)
    }

    if (appIcon != null) {
        Image(
            bitmap = appIcon,
            contentDescription = item.messengerName,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(item.brandColor),
            contentAlignment = Alignment.Center
        ) {
            val badgeText = when {
                item.messengerName.contains("VK", true) -> "VK"
                item.messengerName.contains("WhatsApp", true) -> "WA"
                item.messengerName.contains("Telegram", true) -> "TG"
                item.messengerName.contains("MAX", true) -> "MAX"
                item.messengerName.contains("Viber", true) -> "V"
                item.messengerName.contains("Signal", true) -> "S"
                item.messengerName.contains("Skype", true) -> "Sk"
                item.messengerName.contains("Snapchat", true) -> "Sn"
                else -> item.messengerName.take(1).uppercase()
            }
            Text(
                text = badgeText,
                color = Color.White,
                fontSize = if (badgeText.length > 2) 8.sp else 10.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

private data class InstalledMessengerItem(
    val packageName: String,
    val messengerName: String,
    val brandColor: Color
)

private fun getInstalledMessengersList(context: Context): List<InstalledMessengerItem> {
    val candidates = listOf(
        InstalledMessengerItem("org.telegram.messenger", "Telegram", Color(0xFF24A1DE)),
        InstalledMessengerItem("com.whatsapp", "WhatsApp", Color(0xFF25D366)),
        InstalledMessengerItem("ru.oneme.app", "MAX", Color(0xFF2A5885)),
        InstalledMessengerItem("com.viber.voip", "Viber", Color(0xFF7360F2)),
        InstalledMessengerItem("com.vk.im", "VK Messenger", Color(0xFF0077FF)),
        InstalledMessengerItem("org.thoughtcrime.securesms", "Signal", Color(0xFF3A76F0)),
        InstalledMessengerItem("com.skype.raider", "Skype", Color(0xFF00AFF0)),
        InstalledMessengerItem("com.facebook.orca", "Messenger", Color(0xFF0084FF)),
        InstalledMessengerItem("com.snapchat.android", "Snapchat", Color(0xFFE5C100)),
        InstalledMessengerItem("com.tencent.mm", "WeChat", Color(0xFF07C160))
    )

    val installed = candidates.filter { item ->
        when (item.messengerName) {
            "Telegram" -> isPackageInstalled(context, "org.telegram.messenger")
            "WhatsApp" -> isPackageInstalled(context, "com.whatsapp") || isPackageInstalled(
                context,
                "com.whatsapp.w4b"
            )

            "MAX" -> isPackageInstalled(context, "ru.oneme.app") || isPackageInstalled(
                context,
                "ru.max.messenger"
            ) || isPackageInstalled(context, "com.max.app") || isPackageInstalled(
                context,
                "ru.vk.max"
            )

            "Viber" -> isPackageInstalled(context, "com.viber.voip")
            "VK Messenger" -> isPackageInstalled(
                context,
                "com.vk.im"
            ) || isPackageInstalled(context, "com.vkontakte.android")

            "Signal" -> isPackageInstalled(context, "org.thoughtcrime.securesms")
            "Skype" -> isPackageInstalled(
                context,
                "com.skype.raider"
            ) || isPackageInstalled(context, "com.skype.android")

            "Messenger" -> isPackageInstalled(context, "com.facebook.orca")
            "Snapchat" -> isPackageInstalled(context, "com.snapchat.android")
            "WeChat" -> isPackageInstalled(context, "com.tencent.mm")
            else -> isPackageInstalled(context, item.packageName)
        }
    }

    return installed.ifEmpty {
        listOf(
            InstalledMessengerItem("org.telegram.messenger", "Telegram", Color(0xFF24A1DE)),
            InstalledMessengerItem("com.whatsapp", "WhatsApp", Color(0xFF25D366)),
            InstalledMessengerItem("ru.oneme.app", "MAX", Color(0xFF2A5885))
        )
    }
}

private fun getContactCustomKey(contact: FavoriteContact): String {
    return contact.id.ifBlank { contact.number.replace(Regex("[^0-9+]"), "") }
}

private fun savePhoneNumbersOrder(
    context: Context,
    contactKey: String,
    numbers: List<ContactPhoneNumber>
) {
    try {
        val prefs = context.getSharedPreferences("contact_custom_orders", Context.MODE_PRIVATE)
        val array = JSONArray()
        numbers.forEach { array.put(it.number) }
        prefs.edit { putString("phones_order_$contactKey", array.toString()) }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun getSavedPhoneNumbersOrder(context: Context, contactKey: String): List<String> {
    try {
        val prefs = context.getSharedPreferences("contact_custom_orders", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("phones_order_$contactKey", null) ?: return emptyList()
        val array = JSONArray(jsonString)
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            list.add(array.getString(i))
        }
        return list
    } catch (_: Exception) {
        return emptyList()
    }
}

private fun saveMessengerAccountsOrder(
    context: Context,
    contactKey: String,
    messengers: List<MessengerAccount>
) {
    try {
        val prefs = context.getSharedPreferences("contact_custom_orders", Context.MODE_PRIVATE)
        val array = JSONArray()
        messengers.forEach { array.put(it.id) }
        prefs.edit { putString("messengers_order_$contactKey", array.toString()) }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun getSavedMessengerAccountsOrder(context: Context, contactKey: String): List<String> {
    try {
        val prefs = context.getSharedPreferences("contact_custom_orders", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("messengers_order_$contactKey", null) ?: return emptyList()
        val array = JSONArray(jsonString)
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            list.add(array.getString(i))
        }
        return list
    } catch (_: Exception) {
        return emptyList()
    }
}

private data class ContactEmail(
    val email: String,
    val label: String
)

private data class ContactBirthday(
    val dateString: String,
    val formattedDate: String,
    val ageText: String?
)

private fun getEmailTypeLabel(type: Int, customLabel: String?): String {
    return when (type) {
        ContactsContract.CommonDataKinds.Email.TYPE_HOME -> "Личный"
        ContactsContract.CommonDataKinds.Email.TYPE_WORK -> "Рабочий"
        ContactsContract.CommonDataKinds.Email.TYPE_MOBILE -> "Мобильный"
        ContactsContract.CommonDataKinds.Email.TYPE_OTHER -> "Другой"
        ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM -> customLabel ?: "Другой"
        else -> "Личный"
    }
}

private fun saveEmailOrder(context: Context, contactKey: String, emails: List<ContactEmail>) {
    try {
        val prefs = context.getSharedPreferences("contact_custom_orders", Context.MODE_PRIVATE)
        val array = JSONArray()
        emails.forEach { array.put(it.email) }
        prefs.edit { putString("emails_order_$contactKey", array.toString()) }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun getSavedEmailOrder(context: Context, contactKey: String): List<String> {
    try {
        val prefs = context.getSharedPreferences("contact_custom_orders", Context.MODE_PRIVATE)
        val jsonString = prefs.getString("emails_order_$contactKey", null) ?: return emptyList()
        val array = JSONArray(jsonString)
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            list.add(array.getString(i))
        }
        return list
    } catch (_: Exception) {
        return emptyList()
    }
}

private suspend fun loadContactEmails(
    context: Context,
    contact: FavoriteContact
): List<ContactEmail> = withContext(Dispatchers.IO) {
    val list = mutableListOf<ContactEmail>()
    val addedAddresses = mutableSetOf<String>()

    try {
        var contactId: String? = null
        if (contact.number.isNotBlank()) {
            try {
                val lookupUri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(contact.number)
                )
                val lookupCursor = context.contentResolver.query(
                    lookupUri,
                    arrayOf(ContactsContract.PhoneLookup._ID),
                    null, null, null
                )
                lookupCursor?.use { c ->
                    if (c.moveToFirst()) {
                        val idIdx = c.getColumnIndex(ContactsContract.PhoneLookup._ID)
                        if (idIdx != -1) contactId = c.getString(idIdx)
                    }
                }
            } catch (_: Exception) {
                // ignore
            }
        }

        val selection: String
        val selectionArgs: Array<String>
        if (!contactId.isNullOrBlank()) {
            selection =
                "${ContactsContract.CommonDataKinds.Email.CONTACT_ID} = ? OR ${ContactsContract.CommonDataKinds.Email.DISPLAY_NAME} = ?"
            selectionArgs = arrayOf(contactId!!, contact.name)
        } else {
            selection = "${ContactsContract.CommonDataKinds.Email.DISPLAY_NAME} = ?"
            selectionArgs = arrayOf(contact.name)
        }

        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Email.ADDRESS,
                ContactsContract.CommonDataKinds.Email.TYPE,
                ContactsContract.CommonDataKinds.Email.LABEL
            ),
            selection,
            selectionArgs,
            null
        )
        cursor?.use { c ->
            val addrIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            val typeIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE)
            val labelIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Email.LABEL)
            while (c.moveToNext()) {
                val email = if (addrIdx != -1) c.getString(addrIdx) else ""
                val type =
                    if (typeIdx != -1) c.getInt(typeIdx) else ContactsContract.CommonDataKinds.Email.TYPE_OTHER
                val customLabel = if (labelIdx != -1) c.getString(labelIdx) else null

                val cleanEmail = email.trim().lowercase()
                if (cleanEmail.isNotBlank() && !addedAddresses.contains(cleanEmail)) {
                    addedAddresses.add(cleanEmail)
                    val labelStr = getEmailTypeLabel(type, customLabel)
                    list.add(ContactEmail(email.trim(), labelStr))
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    val contactKey = getContactCustomKey(contact)
    val savedOrder = getSavedEmailOrder(context, contactKey)
    if (savedOrder.isNotEmpty()) {
        val cleanSaved = savedOrder.map { it.trim().lowercase() }
        list.sortBy { item ->
            val idx = cleanSaved.indexOf(item.email.trim().lowercase())
            if (idx != -1) idx else 999
        }
    }

    list
}

private fun formatAgeRu(age: Int): String {
    val rem100 = age % 100
    val rem10 = age % 10
    if (rem100 in 11..14) return "$age лет"
    return when (rem10) {
        1 -> "$age год"
        in 2..4 -> "$age года"
        else -> "$age лет"
    }
}

private fun parseBirthdayString(rawDate: String): ContactBirthday {
    val cleanDate = rawDate.trim()
    if (cleanDate.isBlank()) return ContactBirthday("", "", null)

    try {
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val currentMonth = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1
        val currentDay = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH)

        val monthNames = arrayOf(
            "января",
            "февраля",
            "марта",
            "апреля",
            "мая",
            "июня",
            "июля",
            "августа",
            "сентября",
            "октября",
            "ноября",
            "декабря"
        )

        if (cleanDate.startsWith("--") || cleanDate.length == 5) {
            val mm = cleanDate.takeLast(5).substring(0, 2).toIntOrNull() ?: 1
            val dd = cleanDate.takeLast(2).toIntOrNull() ?: 1
            val mName = if (mm in 1..12) monthNames[mm - 1] else ""
            return ContactBirthday(cleanDate, "$dd $mName", null)
        }

        // Search for 4-digit year e.g. 1990 or 2005
        val yearMatch = Regex("\\b(19\\d{2}|20\\d{2})\\b").find(cleanDate)
        var birthYear: Int? = yearMatch?.value?.toIntOrNull()

        var birthDay: Int? = null
        var birthMonth: Int? = null

        val lowerDate = cleanDate.lowercase()
        for (i in monthNames.indices) {
            if (lowerDate.contains(monthNames[i])) {
                birthMonth = i + 1
                break
            }
        }

        val numbers =
            Regex("\\d+").findAll(cleanDate).mapNotNull { it.value.toIntOrNull() }.toList()

        if (birthYear != null) {
            val nonYearNumbers = numbers.filter { it != birthYear }
            if (birthMonth == null && nonYearNumbers.size >= 2) {
                if (nonYearNumbers[0] in 1..31 && nonYearNumbers[1] in 1..12) {
                    birthDay = nonYearNumbers[0]
                    birthMonth = nonYearNumbers[1]
                } else if (nonYearNumbers[0] in 1..12 && nonYearNumbers[1] in 1..31) {
                    birthMonth = nonYearNumbers[0]
                    birthDay = nonYearNumbers[1]
                }
            } else if (nonYearNumbers.isNotEmpty()) {
                birthDay = nonYearNumbers.firstOrNull { it in 1..31 }
            }
        } else if (numbers.size >= 3) {
            val digits = cleanDate.replace(Regex("[^0-9]"), "")
            if (digits.length >= 8) {
                birthYear = digits.substring(0, 4).toIntOrNull()
                birthMonth = digits.substring(4, 6).toIntOrNull()
                birthDay = digits.substring(6, 8).toIntOrNull()
            }
        }

        if (birthYear != null && birthYear in 1900..currentYear) {
            var age = currentYear - birthYear
            if (birthMonth != null && birthDay != null) {
                if (currentMonth < birthMonth || (currentMonth == birthMonth && currentDay < birthDay)) {
                    age--
                }
            }
            val ageText = if (age in 0..120) formatAgeRu(age) else null

            val formatted = if (birthDay != null && birthMonth != null && birthMonth in 1..12) {
                "$birthDay ${monthNames[birthMonth - 1]} $birthYear г."
            } else cleanDate

            return ContactBirthday(cleanDate, formatted, ageText)
        }

        return ContactBirthday(cleanDate, cleanDate, null)
    } catch (_: Exception) {
        return ContactBirthday(cleanDate, cleanDate, null)
    }
}

private suspend fun loadContactBirthday(
    context: Context,
    contact: FavoriteContact
): ContactBirthday? = withContext(Dispatchers.IO) {
    try {
        var contactId: String? = null
        if (contact.number.isNotBlank()) {
            try {
                val lookupUri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(contact.number)
                )
                val lookupCursor = context.contentResolver.query(
                    lookupUri,
                    arrayOf(ContactsContract.PhoneLookup._ID),
                    null, null, null
                )
                lookupCursor?.use { c ->
                    if (c.moveToFirst()) {
                        val idIdx = c.getColumnIndex(ContactsContract.PhoneLookup._ID)
                        if (idIdx != -1) contactId = c.getString(idIdx)
                    }
                }
            } catch (_: Exception) {
                // ignore
            }
        }

        val selection: String
        val selectionArgs: Array<String>
        if (!contactId.isNullOrBlank()) {
            selection =
                "(${ContactsContract.Data.CONTACT_ID} = ? OR ${ContactsContract.Data.DISPLAY_NAME} = ?) AND ${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?"
            selectionArgs = arrayOf(
                contactId!!,
                contact.name,
                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()
            )
        } else {
            selection =
                "${ContactsContract.Data.DISPLAY_NAME} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?"
            selectionArgs = arrayOf(
                contact.name,
                ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString()
            )
        }

        val cursor = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Event.START_DATE
            ),
            selection,
            selectionArgs,
            null
        )
        cursor?.use { c ->
            val dateIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Event.START_DATE)
            if (c.moveToFirst()) {
                val rawDate = if (dateIdx != -1) c.getString(dateIdx) else null
                if (!rawDate.isNullOrBlank()) {
                    return@withContext parseBirthdayString(rawDate)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    null
}

data class CustomSwipeAction(
    val actionType: String,
    val targetValue: String,
    val label: String,
    val messengerName: String? = null,
    val messengerColorHex: String? = null
)

private fun saveCustomSwipeAction(
    context: Context,
    contactKey: String,
    contactNumber: String,
    isRight: Boolean,
    action: CustomSwipeAction?
) {
    try {
        val prefs = context.getSharedPreferences("contact_custom_orders", Context.MODE_PRIVATE)
        val keySuffix = if (isRight) "swipe_right_" else "swipe_left_"
        val cleanNum = contactNumber.replace(Regex("[^0-9+]"), "")

        if (action == null) {
            prefs.edit { remove(keySuffix + contactKey) }
            if (cleanNum.isNotBlank()) prefs.edit { remove(keySuffix + cleanNum) }
            return
        }

        val obj = JSONObject().apply {
            put("actionType", action.actionType)
            put("targetValue", action.targetValue)
            put("label", action.label)
            put("messengerName", action.messengerName ?: JSONObject.NULL)
            put("messengerColorHex", action.messengerColorHex ?: JSONObject.NULL)
        }

        prefs.edit {
            putString(keySuffix + contactKey, obj.toString())
            if (cleanNum.isNotBlank()) {
                putString(keySuffix + cleanNum, obj.toString())
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun getCustomSwipeAction(
    context: Context,
    contactKey: String,
    isRight: Boolean,
    fallbackNumber: String? = null
): CustomSwipeAction? {
    try {
        val prefs = context.getSharedPreferences("contact_custom_orders", Context.MODE_PRIVATE)
        val keySuffix = if (isRight) "swipe_right_" else "swipe_left_"

        // 1. Try with contactKey
        var jsonString = prefs.getString(keySuffix + contactKey, null)

        // 2. Try with clean fallbackNumber
        if (jsonString.isNullOrEmpty() && !fallbackNumber.isNullOrBlank()) {
            val cleanNum = fallbackNumber.replace(Regex("[^0-9+]"), "")
            if (cleanNum.isNotBlank()) {
                jsonString = prefs.getString(keySuffix + cleanNum, null)
            }
        }

        // 3. Fallback: match by last 7 digits of phone number across all saved keys
        if (jsonString.isNullOrEmpty()) {
            val searchNum =
                (if (!fallbackNumber.isNullOrBlank()) fallbackNumber else contactKey).replace(
                    Regex("[^0-9]"), ""
                )
            if (searchNum.length >= 7) {
                val last7 = searchNum.takeLast(7)
                val allKeys = prefs.all.keys.filter { it.startsWith(keySuffix) }
                for (k in allKeys) {
                    val cleanKeyDigits = k.replace(Regex("[^0-9]"), "")
                    if (cleanKeyDigits.length >= 7 && cleanKeyDigits.takeLast(7) == last7) {
                        jsonString = prefs.getString(k, null)
                        if (!jsonString.isNullOrEmpty()) break
                    }
                }
            }
        }

        if (jsonString.isNullOrEmpty()) return null

        val obj = JSONObject(jsonString)
        return CustomSwipeAction(
            actionType = obj.getString("actionType"),
            targetValue = obj.getString("targetValue"),
            label = obj.getString("label"),
            messengerName = if (obj.has("messengerName") && !obj.isNull("messengerName")) obj.getString(
                "messengerName"
            ) else null,
            messengerColorHex = if (obj.has("messengerColorHex") && !obj.isNull("messengerColorHex")) obj.getString(
                "messengerColorHex"
            ) else null
        )
    } catch (_: Exception) {
        return null
    }
}

fun executeCustomSwipeAction(
    context: Context,
    action: CustomSwipeAction,
    onCall: (String, Int?) -> Unit,
    onSms: (String) -> Unit
) {
    try {
        when (action.actionType) {
            "call_sim1" -> onCall(action.targetValue, 1)
            "call_sim2" -> onCall(action.targetValue, 2)
            "call_single" -> onCall(action.targetValue, null)
            "sms" -> onSms(action.targetValue)
            "email" -> {
                val intent = Intent(Intent.ACTION_SENDTO, "mailto:${action.targetValue}".toUri())
                context.startActivity(intent)
            }

            "messenger_chat", "messenger_audio", "messenger_video" -> {
                val intent = Intent(Intent.ACTION_VIEW, action.targetValue.toUri())
                context.startActivity(intent)
            }

            else -> onCall(action.targetValue, null)
        }
    } catch (_: Exception) {
        Toast.makeText(context, "Не удалось выполнить действие", Toast.LENGTH_SHORT).show()
    }
}

data class SwipeBackgroundVisuals(
    val icon: ImageVector,
    val backgroundColor: Color,
    val label: String
)

fun getSwipeBackgroundVisuals(
    customAction: CustomSwipeAction?,
    defaultIsRight: Boolean
): SwipeBackgroundVisuals {
    if (customAction == null) {
        return if (defaultIsRight) {
            SwipeBackgroundVisuals(
                icon = Icons.Default.Phone,
                backgroundColor = SamsungGreen,
                label = "Вызов"
            )
        } else {
            SwipeBackgroundVisuals(
                icon = Icons.AutoMirrored.Filled.Message,
                backgroundColor = SamsungSmsBlue,
                label = "SMS"
            )
        }
    }

    return when (customAction.actionType) {
        "call_sim1", "call_sim2", "call_single" -> {
            val messenger = customAction.messengerName
            SwipeBackgroundVisuals(
                icon = Icons.Default.Phone,
                backgroundColor = SamsungGreen,
                label = if (!messenger.isNullOrBlank()) messenger else "Вызов"
            )
        }

        "sms" -> {
            val messenger = customAction.messengerName
            SwipeBackgroundVisuals(
                icon = Icons.AutoMirrored.Filled.Message,
                backgroundColor = SamsungSmsBlue,
                label = if (!messenger.isNullOrBlank()) messenger else "SMS"
            )
        }

        "messenger_chat" -> {
            val messenger = customAction.messengerName ?: "Сообщение"
            SwipeBackgroundVisuals(
                icon = Icons.AutoMirrored.Filled.Message,
                backgroundColor = SamsungSmsBlue,
                label = messenger
            )
        }

        "messenger_audio" -> {
            val messenger = customAction.messengerName ?: "Вызов"
            SwipeBackgroundVisuals(
                icon = Icons.Default.Phone,
                backgroundColor = SamsungGreen,
                label = messenger
            )
        }

        "messenger_video" -> {
            val messenger = customAction.messengerName ?: "Видеовызов"
            SwipeBackgroundVisuals(
                icon = Icons.Default.Videocam,
                backgroundColor = Color(0xFF7360F2),
                label = messenger
            )
        }

        "email" -> {
            SwipeBackgroundVisuals(
                icon = Icons.Default.Email,
                backgroundColor = Color(0xFFFFB300),
                label = "E-mail"
            )
        }

        else -> {
            if (defaultIsRight) {
                SwipeBackgroundVisuals(Icons.Default.Phone, SamsungGreen, "Вызов")
            } else {
                SwipeBackgroundVisuals(Icons.AutoMirrored.Filled.Message, SamsungSmsBlue, "SMS")
            }
        }
    }
}

private data class ActionVisuals(
    val icon: ImageVector,
    val color: Color,
    val simNumber: Int? = null
)

private fun getActionVisuals(action: CustomSwipeAction?, defaultIsRight: Boolean): ActionVisuals {
    if (action == null) {
        return if (defaultIsRight) {
            ActionVisuals(Icons.Default.Phone, SamsungGreen)
        } else {
            ActionVisuals(Icons.AutoMirrored.Filled.Message, SamsungSmsBlue)
        }
    }

    val brandColor = if (!action.messengerName.isNullOrBlank()) {
        getMessengerBrandColor(action.messengerName)
    } else null

    return when (action.actionType) {
        "call_sim1" -> ActionVisuals(Icons.Default.Phone, SamsungSmsBlue, simNumber = 1)
        "call_sim2" -> ActionVisuals(Icons.Default.Phone, SamsungGreen, simNumber = 2)
        "call_single" -> ActionVisuals(Icons.Default.Phone, brandColor ?: SamsungGreen)
        "sms" -> ActionVisuals(Icons.AutoMirrored.Filled.Message, brandColor ?: SamsungSmsBlue)
        "email" -> ActionVisuals(Icons.Default.Email, Color(0xFFFFB300))
        "messenger_chat" -> ActionVisuals(
            Icons.AutoMirrored.Filled.Message,
            brandColor ?: SamsungGreen
        )

        "messenger_audio" -> ActionVisuals(Icons.Default.Phone, brandColor ?: SamsungGreen)
        "messenger_video" -> ActionVisuals(Icons.Default.Videocam, brandColor ?: SamsungGreen)
        else -> if (defaultIsRight) ActionVisuals(
            Icons.Default.Phone,
            SamsungGreen
        ) else ActionVisuals(Icons.AutoMirrored.Filled.Message, SamsungSmsBlue)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeActionPickerDialog(
    contact: FavoriteContact,
    phoneNumbersList: List<ContactPhoneNumber>,
    messengerAccountsList: List<MessengerAccount>,
    onUpdateMessengerAccounts: (List<MessengerAccount>) -> Unit = {},
    emailsList: List<ContactEmail>,
    activeSimCount: Int,
    context: Context,
    onActionSelected: (CustomSwipeAction) -> Unit,
    onDismiss: () -> Unit
) {
    var pickerMessengerList by remember(messengerAccountsList) {
        mutableStateOf(
            messengerAccountsList
        )
    }
    var showAddCustomLinkDialogInPicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Выберите иконку действия для свайпа",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Section 1: Телефоны
            if (phoneNumbersList.isNotEmpty()) {
                Text(
                    text = "Телефоны",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

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
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
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
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (activeSimCount > 1) {
                                        // SIM 1 Call Button
                                        IconButton(
                                            onClick = {
                                                onActionSelected(
                                                    CustomSwipeAction(
                                                        actionType = "call_sim1",
                                                        targetValue = phoneItem.number,
                                                        label = "Вызов SIM 1 (${
                                                            formatPhoneNumber(
                                                                phoneItem.number
                                                            )
                                                        })"
                                                    )
                                                )
                                            },
                                            modifier = Modifier
                                                .size(37.6.dp)
                                                .clip(CircleShape)
                                                .background(SamsungSmsBlue.copy(alpha = 0.12f))
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize()) {
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
                                                onActionSelected(
                                                    CustomSwipeAction(
                                                        actionType = "call_sim2",
                                                        targetValue = phoneItem.number,
                                                        label = "Вызов SIM 2 (${
                                                            formatPhoneNumber(
                                                                phoneItem.number
                                                            )
                                                        })"
                                                    )
                                                )
                                            },
                                            modifier = Modifier
                                                .size(37.6.dp)
                                                .clip(CircleShape)
                                                .background(SamsungGreen.copy(alpha = 0.12f))
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize()) {
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
                                                onActionSelected(
                                                    CustomSwipeAction(
                                                        actionType = "call_single",
                                                        targetValue = phoneItem.number,
                                                        label = "Вызов (${
                                                            formatPhoneNumber(
                                                                phoneItem.number
                                                            )
                                                        })"
                                                    )
                                                )
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
                                            onActionSelected(
                                                CustomSwipeAction(
                                                    actionType = "sms",
                                                    targetValue = phoneItem.number,
                                                    label = "SMS (${formatPhoneNumber(phoneItem.number)})"
                                                )
                                            )
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

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Section 2: Мессенджеры
            val hiddenSetInPicker = remember(pickerMessengerList) {
                getHiddenMessengers(
                    context,
                    getContactCustomKey(contact)
                )
            }
            val visiblePickerMessengers = pickerMessengerList.filter { messenger ->
                val key = if (messenger.isCustomLink) messenger.id else messenger.packageName
                !hiddenSetInPicker.contains(key)
            }

            if (visiblePickerMessengers.isNotEmpty()) {
                Text(
                    text = "Мессенджеры",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column {
                        visiblePickerMessengers.forEachIndexed { index, messenger ->
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
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        MessengerBrandBadge(
                                            item = InstalledMessengerItem(
                                                packageName = messenger.packageName,
                                                messengerName = messenger.messengerName,
                                                brandColor = messenger.brandColor
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = messenger.messengerName,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = messenger.brandColor
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (messenger.isCustomLink) messenger.accountDetail else formatPhoneNumber(
                                            messenger.accountDetail
                                        ),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // 1. Chat Icon
                                    if (messenger.chatIntent != null) {
                                        IconButton(
                                            onClick = {
                                                onActionSelected(
                                                    CustomSwipeAction(
                                                        actionType = "messenger_chat",
                                                        targetValue = messenger.chatIntent.dataString
                                                            ?: "",
                                                        label = "${messenger.messengerName} Чат",
                                                        messengerName = messenger.messengerName
                                                    )
                                                )
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

                                    // 2. Audio Call Icon
                                    if (!messenger.isCustomLink && messenger.audioCallIntent != null) {
                                        IconButton(
                                            onClick = {
                                                onActionSelected(
                                                    CustomSwipeAction(
                                                        actionType = "messenger_audio",
                                                        targetValue = messenger.audioCallIntent.dataString
                                                            ?: "",
                                                        label = "${messenger.messengerName} Аудиовызов",
                                                        messengerName = messenger.messengerName
                                                    )
                                                )
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

                                    // 3. Video Call Icon
                                    if (!messenger.isCustomLink && messenger.videoCallIntent != null) {
                                        IconButton(
                                            onClick = {
                                                onActionSelected(
                                                    CustomSwipeAction(
                                                        actionType = "messenger_video",
                                                        targetValue = messenger.videoCallIntent.dataString
                                                            ?: "",
                                                        label = "${messenger.messengerName} Видеовызов",
                                                        messengerName = messenger.messengerName
                                                    )
                                                )
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

                        // Bottom "+" Button inside Messenger Card in Swipe Action Picker Dialog
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { showAddCustomLinkDialogInPicker = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SamsungGreen.copy(alpha = 0.12f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Добавить ссылку",
                                    tint = SamsungGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Section 3: E-mail
            if (emailsList.isNotEmpty()) {
                Text(
                    text = "E-mail",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp
                ) {
                    Column {
                        emailsList.forEachIndexed { index, emailItem ->
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
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = emailItem.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = emailItem.email,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                IconButton(
                                    onClick = {
                                        onActionSelected(
                                            CustomSwipeAction(
                                                actionType = "email",
                                                targetValue = emailItem.email,
                                                label = "Email (${emailItem.email})"
                                            )
                                        )
                                    },
                                    modifier = Modifier
                                        .size(37.6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFB300).copy(alpha = 0.15f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Email,
                                        contentDescription = "Письмо",
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddCustomLinkDialogInPicker) {
            AddCustomMessengerLinkDialog(
                context = context,
                onAddCustomLink = { newAccount ->
                    val updatedList = pickerMessengerList + newAccount
                    pickerMessengerList = updatedList
                    onUpdateMessengerAccounts(updatedList)
                    saveCustomMessengerLinks(context, getContactCustomKey(contact), updatedList)
                },
                onDismiss = { showAddCustomLinkDialogInPicker = false }
            )
        }
    }
}

@Composable
private fun AddCustomMessengerLinkDialog(
    context: Context,
    onAddCustomLink: (MessengerAccount) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMessengerIndex by remember { mutableIntStateOf(0) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var customLinkInput by remember { mutableStateOf("") }
    var showQrScannerDialog by remember { mutableStateOf(false) }

    val installedMessengers = remember { getInstalledMessengersList(context) }
    val activeMessenger =
        installedMessengers.getOrNull(selectedMessengerIndex) ?: installedMessengers.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Добавить ссылку мессенджера",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Выберите мессенджер",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Messenger Dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dropdownExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (activeMessenger != null) {
                                    MessengerBrandBadge(item = activeMessenger)
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Text(
                                    text = activeMessenger?.messengerName ?: "Мессенджер",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Выбрать",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        installedMessengers.forEachIndexed { idx, item ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        MessengerBrandBadge(item = item)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = item.messengerName,
                                            fontWeight = if (idx == selectedMessengerIndex) FontWeight.Bold else FontWeight.Normal,
                                            color = Color.Black
                                        )
                                    }
                                },
                                onClick = {
                                    selectedMessengerIndex = idx
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = customLinkInput,
                    onValueChange = { customLinkInput = it },
                    label = { Text("Вставьте ссылку") },
                    placeholder = { Text("https://t.me/username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // QR Code Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SamsungGreen.copy(alpha = 0.12f))
                        .clickable { showQrScannerDialog = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Сканировать QR-код",
                        tint = SamsungGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Добавить через QR-код",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SamsungGreen
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val inputUrl = customLinkInput.trim()
                    if (inputUrl.isNotBlank() && activeMessenger != null) {
                        val formattedUrl =
                            if (!inputUrl.startsWith("http://") && !inputUrl.startsWith("https://") && !inputUrl.startsWith(
                                    "viber://"
                                ) && !inputUrl.startsWith("skype:")
                            ) {
                                "https://$inputUrl"
                            } else inputUrl

                        val newAccount = MessengerAccount(
                            id = "custom_${System.currentTimeMillis()}",
                            packageName = activeMessenger.packageName,
                            messengerName = activeMessenger.messengerName,
                            accountDetail = formattedUrl,
                            brandColor = activeMessenger.brandColor,
                            chatIntent = Intent(Intent.ACTION_VIEW, formattedUrl.toUri()),
                            isCustomLink = true
                        )

                        onAddCustomLink(newAccount)
                        onDismiss()
                    }
                }
            ) {
                Text("Добавить", color = SamsungGreen, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )

    if (showQrScannerDialog) {
        var qrInputText by remember { mutableStateOf("") }
        var hasCameraPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            hasCameraPermission = isGranted
        }

        val galleryLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                try {
                    val image = InputImage.fromFilePath(context, uri)
                    val scanner = BarcodeScanning.getClient()
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            val qrUrl = barcodes.firstOrNull()?.rawValue
                            if (!qrUrl.isNullOrBlank()) {
                                val formatted =
                                    if (!qrUrl.startsWith("http://") && !qrUrl.startsWith("https://") && !qrUrl.startsWith(
                                            "viber://"
                                        ) && !qrUrl.startsWith("skype:")
                                    ) {
                                        "https://$qrUrl"
                                    } else qrUrl
                                customLinkInput = formatted
                                showQrScannerDialog = false
                                Toast.makeText(
                                    context,
                                    "QR-код из галереи успешно распознан!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Не удалось найти QR-код на изображении",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                context,
                                "Ошибка анализа изображения",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } catch (_: Exception) {
                    Toast.makeText(
                        context,
                        "Не удалось загрузить фото из галереи",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showQrScannerDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "QR-код",
                        tint = SamsungGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Сканирование QR-кода", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Camera Scanner Frame
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black,
                        modifier = Modifier
                            .size(190.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (hasCameraPermission) {
                                CameraQrScannerView { scannedUrl ->
                                    val formatted =
                                        if (!scannedUrl.startsWith("http://") && !scannedUrl.startsWith(
                                                "https://"
                                            ) && !scannedUrl.startsWith("viber://") && !scannedUrl.startsWith(
                                                "skype:"
                                            )
                                        ) {
                                            "https://$scannedUrl"
                                        } else scannedUrl
                                    customLinkInput = formatted
                                    showQrScannerDialog = false
                                    Toast.makeText(
                                        context,
                                        "QR-код успешно сканирован!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "Камера",
                                        tint = SamsungGreen,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(
                                        onClick = {
                                            permissionLauncher.launch(Manifest.permission.CAMERA)
                                        }
                                    ) {
                                        Text(
                                            "Разрешить камеру",
                                            color = SamsungGreen,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Наведите камеру или введите ссылку ручным вводом",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = qrInputText,
                        onValueChange = { qrInputText = it },
                        label = { Text("Ссылка из QR-кода") },
                        placeholder = { Text("https://t.me/username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Button: Выбрать в галерее
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { galleryLauncher.launch("image/*") }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Выбрать в галерее",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Выбрать в галерее",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (qrInputText.isNotBlank()) {
                            customLinkInput = qrInputText.trim()
                            showQrScannerDialog = false
                        }
                    }
                ) {
                    Text("Использовать", color = SamsungGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQrScannerDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun CameraQrScannerView(
    onQrCodeScanned: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isScanned by remember { mutableStateOf(false) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val barcodeScanner = BarcodeScanning.getClient()
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { imageProxy ->
                    @Suppress("UnsafeOptInUsageError")
                    val mediaImage = imageProxy.image
                    if (mediaImage != null && !isScanned) {
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        barcodeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                val qrUrl = barcodes.firstOrNull()?.rawValue
                                if (!qrUrl.isNullOrBlank() && !isScanned) {
                                    isScanned = true
                                    onQrCodeScanned(qrUrl)
                                }
                            }
                            .addOnCompleteListener {
                                imageProxy.close()
                            }
                    } else {
                        imageProxy.close()
                    }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
private fun showCalendarDatePicker(
    context: Context,
    initialDateString: String,
    onDateSelected: (formattedDate: String) -> Unit
) {
    val cal = java.util.Calendar.getInstance()
    val numbers =
        Regex("\\d+").findAll(initialDateString).mapNotNull { it.value.toIntOrNull() }.toList()
    if (numbers.size >= 3) {
        val y = numbers.firstOrNull { it in 1900..2100 } ?: cal.get(java.util.Calendar.YEAR)
        val nonYears = numbers.filter { it != y }
        val m =
            if (nonYears.size >= 2 && nonYears[1] in 1..12) nonYears[1] - 1 else cal.get(java.util.Calendar.MONTH)
        val d =
            if (nonYears.isNotEmpty() && nonYears[0] in 1..31) nonYears[0] else cal.get(java.util.Calendar.DAY_OF_MONTH)
        cal.set(y, m, d)
    }

    val picker = android.app.DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val monthNames = arrayOf(
                "января",
                "февраля",
                "марта",
                "апреля",
                "мая",
                "июня",
                "июля",
                "августа",
                "сентября",
                "октября",
                "ноября",
                "декабря"
            )
            val mName = monthNames.getOrElse(month) { "" }
            val formatted = "$dayOfMonth $mName $year г."
            onDateSelected(formatted)
        },
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH),
        cal.get(java.util.Calendar.DAY_OF_MONTH)
    )
    picker.show()
}

@Composable
private fun EditContactDialog(
    contact: FavoriteContact,
    phoneNumbersList: List<ContactPhoneNumber>,
    emailsList: List<ContactEmail>,
    birthdayInfo: ContactBirthday?,
    importantDatesList: List<ContactImportantDate> = emptyList(),
    messengerAccountsList: List<MessengerAccount>,
    avatarBitmap: ImageBitmap?,
    context: Context,
    onSave: (
        newName: String,
        newPhones: List<ContactPhoneNumber>,
        newEmails: List<ContactEmail>,
        newBirthday: ContactBirthday?,
        newImportantDates: List<ContactImportantDate>,
        updatedMessengers: List<MessengerAccount>,
        hiddenSet: Set<String>,
        newAvatarBitmap: ImageBitmap?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var nameInput by remember { mutableStateOf(contact.name) }
    var editablePhones by remember { mutableStateOf(phoneNumbersList.toMutableList()) }
    var editableEmails by remember { mutableStateOf(emailsList.toMutableList()) }
    var editableBirthday by remember { mutableStateOf(birthdayInfo) }
    var birthdayInput by remember { mutableStateOf(birthdayInfo?.formattedDate ?: "") }
    var isEditingBirthday by remember { mutableStateOf(birthdayInfo != null) }

    var customImportantDates by remember { mutableStateOf(importantDatesList.toMutableList()) }
    var currentAvatarBitmap by remember { mutableStateOf(avatarBitmap) }

    var editableMessengers by remember { mutableStateOf(messengerAccountsList.toMutableList()) }

    val contactKey = remember(contact) { getContactCustomKey(contact) }
    var hiddenSet by remember {
        mutableStateOf(
            getHiddenMessengers(
                context,
                contactKey
            ).toMutableSet()
        )
    }

    var showAddMessengerDialogInEdit by remember { mutableStateOf(false) }
    var customLinkToEditIndex by remember { mutableStateOf<Int?>(null) }

    val galleryAvatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        currentAvatarBitmap = bitmap.asImageBitmap()
                        Toast.makeText(context, "Фото контакта обновлено", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Не удалось загрузить фото", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактирование контакта", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. ROUND AVATAR WITH CAMERA BADGE
                Box(
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                        .clickable { galleryAvatarLauncher.launch("image/*") }
                ) {
                    val bitmap = currentAvatarBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Аватар контакта",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        val avatarBgColor = remember(contact.name) {
                            val colors = listOf(
                                Color(0xFFE57373), Color(0xFFF06292), Color(0xFFBA68C8),
                                Color(0xFF9575CD), Color(0xFF7986CB), Color(0xFF64B5F6)
                            )
                            val index = (contact.name.hashCode() and Int.MAX_VALUE) % colors.size
                            colors[index]
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(avatarBgColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = contact.name.trim().firstOrNull()?.uppercaseChar()
                                    ?.toString() ?: "?",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 36.sp
                            )
                        }
                    }

                    // Camera Badge Overlay
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.BottomEnd)
                            .clip(CircleShape)
                            .background(SamsungGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Изменить фото",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Name Field
                Text(
                    text = "Имя контакта",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2. PHONE NUMBERS SECTION
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Телефоны",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    IconButton(
                        onClick = {
                            editablePhones = (editablePhones + ContactPhoneNumber(
                                "",
                                "Мобильный"
                            )).toMutableList()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Добавить номер",
                            tint = SamsungGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                editablePhones.forEachIndexed { index, phoneItem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = phoneItem.number,
                            onValueChange = { newNum ->
                                val updated = editablePhones.toMutableList()
                                updated[index] = phoneItem.copy(number = newNum)
                                editablePhones = updated
                            },
                            label = { Text(phoneItem.label) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        if (editablePhones.size > 1) {
                            IconButton(
                                onClick = {
                                    val updated = editablePhones.toMutableList()
                                    updated.removeAt(index)
                                    editablePhones = updated
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Удалить номер",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. MESSENGERS SECTION
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Мессенджеры (видимость)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    IconButton(
                        onClick = { showAddMessengerDialogInEdit = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Добавить мессенджер",
                            tint = SamsungGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        editableMessengers.forEachIndexed { index, messenger ->
                            val messengerKey =
                                if (messenger.isCustomLink) messenger.id else messenger.packageName
                            val isHidden = hiddenSet.contains(messengerKey)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MessengerBrandBadge(
                                        item = InstalledMessengerItem(
                                            packageName = messenger.packageName,
                                            messengerName = messenger.messengerName,
                                            brandColor = messenger.brandColor
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = messenger.messengerName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isHidden) Color.Gray else messenger.brandColor
                                        )
                                        Text(
                                            text = if (messenger.isCustomLink) messenger.accountDetail else formatPhoneNumber(
                                                messenger.accountDetail
                                            ),
                                            fontSize = 11.sp,
                                            color = if (isHidden) Color.Gray.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface.copy(
                                                alpha = 0.6f
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (messenger.isCustomLink) {
                                        // Edit Custom Link Button
                                        IconButton(
                                            onClick = { customLinkToEditIndex = index },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Изменить ссылку",
                                                tint = SamsungGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // Delete Custom Link Button
                                        IconButton(
                                            onClick = {
                                                val updated = editableMessengers.toMutableList()
                                                updated.removeAt(index)
                                                editableMessengers = updated
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Удалить ссылку",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    } else {
                                        // Visibility Toggle Button for Standard Messengers
                                        IconButton(
                                            onClick = {
                                                val newSet = hiddenSet.toMutableSet()
                                                if (isHidden) newSet.remove(messengerKey) else newSet.add(
                                                    messengerKey
                                                )
                                                hiddenSet = newSet
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (isHidden) "Показать" else "Скрыть",
                                                tint = if (isHidden) Color.Gray else SamsungGreen,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. E-MAIL SECTION
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "E-mail адреса",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    IconButton(
                        onClick = {
                            editableEmails =
                                (editableEmails + ContactEmail("", "Личный")).toMutableList()
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Добавить email",
                            tint = SamsungGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))

                if (editableEmails.isEmpty()) {
                    Text(
                        "E-mail адреса не заданы",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    editableEmails.forEachIndexed { index, emailItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = emailItem.email,
                                onValueChange = { newEmail ->
                                    val updated = editableEmails.toMutableList()
                                    updated[index] = emailItem.copy(email = newEmail)
                                    editableEmails = updated
                                },
                                label = { Text(emailItem.label) },
                                placeholder = { Text("example@mail.ru") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val updated = editableEmails.toMutableList()
                                    updated.removeAt(index)
                                    editableEmails = updated
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Удалить email",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 5. BIRTHDAY & IMPORTANT DATES SECTION
                Text(
                    "Важные даты",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (isEditingBirthday) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = birthdayInput,
                            onValueChange = { birthdayInput = it },
                            label = { Text("День рождения") },
                            placeholder = { Text("15 мая 1990 г.") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        showCalendarDatePicker(context, birthdayInput) { newDate ->
                                            birthdayInput = newDate
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Event,
                                        contentDescription = "Календарь",
                                        tint = SamsungGreen
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                isEditingBirthday = false
                                birthdayInput = ""
                                editableBirthday = null
                            }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Удалить день рождения",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable {
                                isEditingBirthday = true
                                birthdayInput = "15 мая 1990 г."
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Cake,
                            contentDescription = "День рождения",
                            tint = SamsungGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "+ Добавить день рождения",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = SamsungGreen
                        )
                    }
                }

                // Custom Important Dates
                customImportantDates.forEachIndexed { index, dateItem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = dateItem.label,
                            onValueChange = { newLabel ->
                                val updated = customImportantDates.toMutableList()
                                updated[index] = dateItem.copy(label = newLabel)
                                customImportantDates = updated
                            },
                            label = { Text("Название даты") },
                            placeholder = { Text("Годовщина") },
                            singleLine = true,
                            modifier = Modifier.weight(0.45f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedTextField(
                            value = dateItem.dateString,
                            onValueChange = { newDate ->
                                val updated = customImportantDates.toMutableList()
                                updated[index] = dateItem.copy(dateString = newDate)
                                customImportantDates = updated
                            },
                            label = { Text("Дата") },
                            placeholder = { Text("15 мая 2020 г.") },
                            singleLine = true,
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        showCalendarDatePicker(
                                            context,
                                            dateItem.dateString
                                        ) { newDate ->
                                            val updated = customImportantDates.toMutableList()
                                            updated[index] = dateItem.copy(dateString = newDate)
                                            customImportantDates = updated
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Event,
                                        contentDescription = "Календарь",
                                        tint = SamsungGreen
                                    )
                                }
                            },
                            modifier = Modifier.weight(0.55f)
                        )

                        IconButton(
                            onClick = {
                                val updated = customImportantDates.toMutableList()
                                updated.removeAt(index)
                                customImportantDates = updated
                            }
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Удалить дату",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .clickable {
                            val labels =
                                listOf("Годовщина", "Юбилей", "Именины", "Памятная дата", "Свадьба")
                            val nextLabel =
                                labels.getOrElse(customImportantDates.size % labels.size) { "Важная дата" }
                            customImportantDates = (customImportantDates + ContactImportantDate(
                                label = nextLabel,
                                dateString = ""
                            )).toMutableList()
                        }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Event,
                        contentDescription = "Важная дата",
                        tint = SamsungGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "+ Добавить другую важную дату",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = SamsungGreen
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanPhones = editablePhones.filter { it.number.isNotBlank() }
                    val cleanEmails = editableEmails.filter { it.email.isNotBlank() }
                    val newBday = if (isEditingBirthday && birthdayInput.isNotBlank()) {
                        parseBirthdayString(birthdayInput.trim())
                    } else null

                    onSave(
                        nameInput.trim(),
                        cleanPhones.ifEmpty { phoneNumbersList },
                        cleanEmails,
                        newBday,
                        customImportantDates,
                        editableMessengers,
                        hiddenSet,
                        currentAvatarBitmap
                    )
                }
            ) {
                Text("Сохранить", color = SamsungGreen, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )

    if (showAddMessengerDialogInEdit) {
        AddCustomMessengerLinkDialog(
            context = context,
            onAddCustomLink = { newAccount ->
                editableMessengers = (editableMessengers + newAccount).toMutableList()
            },
            onDismiss = { showAddMessengerDialogInEdit = false }
        )
    }

    val editingIndex = customLinkToEditIndex
    if (editingIndex != null && editingIndex in editableMessengers.indices) {
        val targetMessenger = editableMessengers[editingIndex]
        EditCustomMessengerLinkDialog(
            initialMessenger = targetMessenger,
            context = context,
            onSaveCustomLink = { updatedMessenger ->
                val updated = editableMessengers.toMutableList()
                updated[editingIndex] = updatedMessenger
                editableMessengers = updated
                customLinkToEditIndex = null
            },
            onDismiss = { customLinkToEditIndex = null }
        )
    }
}

@Composable
private fun EditCustomMessengerLinkDialog(
    initialMessenger: MessengerAccount,
    context: Context,
    onSaveCustomLink: (MessengerAccount) -> Unit,
    onDismiss: () -> Unit
) {
    val installedMessengers = remember { getInstalledMessengersList(context) }
    var selectedMessengerIndex by remember {
        mutableIntStateOf(
            installedMessengers.indexOfFirst {
                it.messengerName.equals(
                    initialMessenger.messengerName,
                    true
                )
            }.coerceAtLeast(0)
        )
    }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var customLinkInput by remember { mutableStateOf(initialMessenger.accountDetail) }

    val activeMessenger =
        installedMessengers.getOrNull(selectedMessengerIndex) ?: installedMessengers.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Редактировать ссылку",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Выберите мессенджер",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { dropdownExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (activeMessenger != null) {
                                    MessengerBrandBadge(item = activeMessenger)
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Text(
                                    text = activeMessenger?.messengerName ?: "Мессенджер",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Выбрать",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        installedMessengers.forEachIndexed { idx, item ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        MessengerBrandBadge(item = item)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = item.messengerName,
                                            fontWeight = if (idx == selectedMessengerIndex) FontWeight.Bold else FontWeight.Normal,
                                            color = Color.Black
                                        )
                                    }
                                },
                                onClick = {
                                    selectedMessengerIndex = idx
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = customLinkInput,
                    onValueChange = { customLinkInput = it },
                    label = { Text("Вставьте ссылку") },
                    placeholder = { Text("https://t.me/username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val inputUrl = customLinkInput.trim()
                    if (inputUrl.isNotBlank() && activeMessenger != null) {
                        val formattedUrl =
                            if (!inputUrl.startsWith("http://") && !inputUrl.startsWith("https://") && !inputUrl.startsWith(
                                    "viber://"
                                ) && !inputUrl.startsWith("skype:")
                            ) {
                                "https://$inputUrl"
                            } else inputUrl

                        val updatedAccount = initialMessenger.copy(
                            packageName = activeMessenger.packageName,
                            messengerName = activeMessenger.messengerName,
                            accountDetail = formattedUrl,
                            brandColor = activeMessenger.brandColor,
                            chatIntent = Intent(Intent.ACTION_VIEW, formattedUrl.toUri())
                        )

                        onSaveCustomLink(updatedAccount)
                        onDismiss()
                    }
                }
            ) {
                Text("Сохранить", color = SamsungGreen, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
