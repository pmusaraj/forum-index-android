package com.musaraj.forumindex

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.net.URL

class AppearanceAndOverviewUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun appearancePickerUpdatesOpenSettingsAndReaderTogether() {
        var appearance by mutableStateOf(AppAppearance.LIGHT)
        var background = Color.Unspecified
        compose.setContent {
            CompositionLocalProvider(LocalAppearanceSettings provides AppearanceSettings(appearance) { appearance = it }) {
                ForumIndexTheme(appearance == AppAppearance.DARK) {
                    background = MaterialTheme.colorScheme.background
                    ForumIndexReader(UiState(
                        taxonomy = TaxonomyState.Loaded(emptyList()),
                        visibleDestinations = listOf(Destination.Main),
                        selectedDestination = Destination.Main,
                        feeds = mapOf(Destination.Main.id to FeedState.Empty),
                        contribution = ContributionState(loading = false),
                    ))
                }
            }
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("appearance-dark").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(Color(0xFF1C1C1A), background) }
        compose.onNodeWithContentDescription("Close settings").performClick()
        compose.onNodeWithText("No topics yet").assertIsDisplayed()
        compose.runOnIdle { assertEquals(AppAppearance.DARK, appearance) }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("appearance-light").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(Color(0xFFFAF6EE), background) }
        compose.onNodeWithTag("appearance-auto").performClick().assertIsSelected()
    }

    @Test fun overviewRetriesAndShowsDescriptionAndLinks() {
        var calls = 0
        val forum = ForumSummary(7, "Fixture forum", "fixture", URL("https://forum.test"), null)
        compose.setContent {
            ForumIndexTheme {
                ForumOverview(7, onDismiss = {}, fetch = {
                    assertEquals(7, it)
                    if (++calls == 1) error("offline")
                    ForumDetail(forum, "<p>A <b>community</b> &amp; its topics.</p>", forum.baseUrl, URL("https://organization.test"))
                })
            }
        }
        compose.onNodeWithText("Couldn’t load site information.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Fixture forum").assertIsDisplayed()
        compose.onNodeWithText("A community & its topics.").assertIsDisplayed()
        compose.onNodeWithTag("forum-overview-forum-link").assertIsDisplayed()
        compose.onNodeWithTag("forum-overview-organization-link").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, calls) }
    }

    @Test fun webHeaderSamplingControlsStatusContrastAndResetsForHiddenPage() {
        var visible by mutableStateOf(true)
        compose.setContent {
            ForumIndexTheme {
                TopicWebPage(URL("https://forum.test/t/1"), visible = visible,
                    testHtml = "<html><body style='margin:0;background:white'><header style='height:60px;background:rgb(10,20,30)'>Header</header>Body</body></html>")
            }
        }
        compose.waitUntil(10_000) {
            val activity = compose.activityForTest()
            activity != null && !androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars
        }
        compose.runOnIdle { visible = false }
        compose.waitForIdle()
        val activity = compose.activityForTest()!!
        compose.runOnIdle {
            assertTrue(androidx.core.view.WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars)
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.activityForTest(): android.app.Activity? {
        var result: android.app.Activity? = null
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync {
            result = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).firstOrNull()
        }
        return result
    }
}
