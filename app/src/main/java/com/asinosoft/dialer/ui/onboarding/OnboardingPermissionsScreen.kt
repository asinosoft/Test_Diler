package com.asinosoft.dialer.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asinosoft.dialer.R
import com.asinosoft.dialer.ui.theme.SamsungGreen

enum class OnboardingPermissionStep {
    RUNTIME,
    OVERLAY
}

@Composable
fun OnboardingPermissionsScreen(
    isRuntimeGranted: Boolean,
    isOverlayGranted: Boolean,
    highlightedStep: OnboardingPermissionStep?,
    onRequestRuntimePermissions: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allGranted = isRuntimeGranted && isOverlayGranted

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 28.dp, bottom = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = SamsungGreen
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.onboarding_permissions_intro),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.62f)
            )

            Spacer(modifier = Modifier.height(22.dp))

            PermissionBlock(
                title = stringResource(R.string.onboarding_permissions_title),
                subtitle = stringResource(R.string.onboarding_permissions_subtitle),
                details = listOf(
                    stringResource(R.string.onboarding_perm_call_log),
                    stringResource(R.string.onboarding_perm_contacts),
                    stringResource(R.string.onboarding_perm_phone)
                ),
                isGranted = isRuntimeGranted,
                isHighlighted = highlightedStep == OnboardingPermissionStep.RUNTIME,
                onClick = onRequestRuntimePermissions
            )

            Spacer(modifier = Modifier.height(12.dp))

            PermissionBlock(
                title = stringResource(R.string.onboarding_overlay_title),
                subtitle = stringResource(R.string.onboarding_overlay_subtitle),
                details = emptyList(),
                isGranted = isOverlayGranted,
                isHighlighted = highlightedStep == OnboardingPermissionStep.OVERLAY,
                onClick = onRequestOverlayPermission
            )
        }

        Button(
            onClick = {
                when {
                    !isRuntimeGranted -> onRequestRuntimePermissions()
                    !isOverlayGranted -> onRequestOverlayPermission()
                    else -> onContinue()
                }
            },
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SamsungGreen,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(bottom = 8.dp)
        ) {
            Text(
                text = stringResource(
                    if (allGranted) R.string.action_next else R.string.action_next_chevron
                ),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun PermissionBlock(
    title: String,
    subtitle: String,
    details: List<String>,
    isGranted: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            isHighlighted -> SamsungGreen
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        },
        label = "permission_border"
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            isHighlighted -> SamsungGreen.copy(alpha = 0.10f)
            else -> MaterialTheme.colorScheme.surface
        },
        label = "permission_bg"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isHighlighted) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = !isGranted, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                if (details.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    details.forEach { line ->
                        Text(
                            text = "• $line",
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (isGranted) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    color = SamsungGreen.copy(alpha = 0.15f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.onboarding_permission_granted_cd),
                            tint = SamsungGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            } else {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onClick)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Transparent
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_toggle_on),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}
