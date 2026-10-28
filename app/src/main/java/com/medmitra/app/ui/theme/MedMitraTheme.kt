package com.medmitra.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object MedMitraColors {
    val Background = Color(0xFFF3FAF6)
    val Surface = Color(0xFFFCFEFD)
    val SurfaceSoft = Color(0xFFEAF5EE)
    val Mint = Color(0xFF329A79)
    val MintDark = Color(0xFF176B55)
    val MintPale = Color(0xFFDFF2E7)
    val Sage = Color(0xFF687E73)
    val Text = Color(0xFF1B352B)
    val TextSecondary = Color(0xFF60776B)
    val Divider = Color(0xFFD3E5DA)
    val Coral = Color(0xFFC95D59)
    val CoralPale = Color(0xFFFFF0ED)
    val Amber = Color(0xFF92662B)
    val AmberPale = Color(0xFFFFF3DD)
}

@Composable
fun MedMitraTheme(content: @Composable () -> Unit) {
    val colors = MedMitraColors
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = colors.MintDark,
            onPrimary = Color.White,
            primaryContainer = colors.MintPale,
            onPrimaryContainer = colors.MintDark,
            secondary = colors.Sage,
            onSecondary = Color.White,
            secondaryContainer = colors.SurfaceSoft,
            onSecondaryContainer = colors.Text,
            error = colors.Coral,
            onError = Color.White,
            errorContainer = colors.CoralPale,
            onErrorContainer = colors.Coral,
            background = colors.Background,
            onBackground = colors.Text,
            surface = colors.Surface,
            onSurface = colors.Text,
            surfaceVariant = colors.SurfaceSoft,
            onSurfaceVariant = colors.TextSecondary,
            outline = colors.Divider
        ),
        typography = MaterialTheme.typography.copy(
            headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 25.sp, lineHeight = 31.sp, letterSpacing = (-.25).sp),
            headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-.15).sp),
            titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-.1).sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
            bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
            bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 17.sp)
        ),
        content = content
    )
}
