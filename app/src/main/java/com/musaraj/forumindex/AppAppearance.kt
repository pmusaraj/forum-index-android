package com.musaraj.forumindex

import androidx.compose.runtime.staticCompositionLocalOf

enum class AppAppearance(val title: String) { AUTO("Auto"), LIGHT("Light"), DARK("Dark") }

internal data class AppearanceSettings(
    val selected: AppAppearance = AppAppearance.AUTO,
    val onSelect: (AppAppearance) -> Unit = {},
)
internal val LocalAppearanceSettings = staticCompositionLocalOf { AppearanceSettings() }
