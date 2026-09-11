package com.asinosoft.dialer.util

import android.content.Context
import android.provider.ContactsContract
import com.asinosoft.dialer.R
import java.util.Locale

object ContactLabelHelper {

    fun defaultMobileLabel(context: Context): String =
        context.getString(R.string.phone_type_mobile)

    fun phoneFallbackLabel(context: Context): String =
        context.getString(R.string.phone_number)

    fun phoneTypeLabel(context: Context, type: Int, customLabel: String?): String {
        return when (type) {
            ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE ->
                context.getString(R.string.phone_type_mobile)
            ContactsContract.CommonDataKinds.Phone.TYPE_HOME ->
                context.getString(R.string.phone_type_home)
            ContactsContract.CommonDataKinds.Phone.TYPE_WORK ->
                context.getString(R.string.phone_type_work)
            ContactsContract.CommonDataKinds.Phone.TYPE_MAIN ->
                context.getString(R.string.phone_type_main)
            ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK ->
                context.getString(R.string.phone_type_fax_work)
            ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME ->
                context.getString(R.string.phone_type_fax_home)
            ContactsContract.CommonDataKinds.Phone.TYPE_PAGER ->
                context.getString(R.string.phone_type_pager)
            ContactsContract.CommonDataKinds.Phone.TYPE_OTHER ->
                context.getString(R.string.phone_type_other)
            ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM ->
                customLabel ?: context.getString(R.string.phone_type_other)
            else -> context.getString(R.string.phone_type_mobile)
        }
    }

    fun emailTypeLabel(context: Context, type: Int, customLabel: String?): String {
        return when (type) {
            ContactsContract.CommonDataKinds.Email.TYPE_HOME ->
                context.getString(R.string.email_type_personal)
            ContactsContract.CommonDataKinds.Email.TYPE_WORK ->
                context.getString(R.string.email_type_work)
            ContactsContract.CommonDataKinds.Email.TYPE_MOBILE ->
                context.getString(R.string.phone_type_mobile)
            ContactsContract.CommonDataKinds.Email.TYPE_OTHER ->
                context.getString(R.string.email_type_other)
            ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM ->
                customLabel ?: context.getString(R.string.email_type_other)
            else -> context.getString(R.string.email_type_personal)
        }
    }

    fun formatCallDuration(context: Context, seconds: Long): String {
        if (seconds <= 0L) return context.getString(R.string.contact_duration_no_answer)
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) {
            context.getString(R.string.contact_duration_min_sec, m.toInt(), s.toInt())
        } else {
            context.getString(R.string.contact_duration_sec, s.toInt())
        }
    }

    fun formatAge(context: Context, age: Int): String =
        context.resources.getQuantityString(R.plurals.age_years, age, age)

    fun isHomePhoneLabel(context: Context, label: String): Boolean {
        val l = label.lowercase(Locale.getDefault())
        return l == "home" || l == context.getString(R.string.phone_type_home).lowercase(Locale.getDefault())
    }

    fun isWorkPhoneLabel(context: Context, label: String): Boolean {
        val l = label.lowercase(Locale.getDefault())
        return l == "work" || l == context.getString(R.string.phone_type_work).lowercase(Locale.getDefault())
    }
}
