package com.asinosoft.dialer

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CallLog
import android.provider.Settings
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.asinosoft.dialer.data.model.FavoriteContact
import com.asinosoft.dialer.service.MissedCallNotificationListener
import com.asinosoft.dialer.ui.onboarding.OnboardingFavoritesSetupScreen
import com.asinosoft.dialer.ui.onboarding.OnboardingPermissionStep
import com.asinosoft.dialer.ui.onboarding.OnboardingPermissionsScreen
import com.asinosoft.dialer.ui.recents.RecentsScreen
import com.asinosoft.dialer.ui.recents.RecentsViewModel
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
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current

                val onboardingPrefs = remember {
                    getSharedPreferences("dialer_settings", MODE_PRIVATE)
                }
                var isOnboardingComplete by remember {
                    mutableStateOf(resolveOnboardingComplete(onboardingPrefs))
                }
                var isPermissionsStepDone by remember { mutableStateOf(false) }

                var isRuntimeGranted by remember {
                    mutableStateOf(areRuntimePermissionsGranted())
                }
                var isOverlayGranted by remember {
                    mutableStateOf(Settings.canDrawOverlays(this))
                }
                var highlightedStep by remember {
                    mutableStateOf<OnboardingPermissionStep?>(null)
                }

                val roleLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) {
                    viewModel.loadCallLogs()
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    isRuntimeGranted = areRuntimePermissionsGranted()
                    highlightedStep = if (isRuntimeGranted) {
                        if (!Settings.canDrawOverlays(this)) OnboardingPermissionStep.OVERLAY else null
                    } else {
                        OnboardingPermissionStep.RUNTIME
                    }
                    if (isRuntimeGranted) {
                        viewModel.loadCallLogs()
                    }
                }

                val overlayLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) {
                    isOverlayGranted = Settings.canDrawOverlays(this)
                    highlightedStep = if (isOverlayGranted) null else OnboardingPermissionStep.OVERLAY
                }

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            isRuntimeGranted = areRuntimePermissionsGranted()
                            isOverlayGranted = Settings.canDrawOverlays(this@MainActivity)
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                LaunchedEffect(isOnboardingComplete, isRuntimeGranted) {
                    if (isOnboardingComplete && isRuntimeGranted) {
                        requestDefaultDialerRole(roleLauncher)
                        viewModel.loadCallLogs()
                    }
                }

                LaunchedEffect(intent) {
                    handleContactOpenIntent(intent)
                }

                fun openOverlaySettings() {
                    highlightedStep = OnboardingPermissionStep.OVERLAY
                    Toast.makeText(
                        context,
                        "Включите Contacts Dialer Messages на этом экране",
                        Toast.LENGTH_LONG
                    ).show()
                    // Open the system "Appear on top" list focused on this app when possible.
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    try {
                        overlayLauncher.launch(intent)
                    } catch (_: Exception) {
                        try {
                            overlayLauncher.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                "Не удалось открыть настройки",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                fun requestRuntime() {
                    highlightedStep = OnboardingPermissionStep.RUNTIME
                    permissionLauncher.launch(requiredPermissions)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when {
                        !isOnboardingComplete && !isPermissionsStepDone -> {
                            OnboardingPermissionsScreen(
                                isRuntimeGranted = isRuntimeGranted,
                                isOverlayGranted = isOverlayGranted,
                                highlightedStep = highlightedStep,
                                onRequestRuntimePermissions = { requestRuntime() },
                                onRequestOverlayPermission = { openOverlaySettings() },
                                onContinue = {
                                    if (isRuntimeGranted && isOverlayGranted) {
                                        highlightedStep = null
                                        isPermissionsStepDone = true
                                    }
                                }
                            )
                        }

                        !isOnboardingComplete -> {
                            val favoriteRowsCount by viewModel.currentFavoriteRowsCount.collectAsState()
                            val favoritesViewMode by viewModel.favoritesViewMode.collectAsState()
                            val tabs by viewModel.tabs.collectAsState()

                            OnboardingFavoritesSetupScreen(
                                selectedRowsCount = favoriteRowsCount,
                                favoritesViewMode = favoritesViewMode,
                                maxPossibleRows = 8,
                                tabs = tabs,
                                onRowsCountSelected = { viewModel.setFavoriteRowsCount(it) },
                                onFavoritesViewModeSelected = { viewModel.setFavoritesViewMode(it) },
                                onAddTab = { viewModel.addTab(it) },
                                onRenameTab = { id, name -> viewModel.renameTab(id, name) },
                                onDeleteTab = { viewModel.deleteTab(it) },
                                onReorderTabs = { viewModel.reorderTabs(it) },
                                onFinish = {
                                    onboardingPrefs.edit {
                                        putBoolean(KEY_ONBOARDING_COMPLETE, true)
                                    }
                                    isOnboardingComplete = true
                                    requestDefaultDialerRole(roleLauncher)
                                    viewModel.loadCallLogs()
                                }
                            )
                        }

                        else -> {
                            RecentsScreen(
                                viewModel = viewModel,
                                onCall = { number, simSlot -> makeCall(number, simSlot) },
                                onSms = { number -> sendSms(number) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun areRuntimePermissionsGranted(): Boolean {
        return requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun resolveOnboardingComplete(prefs: android.content.SharedPreferences): Boolean {
        if (prefs.contains(KEY_ONBOARDING_COMPLETE)) {
            return prefs.getBoolean(KEY_ONBOARDING_COMPLETE, false)
        }
        // Existing installs (app update) should not see first-run onboarding again.
        val isExistingInstall = try {
            val info = packageManager.getPackageInfo(packageName, 0)
            info.firstInstallTime != info.lastUpdateTime
        } catch (_: Exception) {
            false
        }
        if (isExistingInstall) {
            prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETE, true) }
            return true
        }
        return false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleContactOpenIntent(intent)
    }

    private fun handleContactOpenIntent(intent: Intent?) {
        if (intent == null) return

        val numberExtra = intent.getStringExtra(EXTRA_OPEN_CONTACT_NUMBER)
        if (!numberExtra.isNullOrBlank()) {
            val name = intent.getStringExtra(EXTRA_OPEN_CONTACT_NAME)
            val id = intent.getStringExtra(EXTRA_OPEN_CONTACT_ID).orEmpty()
            val contact = FavoriteContact(
                id = id,
                name = name ?: numberExtra,
                number = numberExtra,
                photoUri = null
            )
            mainViewModel?.openContactDetail(contact, initialTab = 0)
            return
        }

        val action = intent.action
        val data = intent.data
        if (data != null && (action == Intent.ACTION_DIAL || action == Intent.ACTION_VIEW || action == Intent.ACTION_CALL)) {
            val scheme = data.scheme
            if (scheme == "tel" || scheme == "sip") {
                val rawNumber = data.schemeSpecificPart.orEmpty()
                if (rawNumber.isNotBlank()) {
                    mainViewModel?.openSearchDialer(rawNumber)
                    return
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_CONTACT_NUMBER = "extra_open_contact_number"
        const val EXTRA_OPEN_CONTACT_NAME = "extra_open_contact_name"
        const val EXTRA_OPEN_CONTACT_ID = "extra_open_contact_id"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    }

    override fun onResume() {
        super.onResume()
        clearMissedCallNotifications()
    }

    private fun clearMissedCallNotifications() {
        try {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.cancelAll()
        } catch (_: Exception) {
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val telecomManager = getSystemService(TELECOM_SERVICE) as? TelecomManager
                @Suppress("MissingPermission")
                telecomManager?.cancelMissedCallsNotification()
            }
        } catch (_: Exception) {
        }

        try {
            MissedCallNotificationListener.cancelActiveIfConnected()
        } catch (_: Exception) {
        }

        try {
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
                        val targetSlotIndex = simSlot - 1
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
            } catch (_: Exception) {
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
