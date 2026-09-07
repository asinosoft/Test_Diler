package com.asinosoft.dialer.data.model

/**
 * Вариант отображения избранных контактов на главном экране.
 */
enum class FavoritesViewMode(val storageKey: String, val title: String) {
    /** Сетка 3 колонки (квадратиками) */
    GRID("grid", "Сетка (квадраты)"),

    /** Список карточек со свайпами (как в журнале звонков) */
    LIST("list", "Список (карточки)");

    companion object {
        fun fromStorageKey(key: String?): FavoritesViewMode =
            entries.find { it.storageKey == key } ?: GRID
    }
}
