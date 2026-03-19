package com.memex.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.memex.app.R

// ── Google Fonts provider ────────────────────────────────────────────────────
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs
)

// ── Font families ────────────────────────────────────────────────────────────
val SpaceGroteskFamily = FontFamily(
    Font(googleFont = GoogleFont("Space Grotesk"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Space Grotesk"), fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = GoogleFont("Space Grotesk"), fontProvider = provider, weight = FontWeight.Bold)
)

val InterFamily = FontFamily(
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("Inter"), fontProvider = provider, weight = FontWeight.SemiBold)
)

// ── Shared text styles (used directly in composables) ────────────────────────
/** 28sp Bold Space Grotesk — screen titles, logo */
val MemexDisplayStyle = TextStyle(
    fontFamily   = SpaceGroteskFamily,
    fontWeight   = FontWeight.Bold,
    fontSize     = 28.sp,
    lineHeight   = 34.sp,
    letterSpacing= (-0.5).sp
)

/** 20sp SemiBold Space Grotesk — section headings */
val MemexTitleStyle = TextStyle(
    fontFamily   = SpaceGroteskFamily,
    fontWeight   = FontWeight.SemiBold,
    fontSize     = 20.sp,
    lineHeight   = 26.sp
)

/** 15sp Regular Inter — body copy */
val MemexBodyStyle = TextStyle(
    fontFamily   = InterFamily,
    fontWeight   = FontWeight.Normal,
    fontSize     = 15.sp,
    lineHeight   = 22.sp
)

/** 12sp Regular Inter colored MemexGray — captions, timestamps */
val MemexCaptionStyle = TextStyle(
    fontFamily   = InterFamily,
    fontWeight   = FontWeight.Normal,
    fontSize     = 12.sp,
    lineHeight   = 16.sp,
    color        = MemexGray
)

/** 13sp Monospace — SHA hashes, technical strings */
val MemexMonoStyle = TextStyle(
    fontFamily   = FontFamily.Monospace,
    fontWeight   = FontWeight.Normal,
    fontSize     = 13.sp,
    lineHeight   = 18.sp,
    letterSpacing= 0.3.sp
)

// ── Material3 Typography ─────────────────────────────────────────────────────
val MemexTypography = Typography(
    displayLarge  = MemexDisplayStyle.copy(fontSize = 57.sp),
    displayMedium = MemexDisplayStyle.copy(fontSize = 45.sp),
    displaySmall  = MemexDisplayStyle,
    headlineLarge = MemexTitleStyle.copy(fontSize = 32.sp),
    headlineMedium= MemexTitleStyle.copy(fontSize = 28.sp),
    headlineSmall = MemexTitleStyle,
    titleLarge    = MemexTitleStyle.copy(fontSize = 22.sp),
    titleMedium   = MemexTitleStyle.copy(fontSize = 16.sp),
    titleSmall    = MemexTitleStyle.copy(fontSize = 14.sp),
    bodyLarge     = MemexBodyStyle.copy(fontSize = 16.sp),
    bodyMedium    = MemexBodyStyle,
    bodySmall     = MemexBodyStyle.copy(fontSize = 13.sp),
    labelLarge    = MemexCaptionStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium   = MemexCaptionStyle.copy(fontSize = 12.sp),
    labelSmall    = MemexCaptionStyle.copy(fontSize = 11.sp)
)
