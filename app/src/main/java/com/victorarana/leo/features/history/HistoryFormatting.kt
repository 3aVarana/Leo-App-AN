package com.victorarana.leo.features.history

import android.icu.text.DateFormat
import android.icu.util.Calendar
import android.icu.util.TimeZone
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

// The date and number formats of History and the round page. iOS writes them as `Date.FormatStyle`s
// (`.dateTime.day().month(.abbreviated)`), which Foundation resolves through ICU skeletons; here the same skeletons go
// to ICU directly (D3), so each locale orders and punctuates them its own way.

private const val DAY_AND_ABBREVIATED_MONTH = "MMMd"
private const val DAY_AND_WIDE_MONTH = "MMMMd"
private const val STANDALONE_WIDE_MONTH = "LLLL"
private const val STANDALONE_WIDE_MONTH_AND_YEAR = "LLLLy"
private const val MIDDAY_HOUR = 12
private const val MIDMONTH_DAY = 15
private const val DAYS_IN_WEEK = 7
private const val SUNDAY_TO_MONDAY_BASED = 5

private fun format(
    skeleton: String,
    locale: Locale,
    zone: ZoneId,
    instant: Instant,
): String {
    val formatter = DateFormat.getInstanceForSkeleton(skeleton, locale)
    formatter.timeZone = TimeZone.getTimeZone(zone.id)
    return formatter.format(Date.from(instant))
}

private fun timeSkeleton(is24Hour: Boolean) = if (is24Hour) "Hm" else "hm"

/**
 * A section's month, wide, with the year only when [showingYear] ("March", "December 2025"). A [YearMonth] has no
 * time zone, so the name can't slip into the month before the way a first-day-of-month `Date` can west of UTC (iOS
 * formats it in the calendar's zone for that reason): it is written from midday UTC on the 15th.
 */
fun monthName(
    month: YearMonth,
    showingYear: Boolean,
    locale: Locale,
): String =
    format(
        skeleton = if (showingYear) STANDALONE_WIDE_MONTH_AND_YEAR else STANDALONE_WIDE_MONTH,
        locale = locale,
        zone = ZoneOffset.UTC,
        instant = month.atDay(MIDMONTH_DAY).atTime(MIDDAY_HOUR, 0).toInstant(ZoneOffset.UTC),
    )

/** A row's day and abbreviated month ("Mar 20"). */
fun rowDate(
    instant: Instant,
    zone: ZoneId,
    locale: Locale,
): String = format(DAY_AND_ABBREVIATED_MONTH, locale, zone, instant)

/** A row's hour and minute, in the device's 12 or 24 hour clock ("9:12 AM", "09:12"). */
fun rowTime(
    instant: Instant,
    zone: ZoneId,
    locale: Locale,
    is24Hour: Boolean,
): String = format(timeSkeleton(is24Hour), locale, zone, instant)

/** The day and wide month TalkBack reads ("March 20"). */
fun accessibilityDay(
    instant: Instant,
    zone: ZoneId,
    locale: Locale,
): String = format(DAY_AND_WIDE_MONTH, locale, zone, instant)

/** The round page's date and time, in one phrase ("Mar 20, 9:12 AM"): the first half of its kicker. */
fun roundKicker(
    instant: Instant,
    zone: ZoneId,
    locale: Locale,
    is24Hour: Boolean,
): String = format(DAY_AND_ABBREVIATED_MONTH + timeSkeleton(is24Hour), locale, zone, instant)

/** The locale the screen's resources resolved to, read from the configuration so a change of language recomposes. */
@Composable
internal fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

/** `.percent.precision(.fractionLength(0))`: "78%", whole percents, in [locale]. */
fun percentText(
    share: Double,
    locale: Locale,
): String =
    NumberFormat
        .getPercentInstance(locale)
        .apply { maximumFractionDigits = 0 }
        .format(share)

/** The locale's first weekday (`Calendar.firstWeekday`), which Android 14's "First day of week" setting changes. */
fun localeFirstDayOfWeek(locale: Locale = Locale.getDefault()): DayOfWeek {
    // `Calendar.SUNDAY` is 1 and `SATURDAY` 7, `DayOfWeek.MONDAY` is 1 and `SUNDAY` 7: shift by 5, wrap, add 1.
    val day = Calendar.getInstance(locale).firstDayOfWeek
    return DayOfWeek.of((day + SUNDAY_TO_MONDAY_BASED) % DAYS_IN_WEEK + 1)
}
