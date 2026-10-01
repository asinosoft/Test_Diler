package com.asinosoft.cdm.util

import android.content.Context
import android.telephony.TelephonyManager
import android.util.LruCache
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber
import java.util.Locale

object PhoneNumberHelper {
    private val util: PhoneNumberUtil by lazy { PhoneNumberUtil.getInstance() }
    private val formatCache = LruCache<String, String>(512)
    private const val FORMATTABLE_CHARS = "+ -().\u00A0"

    @Volatile
    private var region: String = Locale.getDefault().country.uppercase(Locale.ROOT)

    /** Default region for numbers without a country code: network, then SIM, then locale. */
    fun init(context: Context) {
        val tm = context.getSystemService(TelephonyManager::class.java)
        region = listOfNotNull(tm?.networkCountryIso, tm?.simCountryIso, Locale.getDefault().country)
            .firstOrNull { it.length == 2 }
            ?.uppercase(Locale.ROOT)
            ?: region
        formatCache.evictAll()
    }

    /** Keeps digits and dialer/USSD symbols needed to place a call. */
    fun sanitizeForDial(raw: String): String =
        raw.filter { ch ->
            ch.isDigit() || ch == '+' || ch == '*' || ch == '#' || ch == ',' || ch == ';'
        }

    fun telUri(rawNumber: String): android.net.Uri =
        android.net.Uri.fromParts("tel", sanitizeForDial(rawNumber), null)

    fun parse(text: String): String? {
        val number = text.filter { it.isDigit() || it == '+' }
        if (number.isEmpty()) return null
        return parseValid(number)?.let { util.format(it, PhoneNumberUtil.PhoneNumberFormat.E164) } ?: number
    }

    fun format(rawNumber: String): String {
        if (rawNumber.isBlank()) return rawNumber
        val number = rawNumber.trim()
        if (number.any { !it.isDigit() && it !in FORMATTABLE_CHARS }) return number
        formatCache.get(number)?.let { return it }
        val formatted = parseValid(number)
            ?.let { util.format(it, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL) }
            ?: number
        formatCache.put(number, formatted)
        return formatted
    }

    private fun parseValid(number: String): PhoneNumber? =
        parseValid(number, region) ?: if (isRussianTrunkNumber(number)) parseValid(number, "RU") else null

    private fun parseValid(number: String, region: String): PhoneNumber? = try {
        util.parse(number, region).takeIf { util.isValidNumber(it) }
    } catch (_: Exception) {
        null
    }

    /** 11-digit 7XXXXXXXXXX / 8XXXXXXXXXX saved without '+', common in RU/KZ contacts. */
    private fun isRussianTrunkNumber(number: String): Boolean {
        if (number.startsWith("+")) return false
        val digits = number.filter { it.isDigit() }
        return digits.length == 11 && (digits[0] == '7' || digits[0] == '8')
    }
}
