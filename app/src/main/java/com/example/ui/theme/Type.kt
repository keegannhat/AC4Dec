package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ModernFontFamily = FontFamily.SansSerif

val Typography =
  Typography(
    bodyLarge =
      TextStyle(
        fontFamily = ModernFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
      ),
    bodyMedium = TextStyle(
        fontFamily = ModernFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 14.sp
    ),
    titleLarge = TextStyle(
        fontFamily = ModernFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = ModernFontFamily,
        fontWeight = FontWeight.Light,
        fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = ModernFontFamily,
        fontWeight = FontWeight.ExtraLight,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = ModernFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp
    )
  )

