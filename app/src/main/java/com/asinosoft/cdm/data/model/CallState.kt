package com.asinosoft.cdm.data.model

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.Call
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

data class CallState(
    val state: Int,
    val rawNumber: String,
    val displayName: String,
    val connectTimeMillis: Long?,
    val simNumber: Int
) {
    companion object {
        fun fromSystemCall(call: Call, context: Context) = CallState(
            state = call.state,
            rawNumber = call.details?.handle?.schemeSpecificPart ?: "",
            displayName = call.details?.callerDisplayName ?: call.details?.handle?.schemeSpecificPart ?: "",
            connectTimeMillis = call.details?.connectTimeMillis,
            simNumber = getSimNumberFromCall(call, context)
        )

        private fun getSimNumberFromCall(call: Call?, context: Context): Int {
            if (call == null) return 1
            val details = call.details ?: return 1
            val accountHandle = details.accountHandle ?: return 1
            val accountId = accountHandle.id ?: return 1

            try {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    val telecomManager =
                        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

                    @Suppress("MissingPermission")
                    val phoneAccountHandles = telecomManager?.callCapablePhoneAccounts

                    if (!phoneAccountHandles.isNullOrEmpty()) {
                        return phoneAccountHandles.indexOfFirst { it.id == accountId }.coerceAtLeast(0) + 1
                    }
                }
            } catch (_: Exception) {
                // ignore
            }

            val cleanId = accountId.lowercase().trim()
            if (cleanId.contains("sim2") || cleanId.contains("slot1") || cleanId.contains("sub2") || cleanId.endsWith(
                    "_1"
                ) || cleanId.endsWith(":1")
            ) {
                return 2
            }
            return 1
        }
    }
}