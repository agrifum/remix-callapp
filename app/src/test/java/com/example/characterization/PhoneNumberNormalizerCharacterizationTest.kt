package com.example.characterization

import com.example.core.phone.PhoneNumberNormalizer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterization tests for [PhoneNumberNormalizer].
 * Covers Polish number variants, prefix handling, sanitization of formatting characters,
 * international numbers, edge cases, and display formatting.
 */
class PhoneNumberNormalizerCharacterizationTest {

    @Test
    fun normalizeKey_polishNumberWithPlus48AndSpaces_normalizesToPlus48Standard() {
        val result = PhoneNumberNormalizer.normalizeKey("+48 501 234 567")
        assertEquals("+48501234567", result)
    }

    @Test
    fun normalizeKey_polishNumberWithDoubleZero48Prefix_replacesWithPlus48() {
        val result = PhoneNumberNormalizer.normalizeKey("0048 501234567")
        assertEquals("+48501234567", result)
    }

    @Test
    fun normalizeKey_nineDigitPolishNumberWithoutPrefix_prependsPlus48() {
        val result = PhoneNumberNormalizer.normalizeKey("501234567")
        assertEquals("+48501234567", result)
    }

    @Test
    fun normalizeKey_elevenDigitPolishNumberStartingWith48_prependsPlus() {
        val result = PhoneNumberNormalizer.normalizeKey("48501234567")
        assertEquals("+48501234567", result)
    }

    @Test
    fun normalizeKey_canonicalFormatsAllYieldIdenticalKey() {
        val canonical = "+48501234567"
        val variations = listOf(
            "+48 501 234 567",
            "0048 501234567",
            "501234567",
            "48501234567",
            "+48 501-234-567",
            "+48 (501) 234 567",
            "501.234.567",
            " 0048 501 234 567 "
        )
        for (v in variations) {
            assertEquals("Variation '$v' must yield canonical key", canonical, PhoneNumberNormalizer.normalizeKey(v))
        }
    }

    @Test
    fun normalizeKey_stripsWhitespacePunctuationAndSlashes() {
        val raw = " (501) . 234 - 567 / "
        assertEquals("+48501234567", PhoneNumberNormalizer.normalizeKey(raw))
    }

    @Test
    fun normalizeKey_nullEmptyOrWhitespace_returnsEmptyString() {
        assertEquals("", PhoneNumberNormalizer.normalizeKey(null))
        assertEquals("", PhoneNumberNormalizer.normalizeKey(""))
        assertEquals("", PhoneNumberNormalizer.normalizeKey("   "))
        assertEquals("", PhoneNumberNormalizer.normalizeKey("\t\n  "))
    }

    @Test
    fun normalizeKey_internationalNumberWithLeadingPlus_retainsPlusAndDigits() {
        val result = PhoneNumberNormalizer.normalizeKey("+49 170 123 4567")
        assertEquals("+491701234567", result)
    }

    @Test
    fun normalizeKey_internationalNumberWithDoubleZeroPrefix_replacesWithPlus() {
        val result = PhoneNumberNormalizer.normalizeKey("0044 20 7946 0919")
        assertEquals("+442079460919", result)
    }

    @Test
    fun normalizeKey_nonStandardShortString_returnsCleanedString() {
        assertEquals("123", PhoneNumberNormalizer.normalizeKey("123"))
        assertEquals("alarm", PhoneNumberNormalizer.normalizeKey("alarm"))
    }

    @Test
    fun formatDisplay_twelveCharPolishNumber_formatsWithSpaces() {
        val result = PhoneNumberNormalizer.formatDisplay("+48501234567")
        assertEquals("+48 501 234 567", result)
    }

    @Test
    fun formatDisplay_nineDigitRawPolishNumber_formatsWithPlus48AndSpaces() {
        val result = PhoneNumberNormalizer.formatDisplay("501234567")
        assertEquals("+48 501 234 567", result)
    }

    @Test
    fun formatDisplay_internationalNumber_returnsNormalizedKey() {
        val result = PhoneNumberNormalizer.formatDisplay("+49 170 1234567")
        assertEquals("+491701234567", result)
    }

    @Test
    fun formatDisplay_nullOrBlank_returnsOriginalOrEmpty() {
        assertEquals("", PhoneNumberNormalizer.formatDisplay(null))
        assertEquals("", PhoneNumberNormalizer.formatDisplay(""))
        assertEquals("   ", PhoneNumberNormalizer.formatDisplay("   "))
    }
}