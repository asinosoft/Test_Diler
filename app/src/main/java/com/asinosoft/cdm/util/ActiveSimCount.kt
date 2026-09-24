package com.asinosoft.cdm.util

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live count of active SIMs for UI (icons, dual-SIM actions).
 * Observes subscription changes and SIM insert/remove while the app is running.
 */
object ActiveSimCount {
    private val _count = MutableStateFlow(1)
    val count: StateFlow<Int> = _count.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val refreshRetries = listOf(0L, 400L, 1_200L, 3_000L)

    @Volatile
    private var observing = false

    private var appContext: Context? = null
    private var subscriptionManager: SubscriptionManager? = null
    private var subscriptionsListener: SubscriptionManager.OnSubscriptionsChangedListener? = null
    private var simReceiver: BroadcastReceiver? = null

    fun read(context: Context): Int {
        return try {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_PHONE_STATE
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) return 1

            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? SubscriptionManager
                ?: return 1

            @Suppress("MissingPermission")
            val fromList = sm.activeSubscriptionInfoList?.size
            @Suppress("MissingPermission")
            val fromCount = sm.activeSubscriptionInfoCount
            val raw = maxOf(fromList ?: 0, fromCount)
            if (raw > 1) raw else 1
        } catch (_: Exception) {
            1
        }
    }

    /** Start process-wide observation (safe to call repeatedly). */
    fun ensureObserving(context: Context) {
        if (observing) {
            refreshNow(context)
            return
        }
        synchronized(this) {
            if (observing) {
                refreshNow(context)
                return
            }
            val app = context.applicationContext
            appContext = app
            observing = true
            registerListeners(app)
            refreshNow(app)
        }
    }

    private fun registerListeners(app: Context) {
        val sm = app.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        subscriptionManager = sm

        if (sm != null) {
            val listener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
                override fun onSubscriptionsChanged() {
                    scheduleRefresh()
                }
            }
            subscriptionsListener = listener
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                sm.addOnSubscriptionsChangedListener(
                    ContextCompat.getMainExecutor(app),
                    listener
                )
            } else {
                // Pre-R API: only non-Executor overload exists.
                @Suppress("DEPRECATION")
                sm.addOnSubscriptionsChangedListener(listener)
            }
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                scheduleRefresh()
            }
        }
        simReceiver = receiver
        val filter = IntentFilter().apply {
            addAction("android.intent.action.SIM_STATE_CHANGED")
            addAction("android.telephony.action.SIM_CARD_STATE_CHANGED")
            addAction("android.telephony.action.MULTI_SIM_CONFIG_CHANGED")
        }
        // Telephony broadcasts come from the phone UID, not system UID —
        // RECEIVER_NOT_EXPORTED drops them on Android 13+.
        ContextCompat.registerReceiver(
            app,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    private fun scheduleRefresh() {
        val ctx = appContext ?: return
        refreshRetries.forEach { delayMs ->
            mainHandler.postDelayed({ refreshNow(ctx) }, delayMs)
        }
    }

    fun refreshNow(context: Context) {
        val next = read(context)
        if (_count.value != next) {
            _count.value = next
        }
    }
}

/** Active SIM count that updates when a SIM is inserted or removed. */
@Composable
fun rememberActiveSimCount(): Int {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(context.applicationContext) {
        ActiveSimCount.ensureObserving(context)
        onDispose { }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ActiveSimCount.refreshNow(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val count by ActiveSimCount.count.collectAsState(initial = ActiveSimCount.read(context))
    return count
}
