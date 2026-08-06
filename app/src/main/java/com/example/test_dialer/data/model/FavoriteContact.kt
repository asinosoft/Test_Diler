package com.example.test_dialer.data.model

data class FavoriteContact(
    val id: String,
    val name: String,
    val number: String,
    val photoUri: String? = null,
    val order: Int = 0
)
