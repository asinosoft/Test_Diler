package com.asinosoft.cdm.util

import android.os.Build
import androidx.annotation.StringRes
import com.asinosoft.cdm.R

/**
 * OEM-оболочки, где dialer’у обычно нужны доп. разрешения
 * (автозапуск, фон, экран блокировки). Приоритет детекта:
 * Xiaomi → Huawei/Honor → OPPO/OnePlus/Realme → vivo/iQOO.
 */
enum class OemShellGuide {
    NONE,
    MIUI,
    HUAWEI,
    OPPO,
    VIVO;

    val isRequired: Boolean get() = this != NONE

    @get:StringRes
    val titleRes: Int
        get() = when (this) {
            NONE -> error("NONE has no strings")
            MIUI -> R.string.onboarding_miui_title
            HUAWEI -> R.string.onboarding_huawei_title
            OPPO -> R.string.onboarding_oppo_title
            VIVO -> R.string.onboarding_vivo_title
        }

    @get:StringRes
    val subtitleRes: Int
        get() = when (this) {
            NONE -> error("NONE has no strings")
            MIUI -> R.string.onboarding_miui_subtitle
            HUAWEI -> R.string.onboarding_huawei_subtitle
            OPPO -> R.string.onboarding_oppo_subtitle
            VIVO -> R.string.onboarding_vivo_subtitle
        }

    @get:StringRes
    val dialogTitleRes: Int
        get() = when (this) {
            NONE -> error("NONE has no strings")
            MIUI -> R.string.onboarding_miui_dialog_title
            HUAWEI -> R.string.onboarding_huawei_dialog_title
            OPPO -> R.string.onboarding_oppo_dialog_title
            VIVO -> R.string.onboarding_vivo_dialog_title
        }

    @get:StringRes
    val dialogMessageRes: Int
        get() = when (this) {
            NONE -> error("NONE has no strings")
            MIUI -> R.string.onboarding_miui_dialog_message
            HUAWEI -> R.string.onboarding_huawei_dialog_message
            OPPO -> R.string.onboarding_oppo_dialog_message
            VIVO -> R.string.onboarding_vivo_dialog_message
        }
}

object OemShellHelper {

    private val xiaomiBrands = setOf("xiaomi", "redmi", "poco", "blackshark")
    private val huaweiBrands = setOf("huawei", "honor")
    private val oppoBrands = setOf("oppo", "realme", "oneplus")
    private val vivoBrands = setOf("vivo", "iqoo")

    fun detectGuide(): OemShellGuide {
        if (isMiuiFamily()) return OemShellGuide.MIUI
        if (isHuaweiFamily()) return OemShellGuide.HUAWEI
        if (isOppoFamily()) return OemShellGuide.OPPO
        if (isVivoFamily()) return OemShellGuide.VIVO
        return OemShellGuide.NONE
    }

    private fun isMiuiFamily(): Boolean {
        if (!systemProperty("ro.miui.ui.version.name").isNullOrBlank()) return true
        if (!systemProperty("ro.miui.ui.version.code").isNullOrBlank()) return true
        if (!systemProperty("ro.mi.os.version.name").isNullOrBlank()) return true
        return matchesBrand(xiaomiBrands)
    }

    private fun isHuaweiFamily(): Boolean {
        if (!systemProperty("ro.build.version.emui").isNullOrBlank()) return true
        if (!systemProperty("ro.build.version.magic").isNullOrBlank()) return true
        if (!systemProperty("hw_sc.build.platform.version").isNullOrBlank()) return true
        val harmony = systemProperty("ro.build.version.harmony")
            ?: systemProperty("ro.huawei.build.display.id")
        if (!harmony.isNullOrBlank()) return true
        return matchesBrand(huaweiBrands)
    }

    private fun isOppoFamily(): Boolean {
        if (!systemProperty("ro.oppo.theme.version").isNullOrBlank()) return true
        if (!systemProperty("ro.build.version.opporom").isNullOrBlank()) return true
        if (!systemProperty("ro.oxygen.version").isNullOrBlank()) return true
        if (!systemProperty("ro.build.version.oplusrom").isNullOrBlank()) return true
        if (!systemProperty("ro.realme.version").isNullOrBlank()) return true
        return matchesBrand(oppoBrands)
    }

    private fun isVivoFamily(): Boolean {
        if (!systemProperty("ro.vivo.os.version").isNullOrBlank()) return true
        if (!systemProperty("ro.vivo.os.build.display.id").isNullOrBlank()) return true
        if (!systemProperty("ro.iqoo.product.origin").isNullOrBlank()) return true
        return matchesBrand(vivoBrands)
    }

    private fun matchesBrand(brands: Set<String>): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer in brands || brand in brands
    }

    private fun systemProperty(key: String): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java)
            (get.invoke(null, key) as? String)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }
}
