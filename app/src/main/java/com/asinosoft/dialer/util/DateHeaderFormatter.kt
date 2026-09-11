package com.asinosoft.dialer.util

import android.content.Context
import android.text.format.DateUtils
import com.asinosoft.dialer.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateHeaderFormatter {

    fun formatDateHeader(context: Context, timestamp: Long): String {
        if (timestamp == 0L) return ""
        if (DateUtils.isToday(timestamp)) return context.getString(R.string.date_today)
        if (DateUtils.isToday(timestamp + 24 * 3600 * 1000L)) {
            return context.getString(R.string.date_yesterday)
        }

        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val monthRes = monthGenitiveRes(cal.get(Calendar.MONTH))
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val monthName = context.getString(monthRes)
        val dateStr = "$day $monthName"
        val shortWeekday = formatShortWeekday(context, timestamp)

        return if (shortWeekday.isNotEmpty()) "$dateStr, $shortWeekday" else dateStr
    }

    private fun formatShortWeekday(context: Context, timestamp: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val res = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> R.string.weekday_mon
            Calendar.TUESDAY -> R.string.weekday_tue
            Calendar.WEDNESDAY -> R.string.weekday_wed
            Calendar.THURSDAY -> R.string.weekday_thu
            Calendar.FRIDAY -> R.string.weekday_fri
            Calendar.SATURDAY -> R.string.weekday_sat
            Calendar.SUNDAY -> R.string.weekday_sun
            else -> return ""
        }
        return context.getString(res)
    }

    fun monthGenitiveName(context: Context, month1to12: Int): String {
        if (month1to12 !in 1..12) return ""
        return context.getString(monthGenitiveRes(month1to12 - 1))
    }

    fun monthGenitiveNames(context: Context): Array<String> =
        Array(12) { i -> context.getString(monthGenitiveRes(i)) }

    private fun monthGenitiveRes(monthIndex: Int): Int = when (monthIndex) {
        Calendar.JANUARY -> R.string.month_genitive_1
        Calendar.FEBRUARY -> R.string.month_genitive_2
        Calendar.MARCH -> R.string.month_genitive_3
        Calendar.APRIL -> R.string.month_genitive_4
        Calendar.MAY -> R.string.month_genitive_5
        Calendar.JUNE -> R.string.month_genitive_6
        Calendar.JULY -> R.string.month_genitive_7
        Calendar.AUGUST -> R.string.month_genitive_8
        Calendar.SEPTEMBER -> R.string.month_genitive_9
        Calendar.OCTOBER -> R.string.month_genitive_10
        Calendar.NOVEMBER -> R.string.month_genitive_11
        Calendar.DECEMBER -> R.string.month_genitive_12
        else -> R.string.month_genitive_1
    }
}
