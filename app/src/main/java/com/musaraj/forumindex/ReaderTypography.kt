package com.musaraj.forumindex

import androidx.compose.runtime.staticCompositionLocalOf

internal enum class ReaderTypography(val label: String, val bodySize: Float, val titleSize: Int, val inset: Int, val leading: Float) {
    Compact("Compact", 16f, 22, 24, 1.18f),
    Spacious("Spacious", 18f, 24, 28, 1.4f),
}

internal val LocalReaderTypography = staticCompositionLocalOf { ReaderTypography.Compact }
internal val LocalSelectTypography = staticCompositionLocalOf<(ReaderTypography) -> Unit> { {} }
