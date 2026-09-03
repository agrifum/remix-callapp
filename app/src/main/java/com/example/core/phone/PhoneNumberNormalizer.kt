package com.example.core.phone

object PhoneNumberNormalizer {

    fun normalizeKey(rawPhone: String?): String {
        if (rawPhone.isNullOrBlank()) return ""
        val cleaned = rawPhone.trim().replace(Regex("[\\s\\-\\.\\(\\)/]"), "")
        if (cleaned.isEmpty()) return ""

        return when {
            cleaned.startsWith("0048") -> "+48" + cleaned.removePrefix("0048")
            cleaned.startsWith("+48") -> cleaned
            cleaned.startsWith("48") && cleaned.length == 11 -> "+$cleaned"
            cleaned.length == 9 && cleaned.all { it.isDigit() } -> "+48$cleaned"
            cleaned.startsWith("+") -> cleaned
            cleaned.startsWith("00") -> "+" + cleaned.removePrefix("00")
            else -> cleaned
        }
    }

    fun formatDisplay(rawOrKey: String?): String {
        val key = normalizeKey(rawOrKey)
        if (key.isBlank()) return rawOrKey.orEmpty()

        if (key.startsWith("+48") && key.length == 12) {
            val digits = key.removePrefix("+48")
            return "+48 ${digits.substring(0, 3)} ${digits.substring(3, 6)} ${digits.substring(6, 9)}"
        }
        return key
    }
}
