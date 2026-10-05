package com.asinosoft.cdm.util

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
}

object OemShellHelper {

    private val xiaomiBrands = setOf("xiaomi", "redmi", "poco", "blackshark")
    private val huaweiBrands = setOf("huawei", "honor")
    private val oppoBrands = setOf("oppo", "realme", "oneplus")
    private val vivoBrands = setOf("vivo", "iqoo")

    private val autostartComponents = mapOf(
        OemShellGuide.MIUI to listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity"
        ),
        OemShellGuide.HUAWEI to listOf(
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
            "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
        ),
        OemShellGuide.OPPO to listOf(
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oplus.safecenter" to "com.oplus.safecenter.startupapp.StartupAppListActivity"
        ),
        OemShellGuide.VIVO to listOf(
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"
        )
    )

    /** Opens the OEM autostart screen, falling back to the app details page. */
    fun openAutostartSettings(context: Context) {
        openFirst(
            context,
            autostartComponents[detectGuide()].orEmpty().map { (pkg, cls) ->
                appList(context, Intent().setComponent(ComponentName(pkg, cls)))
            } + appPage(appDetailsIntent(context))
        )
    }

    /** MIUI "Other permissions" editor (lock screen, background pop-ups); overlay permission elsewhere. */
    fun openSpecialPermissionsSettings(context: Context) {
        val pkgUri = Uri.fromParts("package", context.packageName, null)
        val miui: List<SettingsTarget> = if (detectGuide() == OemShellGuide.MIUI) listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                .putExtra("extra_pkgname", context.packageName),
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
                .putExtra("extra_pkgname", context.packageName)
        ).map(::appPage) else emptyList()
        val overlay = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
        // Android 11+ ignores the package URI and shows the full list.
        val overlayTarget = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            appList(context, overlay)
        } else {
            appPage(overlay)
        }
        openFirst(context, miui + overlayTarget + appPage(appDetailsIntent(context)))
    }

    /** OEM per-app battery screen; system battery optimization list elsewhere. */
    fun openBatterySettings(context: Context) {
        val oem: List<SettingsTarget> = when (detectGuide()) {
            OemShellGuide.MIUI -> listOf(
                appPage(
                    Intent()
                        .setClassName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                        .putExtra("package_name", context.packageName)
                        .putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager).toString())
                )
            )
            OemShellGuide.OPPO -> listOf(appPage(appDetailsIntent(context)))
            OemShellGuide.VIVO -> listOf(
                appList(context, Intent().setClassName("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity")),
                appList(context, Intent().setClassName("com.iqoo.powersaving", "com.iqoo.powersaving.PowerSavingManagerActivity"))
            )
            else -> emptyList()
        }
        openFirst(
            context,
            oem +
                appList(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) +
                appPage(appDetailsIntent(context))
        )
    }

    fun isHyperOs(): Boolean = !systemProperty("ro.mi.os.version.name").isNullOrBlank()

    private fun appDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    /** A settings screen; [isAppList] means the user still has to find this app in a list. */
    private class SettingsTarget(val intent: Intent, val isAppList: Boolean)

    private fun appPage(intent: Intent) = SettingsTarget(intent, isAppList = false)

    /** Undocumented AOSP Settings extras: scroll to and highlight this app's row where supported. */
    private fun appList(context: Context, intent: Intent) = SettingsTarget(
        intent
            .putExtra(EXTRA_FRAGMENT_ARG_KEY, context.packageName)
            .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, context.packageName) }),
        isAppList = true
    )

    private fun openFirst(context: Context, targets: List<SettingsTarget>) {
        val opened = targets.firstOrNull { tryStart(context, it.intent) } ?: return
        if (opened.isAppList) SettingsHintOverlay.show(context)
    }

    private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

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
        if (!systemProperty("ro.build.version.harmony").isNullOrBlank()) return true
        if (!systemProperty("ro.huawei.build.display.id").isNullOrBlank()) return true
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
