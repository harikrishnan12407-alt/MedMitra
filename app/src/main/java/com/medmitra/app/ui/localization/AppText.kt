package com.medmitra.app.ui.localization

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as PlatformText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.util.Locale

object AppText {
    private const val PROFILE_PREFERENCES = "user_profile"
    private const val LANGUAGE_KEY = "language"
    private data class Catalog(val sources: Array<String>, val translations: Array<String>, val templates: Array<String>, val translatedTemplates: Array<String>)
    private val catalogs = mutableMapOf<Boolean, Catalog>()

    fun locale(context: Context): Locale = if (isTamil(context)) Locale("ta") else Locale.ENGLISH

    fun isTamil(context: Context): Boolean {
        val language = context.getSharedPreferences(PROFILE_PREFERENCES, Context.MODE_PRIVATE)
            .getString(LANGUAGE_KEY, "English")
        return language.equals("தமிழ்", ignoreCase = true) || language.equals("Tamil", ignoreCase = true) || language.equals("ta", ignoreCase = true)
    }

    fun localizedContext(context: Context): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale(context))
        configuration.setLayoutDirection(locale(context))
        return context.createConfigurationContext(configuration)
    }

    fun translate(context: Context, source: String): String {
        if (!isTamil(context)) return source
        val normalizedSource = source.trim()
        if (normalizedSource.isEmpty()) return source
        val leadingWhitespace = source.takeWhile(Char::isWhitespace)
        val trailingWhitespace = source.takeLastWhile(Char::isWhitespace)
        val catalog = synchronized(catalogs) {
            catalogs.getOrPut(true) {
                val appContext = context.applicationContext
                val english = configuredContext(appContext, Locale.ENGLISH)
                val tamil = configuredContext(appContext, Locale("ta"))
                Catalog(
                    english.resources.getStringArray(R.array.app_text_catalog),
                    tamil.resources.getStringArray(R.array.app_text_catalog),
                    english.resources.getStringArray(R.array.app_text_templates),
                    tamil.resources.getStringArray(R.array.app_text_templates)
                )
            }
        }
        val exactIndex = catalog.sources.indexOf(normalizedSource)
        if (exactIndex >= 0) return leadingWhitespace + catalog.translations.getOrElse(exactIndex) { normalizedSource } + trailingWhitespace

        catalog.templates.forEachIndexed { index, template ->
            val match = templateMatch(template, normalizedSource) ?: return@forEachIndexed
            var translated = catalog.translatedTemplates.getOrElse(index) { template }
            match.groupValues.drop(1).forEachIndexed { argumentIndex, value ->
                translated = translated.replace("%${argumentIndex + 1}\$s", value)
            }
            return leadingWhitespace + translated + trailingWhitespace
        }
        return source
    }

    private fun configuredContext(context: Context, locale: Locale): Context =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        })

    private fun templateMatch(template: String, value: String): MatchResult? {
        val placeholders = Regex("%[1-9]\\$s")
        val expression = StringBuilder("^")
        var start = 0
        placeholders.findAll(template).forEach { placeholder ->
            expression.append(Regex.escape(template.substring(start, placeholder.range.first)))
            expression.append("(.+?)")
            start = placeholder.range.last + 1
        }
        expression.append(Regex.escape(template.substring(start))).append("$")
        return Regex(expression.toString()).matchEntire(value)
    }
}

/** App text resolves through values/values-ta resources using the saved profile language. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current,
    localize: Boolean = true
) {
    PlatformText(
        text = if (localize) AppText.translate(LocalContext.current, text) else text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}
