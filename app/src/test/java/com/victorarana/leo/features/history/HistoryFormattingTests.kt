package com.victorarana.leo.features.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** Android only: the date and number formats go through ICU skeletons (D3), where iOS uses `Date.FormatStyle`. */
@RunWith(RobolectricTestRunner::class)
class HistoryFormattingTests {
    private val english = Locale.US
    private val spanish = Locale.forLanguageTag("es")
    private val portuguese = Locale.forLanguageTag("pt-BR")
    private val finishedAt = Instant.parse("2026-03-20T09:12:00Z")

    /** ICU writes the space before "AM" as a narrow no-break space, like Foundation. */
    private fun String.plain() = replace(' ', ' ').replace(' ', ' ')

    @Test
    fun monthNamesAreWideAndInTheReadersLanguage() {
        assertEquals("March", monthName(YearMonth.of(2026, 3), showingYear = false, english))
        assertEquals("marzo", monthName(YearMonth.of(2026, 3), showingYear = false, spanish))
        assertEquals("março", monthName(YearMonth.of(2026, 3), showingYear = false, portuguese))
    }

    @Test
    fun monthNameShowsTheYearOnlyWhenAsked() {
        assertFalse(monthName(YearMonth.of(2025, 12), showingYear = false, english).contains("2025"))
        assertEquals("December 2025", monthName(YearMonth.of(2025, 12), showingYear = true, english))
        assertEquals("diciembre de 2025", monthName(YearMonth.of(2025, 12), showingYear = true, spanish))
        assertEquals("dezembro de 2025", monthName(YearMonth.of(2025, 12), showingYear = true, portuguese))
    }

    /** A month is named whatever the device's zone is: no first-day-at-midnight date can slip into the month before. */
    @Test
    fun monthNameDoesNotDependOnTheDefaultTimeZone() {
        val saved = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Lima"))
            assertEquals("February", monthName(YearMonth.of(2026, 2), showingYear = false, english))
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Kiritimati"))
            assertEquals("February", monthName(YearMonth.of(2026, 2), showingYear = false, english))
        } finally {
            java.util.TimeZone.setDefault(saved)
        }
    }

    @Test
    fun rowDateAndAccessibilityDay() {
        assertEquals("Mar 20", rowDate(finishedAt, ZoneOffset.UTC, english))
        assertEquals("March 20", accessibilityDay(finishedAt, ZoneOffset.UTC, english))
        assertTrue(rowDate(finishedAt, ZoneOffset.UTC, spanish).startsWith("20"))
    }

    @Test
    fun timeFollowsTheDevicesClock() {
        assertEquals("9:12 AM", rowTime(finishedAt, ZoneOffset.UTC, english, is24Hour = false).plain())
        assertEquals("09:12", rowTime(finishedAt, ZoneOffset.UTC, english, is24Hour = true))
    }

    /** Rows show the time of the reader's own zone, a day earlier here. */
    @Test
    fun timeAndDateUseTheGivenZone() {
        val lima = ZoneId.of("America/Lima")
        val late = Instant.parse("2026-01-01T03:30:00Z")
        assertEquals("10:30 PM", rowTime(late, lima, english, is24Hour = false).plain())
        assertEquals("Dec 31", rowDate(late, lima, english))
    }

    @Test
    fun theKickerJoinsTheDateAndTheTime() {
        val kicker = roundKicker(finishedAt, ZoneOffset.UTC, english, is24Hour = false).plain()
        assertTrue(kicker, kicker.startsWith("Mar 20"))
        assertTrue(kicker, kicker.endsWith("9:12 AM"))
    }

    @Test
    fun percentIsWholeAndRoundsHalfToEven() {
        assertEquals("0%", percentText(0.0, english))
        assertEquals("78%", percentText(7.0 / 9.0, english))
        assertEquals("100%", percentText(1.0, english))
        assertEquals("78%", percentText(0.785, english))
        assertEquals("80%", percentText(0.795, english))
    }

    @Test
    fun percentFollowsTheLocale() {
        assertEquals("78 %", percentText(7.0 / 9.0, spanish).plain())
        assertEquals("78%", percentText(7.0 / 9.0, portuguese))
    }

    @Test
    fun firstDayOfWeekFollowsTheLocale() {
        assertEquals(DayOfWeek.SUNDAY, localeFirstDayOfWeek(Locale.US))
        assertEquals(DayOfWeek.MONDAY, localeFirstDayOfWeek(Locale.FRANCE))
        assertEquals(DayOfWeek.MONDAY, localeFirstDayOfWeek(spanish))
        // Android 14's "First day of week" setting is a `-u-fw-` extension of the locale.
        assertEquals(DayOfWeek.MONDAY, localeFirstDayOfWeek(Locale.forLanguageTag("en-US-u-fw-mon")))
        assertEquals(DayOfWeek.SATURDAY, localeFirstDayOfWeek(Locale.forLanguageTag("en-US-u-fw-sat")))
    }
}
