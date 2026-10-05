package com.asinosoft.cdm.ui.recents.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.asinosoft.cdm.R
import com.asinosoft.cdm.ui.theme.SamsungGreen
import com.asinosoft.cdm.util.OemShellGuide
import com.asinosoft.cdm.util.OemShellHelper

/** Full-screen "Permissions help" window, styled like the settings sub-pages. */
@Composable
fun PermissionsHelpDialog(
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        BackHandler(onBack = onDismiss)

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_permissions_help_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            bottomBar = {
                Button(
                    onClick = onDone,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SamsungGreen,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(54.dp)
                ) {
                    Text(
                        text = stringResource(R.string.action_done),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp)
            ) {
                PermissionsHelpContent()
            }
        }
    }
}

/** Shell-specific checklist; the bold title of each item opens the matching system screen. */
@Composable
fun PermissionsHelpContent() {
    val context = LocalContext.current
    val guide = remember { OemShellHelper.detectGuide() }
    val shellName = remember(guide) {
        when (guide) {
            OemShellGuide.MIUI -> if (OemShellHelper.isHyperOs()) "HyperOS" else "MIUI"
            OemShellGuide.HUAWEI -> "Huawei / Honor"
            OemShellGuide.OPPO -> "OPPO / realme / OnePlus"
            OemShellGuide.VIVO -> "vivo / iQOO"
            OemShellGuide.NONE -> ""
        }
    }
    val (autostartRes, popupsRes, batteryRes) = when (guide) {
        OemShellGuide.HUAWEI -> Triple(
            R.string.settings_permissions_help_item_huawei_autostart,
            R.string.settings_permissions_help_item_huawei_popups,
            R.string.settings_permissions_help_item_huawei_battery
        )
        OemShellGuide.OPPO -> Triple(
            R.string.settings_permissions_help_item_autostart_list,
            R.string.settings_permissions_help_item_oppo_popups,
            R.string.settings_permissions_help_item_oppo_battery
        )
        OemShellGuide.VIVO -> Triple(
            R.string.settings_permissions_help_item_autostart_list,
            R.string.settings_permissions_help_item_vivo_popups,
            R.string.settings_permissions_help_item_vivo_battery
        )
        else -> Triple(
            R.string.settings_permissions_help_item_autostart,
            R.string.settings_permissions_help_item_popups,
            R.string.settings_permissions_help_item_battery
        )
    }
    val helpItems = listOf(
        autostartRes to { OemShellHelper.openAutostartSettings(context) },
        popupsRes to { OemShellHelper.openSpecialPermissionsSettings(context) },
        batteryRes to { OemShellHelper.openBatterySettings(context) }
    )
    Text(
        text = stringResource(R.string.settings_permissions_help_header, shellName),
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        helpItems.forEach { (res, onTitleClick) ->
            Row {
                Text(
                    text = "•",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(end = 10.dp)
                )
                Text(
                    text = boldMarkup(stringResource(res, shellName), onTitleClick),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

/** Renders `**text**` fragments in bold; the first one becomes a link when [onTitleClick] is set. */
private fun boldMarkup(text: String, onTitleClick: (() -> Unit)? = null): AnnotatedString = buildAnnotatedString {
    val bold = SpanStyle(fontWeight = FontWeight.Bold)
    text.split("**").forEachIndexed { index, part ->
        when {
            index == 1 && onTitleClick != null -> withLink(
                LinkAnnotation.Clickable(
                    tag = "title",
                    styles = TextLinkStyles(
                        bold.copy(color = SamsungGreen, textDecoration = TextDecoration.Underline)
                    )
                ) { onTitleClick() }
            ) { append(part) }
            index % 2 == 1 -> withStyle(bold) { append(part) }
            else -> append(part)
        }
    }
}
