package com.example.test_dialer.data.model

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED
}

data class CallLogItem(
    val id: String,
    val number: String,
    val name: String?,
    val photoUri: String?,
    val type: CallType,
    val timestamp: Long,
    val duration: Long,
    val count: Int = 1,
    val simNumber: Int = 1
)
