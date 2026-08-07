package com.example.test_dialer.ui.recents.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
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
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.test_dialer.data.model.FavoriteContact
import com.example.test_dialer.ui.theme.SamsungGreen
import com.example.test_dialer.ui.theme.SamsungSmsBlue
import com.example.test_dialer.util.formatPhoneNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactPhoneNumber(
    val number: String,
    val label: String
)

@Composable
fun ContactDetailDialog(
    contact: FavoriteContact,
    onDismiss: () -> Unit,
    onCall: (String) -> Unit,
    onSms: (String) -> Unit,
    onRemoveFavorite: (FavoriteContact) -> Unit
) {
    val context = LocalContext.current
    var avatarBitmap by remember(contact.photoUri) { mutableStateOf<ImageBitmap?>(null) }
    var phoneNumbersList by remember(contact) {
        mutableStateOf(listOf(ContactPhoneNumber(number = contact.number, label = "Мобильный")))
    }

    LaunchedEffect(contact) {
        if (!contact.photoUri.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val uri = Uri.parse(contact.photoUri)
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bitmap = BitmapFactory.decodeStream(stream)
                        avatarBitmap = bitmap?.asImageBitmap()
                    }
                } catch (e: Exception) {
                    avatarBitmap = null
                }
            }
        } else {
            avatarBitmap = null
        }

        // Query all phone numbers for this contact
        val loadedNumbers = loadContactPhoneNumbers(context, contact)
        if (loadedNumbers.isNotEmpty()) {
            phoneNumbersList = loadedNumbers
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
                        .height(300.dp)
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

                        Spacer(modifier = Modifier.height(4.dp))

                        // Primary Phone Number
                        Text(
                            text = formatPhoneNumber(contact.number),
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(28.dp))

                        // Quick Action Buttons Row (Call, SMS, Info)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val primaryNumber = phoneNumbersList.firstOrNull()?.number ?: contact.number

                            ActionButtonItem(
                                icon = Icons.Default.Phone,
                                label = "Вызов",
                                containerColor = SamsungGreen,
                                contentColor = Color.White,
                                onClick = {
                                    onDismiss()
                                    onCall(primaryNumber)
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

                        Spacer(modifier = Modifier.height(28.dp))

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

                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            IconButton(
                                                onClick = {
                                                    onDismiss()
                                                    onCall(phoneItem.number)
                                                },
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(SamsungGreen.copy(alpha = 0.12f))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Phone,
                                                    contentDescription = "Вызов",
                                                    tint = SamsungGreen,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    onDismiss()
                                                    onSms(phoneItem.number)
                                                },
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(SamsungSmsBlue.copy(alpha = 0.12f))
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.Message,
                                                    contentDescription = "SMS",
                                                    tint = SamsungSmsBlue,
                                                    modifier = Modifier.size(20.dp)
                                                )
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
                                        val primaryNumber = phoneNumbersList.firstOrNull()?.number ?: contact.number
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
