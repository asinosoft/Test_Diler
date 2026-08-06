package com.example.test_dialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.test_dialer.ui.recents.RecentsScreen
import com.example.test_dialer.ui.recents.RecentsViewModel
import com.example.test_dialer.ui.theme.SamsungGreen
import com.example.test_dialer.ui.theme.Test_dialerTheme

class MainActivity : ComponentActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CALL_PHONE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Test_dialerTheme {
                val viewModel: RecentsViewModel = viewModel()
                var isPermissionsGranted by remember { mutableStateOf(false) }

                val roleLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) {
                    viewModel.loadCallLogs()
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val allGranted = requiredPermissions.all { perm ->
                        permissions[perm] == true || ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
                    }
                    isPermissionsGranted = allGranted
                    if (allGranted) {
                        requestDefaultDialerRole(roleLauncher)
                        viewModel.loadCallLogs()
                    }
                }

                LaunchedEffect(Unit) {
                    val allGranted = requiredPermissions.all { perm ->
                        ContextCompat.checkSelfPermission(this@MainActivity, perm) == PackageManager.PERMISSION_GRANTED
                    }
                    if (allGranted) {
                        isPermissionsGranted = true
                        requestDefaultDialerRole(roleLauncher)
                        viewModel.loadCallLogs()
                    } else {
                        permissionLauncher.launch(requiredPermissions)
                    }
                }

                if (isPermissionsGranted) {
                    RecentsScreen(
                        viewModel = viewModel,
                        onCall = { number -> makeCall(number) },
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

    private fun requestDefaultDialerRole(launcher: androidx.activity.result.ActivityResultLauncher<Intent>) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER) && !roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    launcher.launch(intent)
                }
            } else {
                val telecomManager = getSystemService(TELECOM_SERVICE) as? TelecomManager
                if (telecomManager != null && packageName != telecomManager.defaultDialerPackage) {
                    val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
                        putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
                    }
                    launcher.launch(intent)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun makeCall(phoneNumber: String) {
        if (phoneNumber.isBlank()) return

        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        val uri = Uri.parse("tel:$cleanNumber")

        val hasCallPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCallPermission) {
            try {
                val telecomManager = getSystemService(TELECOM_SERVICE) as? TelecomManager
                if (telecomManager != null) {
                    telecomManager.placeCall(uri, Bundle())
                    return
                }
            } catch (e: SecurityException) {
                // Fallback
            } catch (e: Exception) {
                // Fallback
            }
        }

        val intent = if (hasCallPermission) {
            Intent(Intent.ACTION_CALL, uri)
        } else {
            Intent(Intent.ACTION_DIAL, uri)
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось совершить вызов", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendSms(phoneNumber: String) {
        if (phoneNumber.isBlank()) return

        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        val uri = Uri.parse("smsto:$cleanNumber")
        val intent = Intent(Intent.ACTION_SENDTO, uri)

        try {
            startActivity(intent)
        } catch (e: Exception) {
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
