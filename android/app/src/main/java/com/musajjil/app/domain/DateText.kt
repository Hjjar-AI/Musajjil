package com.musajjil.app.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

/** Shared by typed date fields, pickers and validation. A date never has a time zone. */
object DateText {
    private val formatter = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)

    fun format(date: LocalDate): String = formatter.format(date)

    fun parse(value: String): LocalDate? = try {
        LocalDate.parse(normalizeDigits(value.trim()), formatter)
    } catch (_: DateTimeParseException) {
        null
    }

    fun positiveWholeNumber(value: String): Int? =
        normalizeDigits(value.trim()).toIntOrNull()?.takeIf { it > 0 }

    fun normalizeDigits(value: String): String = buildString {
        value.forEach { char ->
            append(when (char) {
                in '٠'..'٩' -> '0' + (char - '٠')
                in '۰'..'۹' -> '0' + (char - '۰')
                else -> char
            })
        }
    }
}
