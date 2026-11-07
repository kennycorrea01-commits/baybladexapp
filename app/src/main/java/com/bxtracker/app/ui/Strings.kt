package com.bxtracker.app.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import com.bxtracker.app.R
import com.bxtracker.app.data.Tournament
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Language helpers. Game terms (part kinds, types, finishes, 3on3, Swiss, R1/QF/SF/F) stay in English in every
 * language on purpose; everything else comes from string resources (values/ and values-es/).
 */

enum class AppLanguage(val tag: String, @StringRes val label: Int) {
    SYSTEM("", R.string.language_system),
    ENGLISH("en", R.string.language_english),
    SPANISH("es-419", R.string.language_spanish);

    companion object {
        fun current(): AppLanguage {
            val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            return when {
                tag.isBlank() -> SYSTEM
                tag.startsWith("es") -> SPANISH
                else -> ENGLISH
            }
        }
    }
}

/** Switches the app language. AppCompat recreates the activity and remembers the choice. */
fun setAppLanguage(language: AppLanguage) {
    AppCompatDelegate.setApplicationLocales(
        if (language == AppLanguage.SYSTEM) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(language.tag)
    )
}

/** The locale the UI is actually showing (the app language if one is set). */
@Composable
fun currentLocale(): Locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

fun Context.uiLocale(): Locale = resources.configuration.locales[0] ?: Locale.getDefault()

/** English "3rd", Spanish "3.º". */
fun ordinal(n: Int, locale: Locale): String =
    if (locale.language == "es") "$n.º" else com.bxtracker.app.data.ordinal(n)

@Composable
fun ordinalText(n: Int): String = ordinal(n, currentLocale())

/** "3rd of 24" / "3.º de 24", or null before a placing is set. */
fun placingText(context: Context, t: Tournament): String? = t.placement?.let { p ->
    val o = ordinal(p, context.uiLocale())
    if (t.playerCount > 0) context.getString(R.string.placing_of, o, t.playerCount) else o
}

@Composable
fun placingText(t: Tournament): String? = t.placement?.let { p ->
    val o = ordinalText(p)
    if (t.playerCount > 0) stringResource(R.string.placing_of, o, t.playerCount) else o
}

private fun pattern(locale: Locale, skeleton: String) = android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton)

/** Short local date like "Nov 7" / "7 nov" for timestamps (matches, battles). */
fun shortDate(millis: Long, locale: Locale): String = SimpleDateFormat(pattern(locale, "MMMd"), locale).format(Date(millis))

/** Full date like "Nov 7, 2026" / "7 nov 2026" for days picked in a date picker (stored as UTC midnight). */
fun pickedDay(millis: Long, locale: Locale): String =
    SimpleDateFormat(pattern(locale, "yMMMd"), locale).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))

@Composable
fun shortDate(millis: Long): String = shortDate(millis, currentLocale())

@Composable
fun pickedDay(millis: Long): String = pickedDay(millis, currentLocale())
