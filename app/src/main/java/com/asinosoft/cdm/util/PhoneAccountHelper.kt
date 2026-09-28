package com.asinosoft.cdm.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.PhoneAccountHandle
import android.telecom.PhoneAccountSuggestion
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

data class SelectablePhoneAccount(
    val handle: PhoneAccountHandle,
    val simNumber: Int,
    val label: String
)

class PhoneAccountHelper(private val context: Context) {
    fun getSelectableAccounts(call: Call?): List<SelectablePhoneAccount> {
        val handles = getSuggestedHandles(call)
        val subscriptions = getActiveSubscriptions()

        return handles.map { handle ->
            val simNumber = getSimNumberFromHandle(handle, subscriptions)
            SelectablePhoneAccount(
                handle = handle,
                simNumber = simNumber,
                label = getAccountLabel(handle).ifBlank { "SIM $simNumber" }
            )
        }
    }

    fun autoSelectIfSingle(call: Call): Boolean {
        if (call.state != Call.STATE_SELECT_PHONE_ACCOUNT) return false
        val handles = getSuggestedHandles(call)
        if (handles.size != 1) return false
        return try {
            call.phoneAccountSelected(handles.first(), false)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getSuggestedHandles(call: Call?): List<PhoneAccountHandle> {
        val details = call?.details
        val fromIntent = readSuggestedAccounts(details?.intentExtras)
        if (fromIntent.isNotEmpty()) {
            return fromIntent
        }
        val fromExtras = readSuggestedAccounts(details?.extras)
        if (fromExtras.isNotEmpty()) {
            return fromExtras
        }
        return getCallCapableHandles()
    }

    private fun readSuggestedAccounts(bundle: Bundle?): List<PhoneAccountHandle> {
        if (bundle == null) return emptyList()
        val list =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getParcelableArrayList(
                    Call.EXTRA_SUGGESTED_PHONE_ACCOUNTS,
                    PhoneAccountSuggestion::class.java
                )?.map { it.phoneAccountHandle }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                bundle.getParcelableArrayList<PhoneAccountSuggestion>(Call.EXTRA_SUGGESTED_PHONE_ACCOUNTS)
                    ?.map { it.phoneAccountHandle }
            } else {
                null
            }
        return list.orEmpty()
    }

    private fun getCallCapableHandles(): List<PhoneAccountHandle> {
        if (!canReadPhoneState()) return emptyList()
        return try {
            val telecomManager =
                context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    ?: return emptyList()
            @Suppress("MissingPermission")
            telecomManager.callCapablePhoneAccounts.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun getAccountLabel(handle: PhoneAccountHandle): String {
        if (!canReadPhoneState()) return ""
        return try {
            val telecomManager =
                context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    ?: return ""
            @Suppress("MissingPermission")
            telecomManager.getPhoneAccount(handle)?.label?.toString().orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    private fun getActiveSubscriptions(): List<SubscriptionInfo> {
        if (!canReadPhoneState()) return listOf()

        val subscriptionManager =
            context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        @Suppress("MissingPermission")
        return subscriptionManager?.activeSubscriptionInfoList ?: listOf()
    }

    private fun getSimNumberFromHandle(
        handle: PhoneAccountHandle,
        subscriptions: List<SubscriptionInfo>
    ): Int {
        val telephonyManager =
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val accountId =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                @Suppress("MissingPermission")
                telephonyManager?.getSubscriptionId(handle).toString()
            else
                handle.id
        try {
            if (subscriptions.size == 1) {
                return subscriptions[0].simSlotIndex + 1
            }
            for (info in subscriptions) {
                val subId = info.subscriptionId.toString()
                val slotIndex = info.simSlotIndex
                val iccId = info.iccId.orEmpty()
                if (accountId == subId || accountId == "sub_$subId") {
                    return slotIndex + 1
                }
                if (iccId.isNotBlank() && accountId.contains(iccId)) {
                    return slotIndex + 1
                }
                if (accountId == slotIndex.toString() ||
                    accountId.endsWith(":$slotIndex") ||
                    accountId.endsWith("_$slotIndex") ||
                    accountId.contains("slot$slotIndex", ignoreCase = true) ||
                    accountId.contains("sim${slotIndex + 1}", ignoreCase = true)
                ) {
                    return slotIndex + 1
                }
            }
        } catch (_: Exception) {
            // ignore
        }
        val cleanId = accountId.lowercase().trim()
        if (cleanId.contains("sim2") || cleanId.contains("slot1") || cleanId.contains("sub2") ||
            cleanId.endsWith("_1") || cleanId.endsWith(":1")
        ) {
            return 2
        }
        if (cleanId.contains("sim1") || cleanId.contains("slot0") || cleanId.contains("sub1") ||
            cleanId.endsWith("_0") || cleanId.endsWith(":0")
        ) {
            return 1
        }
        return 1
    }

    private fun canReadPhoneState(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }
}
