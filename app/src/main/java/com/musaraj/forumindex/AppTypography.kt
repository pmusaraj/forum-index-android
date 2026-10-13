package com.musaraj.forumindex

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

private val SourceSans3 = FontFamily(
    Font(R.font.source_sans_3_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.source_sans_3_medium, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.source_sans_3_semibold, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.source_sans_3_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.source_sans_3_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.source_sans_3_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.source_sans_3_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.source_sans_3_bold_italic, FontWeight.Bold, FontStyle.Italic),
)

// Retain Material sizing and spacing while applying the family to every text role.
private val DefaultTypography = Typography()
internal val ForumIndexTypography = Typography(
    displayLarge = DefaultTypography.displayLarge.copy(fontFamily = SourceSans3),
    displayMedium = DefaultTypography.displayMedium.copy(fontFamily = SourceSans3),
    displaySmall = DefaultTypography.displaySmall.copy(fontFamily = SourceSans3),
    headlineLarge = DefaultTypography.headlineLarge.copy(fontFamily = SourceSans3),
    headlineMedium = DefaultTypography.headlineMedium.copy(fontFamily = SourceSans3),
    headlineSmall = DefaultTypography.headlineSmall.copy(fontFamily = SourceSans3),
    titleLarge = DefaultTypography.titleLarge.copy(fontFamily = SourceSans3),
    titleMedium = DefaultTypography.titleMedium.copy(fontFamily = SourceSans3),
    titleSmall = DefaultTypography.titleSmall.copy(fontFamily = SourceSans3),
    bodyLarge = DefaultTypography.bodyLarge.copy(fontFamily = SourceSans3),
    bodyMedium = DefaultTypography.bodyMedium.copy(fontFamily = SourceSans3),
    bodySmall = DefaultTypography.bodySmall.copy(fontFamily = SourceSans3),
    labelLarge = DefaultTypography.labelLarge.copy(fontFamily = SourceSans3),
    labelMedium = DefaultTypography.labelMedium.copy(fontFamily = SourceSans3),
    labelSmall = DefaultTypography.labelSmall.copy(fontFamily = SourceSans3),
)
