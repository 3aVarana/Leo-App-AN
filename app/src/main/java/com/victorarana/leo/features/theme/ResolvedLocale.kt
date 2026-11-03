package com.victorarana.leo.features.theme

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.victorarana.leo.R
import java.util.Locale

// MARK: - Formatting locale

/**
 * The locale every formatter uses: the language of the strings on screen, the region of the device. iOS
 * `Locale.current` is the same pairing (the app's resolved localization with the region setting, e.g. `en_FR` on a
 * French phone), so a French phone shows English words, English "and" and English month names, in the date order
 * and first weekday of France. The configuration's own `Locale` would mix French formatters with English strings.
 *
 * @param resourceTag The language of the resources in use, `R.string.resource_locale`: "en", "es" or "pt-BR".
 * @param deviceLocale The device's first locale. Only its region and its unicode extensions (Android 14's regional
 * preferences: first day of the week, hour cycle, calendar, numbering system) are used; its language is not.
 */
fun resolvedLocale(
    resourceTag: String,
    deviceLocale: Locale,
): Locale {
    val builder = Locale.Builder().setLanguageTag(resourceTag)
    if (deviceLocale.country.isNotEmpty()) builder.setRegion(deviceLocale.country)
    for (key in deviceLocale.unicodeLocaleKeys) {
        builder.setUnicodeLocaleKeyword(key, deviceLocale.getUnicodeLocaleType(key))
    }
    return builder.build()
}

/**
 * The device's first locale, as the system has it. With a per-app language (Android 13+), the process's own
 * configuration, `Resources.getSystem()` included, starts with the app's locale, so the region would be lost: there
 * `LocaleManager.getSystemLocales()` answers, "ignoring app-specific overrides". Below 33 there is no per-app
 * language, and the process configuration is the system's.
 */
fun deviceLocale(context: Context): Locale =
    pickDeviceLocale(
        systemLocales = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) systemLocales(context) else null,
        processLocales = Resources.getSystem().configuration.locales,
    )

/**
 * The selection of [deviceLocale], without the platform: the first locale of the system's list when it has one, else of
 * the process's, else the JVM default (a list can be empty).
 */
internal fun pickDeviceLocale(
    systemLocales: LocaleList?,
    processLocales: LocaleList,
): Locale =
    systemLocales?.takeUnless { it.isEmpty }?.get(0)
        ?: processLocales.takeUnless { it.isEmpty }?.get(0)
        ?: Locale.getDefault()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun systemLocales(context: Context): LocaleList? =
    context.getSystemService(LocaleManager::class.java)?.systemLocales

/** The formatting locale, which [LeoTheme] resolves once for the whole tree (a test can set its own). */
val LocalResolvedLocale = staticCompositionLocalOf<Locale?> { null }

/**
 * [resolvedLocale] for the resources in use: [LocalResolvedLocale], or (outside a [LeoTheme]) resolved here. One
 * resolution per tree matters, because [deviceLocale] is a binder call on API 33+, and every `Kicker`, dot row and
 * History row reads the locale.
 */
@Composable
fun rememberResolvedLocale(): Locale = LocalResolvedLocale.current ?: rememberDeviceResolvedLocale()

/** Provides [LocalResolvedLocale], unless an outer provider (a test) already set it. */
@Composable
internal fun ProvideResolvedLocale(content: @Composable () -> Unit) {
    if (LocalResolvedLocale.current != null) {
        content()
    } else {
        CompositionLocalProvider(LocalResolvedLocale provides rememberDeviceResolvedLocale(), content = content)
    }
}

/** Read from the configuration, so a change of language (or of the system's locales) recomposes. */
@Composable
private fun rememberDeviceResolvedLocale(): Locale {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val resourceTag = stringResource(R.string.resource_locale)
    return remember(configuration, context, resourceTag) { resolvedLocale(resourceTag, deviceLocale(context)) }
}
