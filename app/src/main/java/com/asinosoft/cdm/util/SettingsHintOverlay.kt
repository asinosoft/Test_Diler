package com.asinosoft.cdm.util

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.asinosoft.cdm.R

/**
 * Short non-touchable hint shown over a system settings list:
 * "Find <app> in the list and turn it on". Requires the overlay permission.
 */
object SettingsHintOverlay {

    private const val SHOW_DELAY_MS = 600L
    private const val DURATION_MS = 7_000L

    private val handler = Handler(Looper.getMainLooper())
    private var current: View? = null

    fun show(context: Context) {
        val app = context.applicationContext
        if (!Settings.canDrawOverlays(app)) return
        hide(app)
        handler.postDelayed({ attach(app) }, SHOW_DELAY_MS)
    }

    fun hide(context: Context) {
        handler.removeCallbacksAndMessages(null)
        val view = current ?: return
        current = null
        runCatching { context.applicationContext.getSystemService(WindowManager::class.java).removeView(view) }
    }

    private fun attach(context: Context) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val label = context.applicationInfo.loadLabel(context.packageManager).toString()
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(18), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(0xF0202124.toInt())
            }
            elevation = dp(6).toFloat()
            addView(
                ImageView(context).apply {
                    setImageDrawable(context.packageManager.getApplicationIcon(context.applicationInfo))
                },
                LinearLayout.LayoutParams(dp(36), dp(36))
            )
            addView(
                TextView(context).apply {
                    text = context.getString(R.string.settings_hint_find_app, label)
                    setTextColor(Color.WHITE)
                    textSize = 15f
                    setPadding(dp(12), 0, 0, 0)
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        val root = FrameLayout(context).apply {
            setPadding(dp(16), 0, dp(16), 0)
            addView(card)
        }

        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = dp(56)
            windowAnimations = android.R.style.Animation_Toast
        }

        val added = runCatching {
            context.getSystemService(WindowManager::class.java).addView(root, params)
        }.isSuccess
        if (!added) return
        current = root
        handler.postDelayed({ hide(context) }, DURATION_MS)
    }
}
