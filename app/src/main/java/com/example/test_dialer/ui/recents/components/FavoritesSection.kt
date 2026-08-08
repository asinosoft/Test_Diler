package com.example.test_dialer.ui.recents.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.test_dialer.data.model.FavoriteContact
import com.example.test_dialer.ui.theme.SamsungGreen

@Composable
fun FavoritesSection(
    favorites: List<FavoriteContact>,
    selectedContact: FavoriteContact?,
    onCall: (String, Int?) -> Unit,
    onSms: (String) -> Unit,
    onSelectContact: (FavoriteContact) -> Unit,
    onAddFavoriteClick: () -> Unit,
    onContactClick: ((FavoriteContact) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
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
                    .clickable { onAddFavoriteClick() }
                    .padding(vertical = 4.dp, horizontal = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (favorites.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddFavoriteClick() }
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
        } else {
            val rows = favorites.chunked(3)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        for (i in 0 until 3) {
                            if (i < rowItems.size) {
                                val contact = rowItems[i]
                                FavoriteContactCard(
                                    contact = contact,
                                    isSelected = selectedContact?.id == contact.id,
                                    onCall = onCall,
                                    onSms = onSms,
                                    onSelect = onSelectContact,
                                    onContactClick = onContactClick,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}
