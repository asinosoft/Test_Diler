package com.example.test_dialer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.test_dialer.ui.recents.RecentsScreen
import com.example.test_dialer.ui.recents.RecentsViewModel
import com.example.test_dialer.ui.theme.Test_dialerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            Test_dialerTheme {
                val viewModel: RecentsViewModel = viewModel()

                // Permission launcher for READ_CALL_LOG & CALL_PHONE
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val callLogGranted = permissions[Manifest.permission.READ_CALL_LOG] ?: false
                    if (callLogGranted) {
                        viewModel.loadCallLogs()
                    }
                }

                LaunchedEffect(Unit) {
                    val hasCallLogPermission = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.READ_CALL_LOG
                    ) == PackageManager.PERMISSION_GRANTED

                    val hasCallPermission = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.CALL_PHONE
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!hasCallLogPermission || !hasCallPermission) {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CALL_LOG,
                                Manifest.permission.CALL_PHONE
                            )
                        )
                    } else {
                        viewModel.loadCallLogs()
                    }
                }

                RecentsScreen(
                    viewModel = viewModel,
                    onCall = { number -> makeCall(number) },
                    onSms = { number -> sendSms(number) }
                )
            }
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
