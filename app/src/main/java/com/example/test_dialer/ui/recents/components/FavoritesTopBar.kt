package com.example.test_dialer.ui.recents.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_dialer.data.model.FavoriteContact
import com.example.test_dialer.ui.theme.MissedRed
import com.example.test_dialer.ui.theme.SamsungGreen

@Composable
fun FavoritesTopBar(
    isVisible: Boolean,
    selectedContact: FavoriteContact?,
    onDeleteClick: (FavoriteContact) -> Unit,
    onAddClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            tonalElevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Info text (Selected contact name)
                Column {
                    Text(
                        text = "Выбран контакт",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = selectedContact?.name ?: "Избранный",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Actions (Удалить, Добавить, Настройки)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 5.2.1: Удалить
                    IconButton(
                        onClick = { selectedContact?.let { onDeleteClick(it) } },
                        enabled = selectedContact != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Удалить из избранного",
                            tint = if (selectedContact != null) MissedRed else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 5.2.2: Добавить
                    IconButton(
                        onClick = onAddClick
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Добавить новый",
                            tint = SamsungGreen,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // 5.2.3: Настройки
                    IconButton(
                        onClick = onSettingsClick
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Настройки",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
