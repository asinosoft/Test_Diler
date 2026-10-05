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
import androidx.core.content.ContextCompat

data class SelectablePhoneAccount(
    val handle: PhoneAccountHandle,
    val simNumber: Int,
    val label: String
)

class SimCardHelper(private val context: Context) {
    fun getSelectableAccounts(call: Call?): List<SelectablePhoneAccount> {
        val handles = getSuggestedHandles(call)
        val phoneAccountHandles = getPhoneAccountHandles()

        return handles.map { handle ->
            val simNumber =
                phoneAccountHandles.indexOfFirst { it.id == handle.id }.coerceAtLeast(0) + 1
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


    fun getSimNumber(accountId: String?): Int? =
        getPhoneAccountHandles()
            .indexOfFirst { it.id == accountId }
            .let { if (it == -1) null else it + 1 }

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

    private fun getPhoneAccountHandles(): List<PhoneAccountHandle> {
        if (!canReadPhoneState()) return listOf()

        val telecomManager =
            context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        @Suppress("MissingPermission")
        return telecomManager?.callCapablePhoneAccounts ?: listOf()
    }

    private fun canReadPhoneState(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }
}
