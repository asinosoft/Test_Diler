package com.asinosoft.dialer.data.model

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.Call
import android.telephony.SubscriptionManager
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
                    val subManager =
                        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager

                    @Suppress("MissingPermission")
                    val activeList = subManager?.activeSubscriptionInfoList

                    if (!activeList.isNullOrEmpty()) {
                        if (activeList.size == 1) {
                            return activeList[0].simSlotIndex + 1
                        }

                        for (info in activeList) {
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