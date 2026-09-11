package com.asinosoft.dialer.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.asinosoft.dialer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AboutSupportHelper {

    private const val SUPPORT_EMAIL = "cdm.asinosoft@gmail.com"
    private const val PLAY_STORE_PACKAGE = "com.asinosoft.cdm"
    const val PRIVACY_POLICY_URL = "https://asinosoft.ru/cdm_privacy_policy.html"

    fun openPlayStoreListing(context: Context) {
        try {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    "market://details?id=$PLAY_STORE_PACKAGE".toUri()
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://play.google.com/store/apps/details?id=$PLAY_STORE_PACKAGE".toUri()
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.error_open_play), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun shareApp(context: Context, appName: String) {
        val playUrl = "https://play.google.com/store/apps/details?id=$PLAY_STORE_PACKAGE"
        val text = context.getString(R.string.about_share_message, appName, playUrl)
        try {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, appName)
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    context.getString(R.string.about_share_chooser)
                )
            )
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.error_share), Toast.LENGTH_SHORT).show()
        }
    }

    fun openPrivacyPolicy(context: Context) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(R.string.error_open_link), Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun openSupportEmail(context: Context, appName: String) = withContext(Dispatchers.IO) {
        val reportFile = buildSupportReportFile(context, appName)
        withContext(Dispatchers.Main) {
            val subject = context.getString(R.string.about_email_subject, appName)
            val body = context.getString(R.string.about_email_body)

            val attachmentUri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    reportFile
                )
            } catch (_: Exception) {
                null
            }

            val opened = startEmailWithAttachment(
                context = context,
                subject = subject,
                body = body,
                attachmentUri = attachmentUri
            ) || startEmailComposeOnly(
                context = context,
                subject = subject,
                body = body
            )

            if (!opened) {
                Toast.makeText(
                    context,
                    context.getString(R.string.error_no_email_app),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun startEmailWithAttachment(
        context: Context,
        subject: String,
        body: String,
        attachmentUri: Uri?
    ): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_SEND, "mailto:$SUPPORT_EMAIL".toUri()).apply {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                attachmentUri?.let {
                    putExtra(Intent.EXTRA_STREAM, attachmentUri)
                    clipData = android.content.ClipData.newRawUri("", attachmentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun startEmailComposeOnly(
        context: Context,
        subject: String,
        body: String
    ): Boolean {
        val mailtoUri = (
            "mailto:$SUPPORT_EMAIL" +
                "?subject=${Uri.encode(subject)}" +
                "&body=${Uri.encode(body)}"
        ).toUri()

        // 1) SENDTO + mailto
        try {
            context.startActivity(
            Intent(Intent.ACTION_SENDTO, mailtoUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return true
        } catch (_: Exception) {
            // continue
        }

        // 2) VIEW + mailto
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, mailtoUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            return true
        } catch (_: Exception) {
            // continue
        }

        // 3) Явно через известные почтовые клиенты
        val emailPackages = listOf(
            "com.google.android.gm",
            "com.samsung.android.email.provider",
            "com.samsung.android.email.ui",
            "com.microsoft.office.outlook",
            "com.yahoo.mobile.client.android.mail",
            "ru.yandex.mail"
        )
        for (pkg in emailPackages) {
            try {
                val intent = Intent(Intent.ACTION_SENDTO, mailtoUri).apply {
                    setPackage(pkg)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (_: Exception) {
                // try next
            }
        }
        return false
    }

    private fun buildSupportReportFile(context: Context, appName: String): File {
        val dir = File(context.cacheDir, "support/report.txt").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "support_report_$stamp.txt")
        file.writeText(buildSupportReport(context, appName), Charsets.UTF_8)
        return file
    }

    private fun buildSupportReport(context: Context, appName: String): String {
        val versionName = readVersionName(context)
        val versionCode = readVersionCode(context)
        val sb = StringBuilder()
        sb.appendLine(context.getString(R.string.about_report_title))
        sb.appendLine(
            context.getString(
                R.string.about_report_date,
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.getDefault()).format(Date())
            )
        )
        sb.appendLine()
        sb.appendLine(context.getString(R.string.about_report_app_section))
        sb.appendLine(context.getString(R.string.about_report_app_name, appName))
        sb.appendLine("Package: ${context.packageName}")
        sb.appendLine("Version name: $versionName")
        sb.appendLine("Version code: $versionCode")
        sb.appendLine()
        sb.appendLine(context.getString(R.string.about_report_device_section))
        sb.appendLine("Manufacturer: ${Build.MANUFACTURER}")
        sb.appendLine("Brand: ${Build.BRAND}")
        sb.appendLine("Model: ${Build.MODEL}")
        sb.appendLine("Device: ${Build.DEVICE}")
        sb.appendLine("Product: ${Build.PRODUCT}")
        sb.appendLine("Hardware: ${Build.HARDWARE}")
        sb.appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Incremental: ${Build.VERSION.INCREMENTAL}")
        sb.appendLine("Display: ${Build.DISPLAY}")
        sb.appendLine("Supported ABIs: ${Build.SUPPORTED_ABIS.joinToString()}")
        sb.appendLine()
        sb.appendLine(context.getString(R.string.about_report_logs_section))
        sb.appendLine(collectRecentLogs(context))
        return sb.toString()
    }

    private fun readVersionName(context: Context): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun readVersionCode(context: Context): Long {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        } catch (_: Exception) {
            -1L
        }
    }

    private fun collectRecentLogs(context: Context): String {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-t", "400", "*:W")
            )
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText()
            }
            process.waitFor()
            if (output.isBlank()) {
                context.getString(R.string.about_report_logs_empty)
            } else {
                output.takeLast(50_000)
            }
        } catch (e: Exception) {
            context.getString(R.string.about_report_logs_error, e.message ?: "")
        }
    }
}
