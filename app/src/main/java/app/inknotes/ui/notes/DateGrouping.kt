package app.inknotes.ui.notes

import androidx.annotation.StringRes
import app.inknotes.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Section a note falls into, Apple Notes style. */
sealed interface DateSection {
    data class Relative(@StringRes val label: Int) : DateSection
    data class Month(val year: Int, val month: Int) : DateSection
}

object DateGrouping {

    fun sectionFor(millis: Long, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): DateSection {
        val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val days = ChronoUnit.DAYS.between(date, today)
        return when {
            days <= 0 -> DateSection.Relative(R.string.section_today)
            days == 1L -> DateSection.Relative(R.string.section_yesterday)
            days < 7 -> DateSection.Relative(R.string.section_previous_7_days)
            days < 30 -> DateSection.Relative(R.string.section_previous_30_days)
            else -> DateSection.Month(date.year, date.monthValue)
        }
    }

    fun monthLabel(section: DateSection.Month, locale: Locale, today: LocalDate = LocalDate.now()): String {
        val month = java.time.Month.of(section.month).getDisplayName(TextStyle.FULL_STANDALONE, locale)
            .replaceFirstChar { it.titlecase(locale) }
        return if (section.year == today.year) month else "$month ${section.year}"
    }

    /** Time for today, weekday for this week, short date otherwise. */
    fun rowDate(millis: Long, locale: Locale, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
        val days = ChronoUnit.DAYS.between(dateTime.toLocalDate(), today)
        return when {
            days <= 0 -> dateTime.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
            days < 7 -> dateTime.dayOfWeek.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }
            else -> dateTime.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale))
        }
    }
}
