package com.asinosoft.dialer.ui.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asinosoft.dialer.data.model.FavoriteTab
import com.asinosoft.dialer.data.model.FavoritesViewMode
import com.asinosoft.dialer.ui.recents.components.FavoritesSettingsTab
import com.asinosoft.dialer.ui.theme.SamsungGreen

@Composable
fun OnboardingFavoritesSetupScreen(
    selectedRowsCount: Int,
    favoritesViewMode: FavoritesViewMode,
    maxPossibleRows: Int,
    tabs: List<FavoriteTab>,
    onRowsCountSelected: (Int) -> Unit,
    onFavoritesViewModeSelected: (FavoritesViewMode) -> Unit,
    onAddTab: (String) -> Unit,
    onRenameTab: (String, String) -> Unit,
    onDeleteTab: (String) -> Unit,
    onReorderTabs: (List<FavoriteTab>) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddTabDialog by remember { mutableStateOf(false) }
    var newTabNameInput by remember { mutableStateOf("") }

    var tabToRename by remember { mutableStateOf<FavoriteTab?>(null) }
    var renameTabInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp, bottom = 12.dp)
    ) {
        Text(
            text = "Избранное",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Настройте вид избранных контактов. Эти параметры всегда можно изменить в настройках.",
            fontSize = 15.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)
        )

        Spacer(modifier = Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            FavoritesSettingsTab(
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onFinish,
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SamsungGreen,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                text = "Готово",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
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
                    label = { Text("Название") },
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
