package com.asinosoft.dialer.data.model

import androidx.annotation.StringRes
import com.asinosoft.dialer.R

/**
 * Display mode for favorite contacts on the home screen.
 */
enum class FavoritesViewMode(val storageKey: String, @StringRes val titleRes: Int) {
    /** 3-column grid */
    GRID("grid", R.string.favorites_view_mode_grid),

    /** List cards with swipes */
    LIST("list", R.string.favorites_view_mode_list);

    companion object {
        fun fromStorageKey(key: String?): FavoritesViewMode =
            entries.find { it.storageKey == key } ?: GRID
    }
}
