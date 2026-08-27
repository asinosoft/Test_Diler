package com.asinosoft.dialer.data.model

/**
 * Как открывать номеронабиратель с главного экрана.
 */
enum class DialerOpenMode(val storageKey: String) {
    /** Только FAB-кнопка */
    BUTTON("button"),

    /** FAB и двойной тап по экрану */
    BUTTON_AND_DOUBLE_TAP("button_and_double_tap"),

    /** Только двойной тап по экрану */
    DOUBLE_TAP("double_tap");

    val showsFab: Boolean
        get() = this == BUTTON || this == BUTTON_AND_DOUBLE_TAP

    val allowsDoubleTap: Boolean
        get() = this == DOUBLE_TAP || this == BUTTON_AND_DOUBLE_TAP

    companion object {
        fun fromStorageKey(key: String?): DialerOpenMode =
            entries.find { it.storageKey == key } ?: BUTTON_AND_DOUBLE_TAP
    }
}
