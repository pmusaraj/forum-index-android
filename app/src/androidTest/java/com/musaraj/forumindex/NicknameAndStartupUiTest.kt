package com.musaraj.forumindex

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test

class NicknameAndStartupUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun startupAndNicknameSettingsInBothAppearances() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val topics = instrumentation.context.assets.open("store/Feed.json").bufferedReader().use {
            ForumIndexJson.feed(it.readText()).results
        }
        val loaded = UiState(
            taxonomy = TaxonomyState.Loaded(emptyList()),
            allDestinations = listOf(Destination.Main), visibleDestinations = listOf(Destination.Main),
            selectedDestination = Destination.Main,
            feeds = mapOf(Destination.Main.id to FeedState.Loaded(topics, 1, false)),
            contribution = ContributionState(
                enrollment = Enrollment("fixture", Installation("fixture-id", "DancingOtter42", "Pixel 9", false, false)),
                loading = false,
            ),
        )
        var state by mutableStateOf(UiState())
        var dark by mutableStateOf(false)
        var generation by mutableIntStateOf(0)
        compose.setContent {
            ForumIndexTheme(dark) {
                CompositionLocalProvider(LocalAppearanceSettings provides AppearanceSettings(
                    if (dark) AppAppearance.DARK else AppAppearance.LIGHT,
                )) {
                    key(generation) {
                        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                            ForumIndexReader(state, onRegenerateNickname = {
                                val contribution = state.contribution
                                state = state.copy(contribution = contribution.copy(
                                    nickname = "ReadingFox07",
                                    enrollment = contribution.enrollment?.let {
                                        it.copy(installation = it.installation.copy(displayName = "ReadingFox07"))
                                    },
                                ))
                            })
                        }
                    }
                }
            }
        }
        for (appearance in listOf("light", "dark")) {
            compose.runOnIdle {
                dark = appearance == "dark"
                val color = if (dark) 0xFF1C1C1A.toInt() else 0xFFFAF6EE.toInt()
                val style = if (dark) SystemBarStyle.dark(color) else SystemBarStyle.light(color, color)
                compose.activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                state = UiState()
                generation++
            }
            compose.onNodeWithTag("startup-loading").assertIsDisplayed()
            capture("$appearance-loading")
            // Remain on the logo until the first feed finishes, not just taxonomy.
            compose.runOnIdle { state = loaded.copy(feeds = emptyMap()) }
            compose.onNodeWithTag("startup-loading").assertIsDisplayed()
            compose.runOnIdle { state = loaded }
            compose.onNodeWithTag("startup-loading").assertDoesNotExist()
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithTag("settings-app-description").assertIsDisplayed()
            capture("$appearance-settings")
            compose.onNodeWithTag("regenerate-device-name").performScrollTo().performClick()
            compose.onNodeWithTag("contribution-device-name").assertTextEquals("ReadingFox07")
            compose.onNodeWithText("App admins use this nickname to identify your contributions. They can promote you to a trusted contributor, giving your activity more weight in topic rankings.").performScrollTo()
            capture("$appearance-nickname")
            compose.onNodeWithContentDescription("Close settings").performClick()
            compose.onNodeWithContentDescription("Settings").performClick()
            compose.onNodeWithTag("contribution-device-name").performScrollTo().assertTextEquals("ReadingFox07")
            compose.runOnIdle { state = state.copy(contribution = state.contribution.copy(enrollment = null)) }
            compose.onNodeWithTag("regenerate-device-name").performScrollTo().assertIsEnabled()
            compose.onNodeWithTag("contribution-device-name").assertTextEquals("ReadingFox07")
            compose.onNodeWithContentDescription("Close settings").performClick()
            // Later refreshes must not cover the app with the startup logo.
            compose.runOnIdle { state = loaded.copy(feeds = mapOf(Destination.Main.id to FeedState.Refreshing(emptyList(), 0, false))) }
            compose.onNodeWithTag("startup-loading").assertDoesNotExist()
        }
    }

    @Test fun startupFailureExposesRetry() {
        var state by mutableStateOf(UiState())
        compose.setContent { ForumIndexTheme { ForumIndexReader(state) } }
        compose.onNodeWithTag("startup-loading").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(taxonomy = TaxonomyState.Error("Connection unavailable")) }
        compose.onNodeWithTag("startup-loading").assertDoesNotExist()
        compose.onNodeWithText("Retry").assertIsDisplayed()
    }

    private fun capture(name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureReview") != "true") return
        compose.waitForIdle()
        Thread.sleep(800) // Allow native system-bar transitions and touch ripples to finish.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "review-screenshots/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
