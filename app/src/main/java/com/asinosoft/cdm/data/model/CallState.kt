package com.asinosoft.cdm.data.model

import android.content.Context
import android.telecom.Call
import com.asinosoft.cdm.util.SimCardHelper

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
            simNumber = SimCardHelper(context).getSimNumber(call.details?.accountHandle?.id)
        )
    }
}