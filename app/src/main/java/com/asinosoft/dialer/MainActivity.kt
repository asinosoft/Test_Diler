package com.asinosoft.dialer

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.asinosoft.dialer.service.MissedCallNotificationListener
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asinosoft.dialer.data.model.FavoriteContact
import com.asinosoft.dialer.ui.recents.RecentsScreen
import com.asinosoft.dialer.ui.recents.RecentsViewModel
import com.asinosoft.dialer.ui.theme.SamsungGreen
import com.asinosoft.dialer.ui.theme.DialerTheme

class MainActivity : ComponentActivity() {

    private var mainViewModel: RecentsViewModel? = null

    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.WRITE_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.WRITE_CONTACTS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.CALL_PHONE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.asinosoft.dialer.util.AppLifecycleTracker.init(application)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()

        setContent {
            DialerTheme {
                val viewModel: RecentsViewModel = viewModel()
                mainViewModel = viewModel

                var isPermissionsGranted by remember {
                    mutableStateOf(
                        requiredPermissions.all { perm ->
                            ContextCompat.checkSelfPermission(
                                this,
                                perm
                            ) == PackageManager.PERMISSION_GRANTED
                        }
                    )
                }

                val roleLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) {
                    viewModel.loadCallLogs()
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val allGranted = requiredPermissions.all { perm ->
                        permissions[perm] == true || ContextCompat.checkSelfPermission(
                            this,
                            perm
                        ) == PackageManager.PERMISSION_GRANTED
                    }
                    isPermissionsGranted = allGranted
                    if (allGranted) {
                        requestDefaultDialerRole(roleLauncher)
                        viewModel.loadCallLogs()
                    }
                }

                LaunchedEffect(Unit) {
                    if (isPermissionsGranted) {
                        requestDefaultDialerRole(roleLauncher)
                        viewModel.loadCallLogs()
                    } else {
                        // System dialog only — custom screen stays as fallback if user denies
                        permissionLauncher.launch(requiredPermissions)
                    }
                }

                LaunchedEffect(intent) {
                    handleContactOpenIntent(intent)
                }

                if (isPermissionsGranted) {
                    RecentsScreen(
                        viewModel = viewModel,
                        onCall = { number, simSlot -> makeCall(number, simSlot) },
                        onSms = { number -> sendSms(number) }
                    )
                } else {
                    PermissionRequestScreen(
                        onRequestPermissions = { permissionLauncher.launch(requiredPermissions) }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleContactOpenIntent(intent)
    }

    private fun handleContactOpenIntent(intent: Intent?) {
        val number = intent?.getStringExtra(EXTRA_OPEN_CONTACT_NUMBER)
        if (!number.isNullOrBlank()) {
            val name = intent.getStringExtra(EXTRA_OPEN_CONTACT_NAME)
            val id = intent.getStringExtra(EXTRA_OPEN_CONTACT_ID).orEmpty()
            val contact = FavoriteContact(
                id = id,
                name = name ?: number,
                number = number,
                photoUri = null
            )
            mainViewModel?.openContactDetail(contact, initialTab = 0)
        }
    }

    companion object {
        const val EXTRA_OPEN_CONTACT_NUMBER = "extra_open_contact_number"
        const val EXTRA_OPEN_CONTACT_NAME = "extra_open_contact_name"
        const val EXTRA_OPEN_CONTACT_ID = "extra_open_contact_id"
    }

    override fun onResume() {
        super.onResume()
        clearMissedCallNotifications()
    }

    private fun clearMissedCallNotifications() {
        try {
            // 1. Cancel own app's notifications (missed calls, etc.)
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancelAll()
        } catch (_: Exception) {
        }

        try {
            // 2. Cancel Telecom system missed call notification
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val telecomManager = getSystemService(TELECOM_SERVICE) as? TelecomManager
                @Suppress("MissingPermission")
                telecomManager?.cancelMissedCallsNotification()
            }
        } catch (_: Exception) {
        }

        try {
            // 3. Cancel through MissedCallNotificationListener if active
            MissedCallNotificationListener.cancelActiveIfConnected()
        } catch (_: Exception) {
        }

        try {
            // 4. Mark missed calls as read/seen in CallLog
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALL_LOG) == PackageManager.PERMISSION_GRANTED) {
                val values = ContentValues().apply {
                    put(CallLog.Calls.NEW, 0)
                    put(CallLog.Calls.IS_READ, 1)
                }
                contentResolver.update(
                    CallLog.Calls.CONTENT_URI,
                    values,
                    "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.NEW} = 1",
                    arrayOf(CallLog.Calls.MISSED_TYPE.toString())
                )
            }
        } catch (_: Exception) {
        }
    }

    private fun requestDefaultDialerRole(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) && !roleManager.isRoleHeld(
                        RoleManager.ROLE_DIALER
                    )
                ) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    launcher.launch(intent)
                }
            } else {
                val telecomManager = getSystemService(TELECOM_SERVICE) as? TelecomManager
                if (telecomManager != null && packageName != telecomManager.defaultDialerPackage) {
                    val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                        putExtra(
                            TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                            packageName
                        )
                    }
                    launcher.launch(intent)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun makeCall(phoneNumber: String, simSlot: Int? = null) {
        if (phoneNumber.isBlank()) return

        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        val uri = "tel:$cleanNumber".toUri()

        val hasCallPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCallPermission) {
            try {
                val telecomManager = getSystemService(TELECOM_SERVICE) as? TelecomManager
                val subscriptionManager =
                    getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

                val extras = Bundle()
                if (telecomManager != null && subscriptionManager != null && simSlot != null) {
                    try {
                        val targetSlotIndex = simSlot - 1 // 0 for SIM1, 1 for SIM2
                        val activeSubscriptions = try {
                            subscriptionManager.activeSubscriptionInfoList
                        } catch (_: SecurityException) {
                            null
                        }

                        val targetSub =
                            activeSubscriptions?.find { it.simSlotIndex == targetSlotIndex }
                                ?: activeSubscriptions?.getOrNull(targetSlotIndex)

                        val phoneAccountHandles = telecomManager.callCapablePhoneAccounts

                        if (!phoneAccountHandles.isNullOrEmpty()) {
                            var targetHandle: PhoneAccountHandle? = null

                            val subIdStr = targetSub?.subscriptionId?.toString()
                            val iccIdStr = targetSub?.iccId.orEmpty()

                            for (handle in phoneAccountHandles) {
                                val hId = handle.id
                                if ((!subIdStr.isNullOrBlank() && hId == subIdStr) ||
                                    (iccIdStr.isNotBlank() && hId.contains(iccIdStr)) ||
                                    hId == targetSlotIndex.toString() ||
                                    hId.endsWith(":$targetSlotIndex") ||
                                    hId.endsWith("_$targetSlotIndex") ||
                                    hId.contains("slot$targetSlotIndex", ignoreCase = true) ||
                                    hId.contains("sim${targetSlotIndex + 1}", ignoreCase = true)
                                ) {
                                    targetHandle = handle
                                    break
                                }
                            }

                            if (targetHandle == null && targetSub != null) {
                                for (handle in phoneAccountHandles) {
                                    if (subIdStr != null && handle.id.contains(subIdStr)) {
                                        targetHandle = handle
                                        break
                                    }
                                }
                            }

                            if (targetHandle == null) {
                                targetHandle = phoneAccountHandles.getOrNull(targetSlotIndex)
                                    ?: phoneAccountHandles.firstOrNull()
                            }

                            if (targetHandle != null) {
                                extras.putParcelable(
                                    TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                                    targetHandle
                                )
                            }
                        }

                        extras.putInt("com.android.phone.extra.slot", targetSlotIndex)
                        extras.putInt("simSlot", targetSlotIndex)
                        extras.putInt("slot", targetSlotIndex)
                        extras.putInt("sim_slot", targetSlotIndex)
                        extras.putInt("com.android.phone.force.slot", targetSlotIndex)

                        if (targetSub != null) {
                            extras.putInt("subscription", targetSub.subscriptionId)
                            extras.putInt("sub_id", targetSub.subscriptionId)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (telecomManager != null) {
                    telecomManager.placeCall(uri, extras)
                    return
                }
            } catch (_: SecurityException) {
                // Fallback
            } catch (_: Exception) {
                // Fallback
            }
        }

        val intent = if (hasCallPermission) {
            Intent(Intent.ACTION_CALL, uri)
        } else {
            Intent(Intent.ACTION_DIAL, uri)
        }

        if (simSlot != null) {
            val slotIdx = simSlot - 1
            intent.putExtra("com.android.phone.extra.slot", slotIdx)
            intent.putExtra("simSlot", slotIdx)
            intent.putExtra("slot", slotIdx)
            intent.putExtra("sim_slot", slotIdx)
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Не удалось совершить вызов", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendSms(phoneNumber: String) {
        if (phoneNumber.isBlank()) return

        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        val uri = "smsto:$cleanNumber".toUri()
        val intent = Intent(Intent.ACTION_SENDTO, uri)

        try {
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "Не удалось открыть SMS", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun PermissionRequestScreen(
    onRequestPermissions: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = "Телефон",
                tint = SamsungGreen,
                modifier = Modifier.size(72.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Доступ к вызовам и контактам",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Для отображения списка последних вызовов и совершения звонков приложению требуются разрешения.",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRequestPermissions,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SamsungGreen,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(
                    text = "Предоставить разрешения",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
