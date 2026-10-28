package com.medmitra.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object MedMitraColors {
    val Background = Color(0xFFF5F9F6)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceSoft = Color(0xFFEEF5F0)
    val Mint = Color(0xFF2C9B7B)
    val MintDark = Color(0xFF176B55)
    val MintPale = Color(0xFFE2F4EB)
    val Sage = Color(0xFF6D8178)
    val Text = Color(0xFF20342D)
    val TextSecondary = Color(0xFF66786F)
    val Divider = Color(0xFFDCE7E0)
    val Coral = Color(0xFFCB5D59)
    val CoralPale = Color(0xFFFFEFED)
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
            headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
            headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
            titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
            bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
            bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)
        ),
        content = content
    )
}
