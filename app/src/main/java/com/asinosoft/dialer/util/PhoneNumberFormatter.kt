package com.asinosoft.dialer.util

fun formatPhoneNumber(rawNumber: String): String {
    if (rawNumber.isBlank()) return rawNumber

    val cleanDigits = buildString(rawNumber.length) {
        for (c in rawNumber) {
            if (c.isDigit()) append(c)
        }
    }

    if (cleanDigits.length == 11 && (cleanDigits.startsWith("7") || cleanDigits.startsWith("8"))) {
        val code = cleanDigits.substring(1, 4)
        val part1 = cleanDigits.substring(4, 7)
        val part2 = cleanDigits.substring(7, 9)
        val part3 = cleanDigits.substring(9, 11)
        return "+7 $code $part1-$part2-$part3"
    }

    if (cleanDigits.length == 10) {
        val code = cleanDigits.substring(0, 3)
        val part1 = cleanDigits.substring(3, 6)
        val part2 = cleanDigits.substring(6, 8)
        val part3 = cleanDigits.substring(8, 10)
        return "+7 $code $part1-$part2-$part3"
    }

    return rawNumber.trim()
}
