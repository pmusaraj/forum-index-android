package com.musaraj.forumindex

import android.content.ActivityNotFoundException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.URL

class TopicInteractionUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun viewerHasOnlyShareStarCloseChromeWithoutNetwork() {
        compose.setContent { TopicWebView(topic(), false, {}, {}, "<html><body>fixture</body></html>") }
        val share = compose.onNodeWithContentDescription("Share")
        val star = compose.onNodeWithContentDescription("Star")
        val close = compose.onNodeWithContentDescription("Close")
        compose.onAllNodesWithContentDescription("Share").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Star").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Close").assertCountEquals(1)
        val shareBounds = share.fetchSemanticsNode().boundsInRoot
        val starBounds = star.fetchSemanticsNode().boundsInRoot
        val closeBounds = close.fetchSemanticsNode().boundsInRoot
        assertEquals(shareBounds.center.x, starBounds.center.x, 1f)
        assertEquals(starBounds.center.x, closeBounds.center.x, 1f)
        assertTrue(shareBounds.bottom < starBounds.top)
        assertTrue(starBounds.bottom < closeBounds.top)
        assertTrue(listOf(shareBounds, starBounds, closeBounds).all { it.width >= 44 * compose.density.density })
        compose.onNodeWithText("Votes").assertDoesNotExist()
        compose.onNodeWithText("Open browser").assertDoesNotExist()
        compose.onNodeWithText("Tray").assertDoesNotExist()
    }

    @Test fun missingExternalBrowserIsHandledWithoutCrash() {
        val opened = openExternalTopic(URL("https://forum.example/t/1")) { throw ActivityNotFoundException() }
        assertFalse(opened)
    }

    @Test fun invalidUrlCannotOpenOrStarButValidStarTogglesImmediately() {
        var opened = false
        var stars = emptyList<StarredTopic>()
        var validStarred by mutableStateOf(false)
        val invalid = topic(1).copy(url = URL("http://forum.example/t/1"))
        compose.setContent {
            ForumIndexReader(
                state(listOf(invalid, topic(2))),
                stars = stars,
                isStarred = { it.id == 2 && validStarred },
                onOpenTopic = { opened = true },
                onToggleStar = { validStarred = !validStarred },
            )
        }
        compose.onNodeWithTag("topic-10-1").performClick()
        compose.onNodeWithTag("topic-10-1").performTouchInput { longClick() }
        compose.onNodeWithText("Star").assertDoesNotExist()
        compose.onNodeWithText("Low quality").assertIsDisplayed()
        compose.onNodeWithText("Low quality").performClick()
        compose.onNodeWithTag("topic-10-2").performTouchInput { longClick() }
        compose.onNodeWithText("Star").performClick()
        compose.onNodeWithTag("topic-10-2").performTouchInput { longClick() }
        compose.onNodeWithText("Unstar").assertIsDisplayed().performClick()
        compose.runOnIdle { assertFalse(opened); assertFalse(validStarred) }
    }

    @Test fun longPressOffersOnlyReportsAndGatesEnrollment() {
        var enrollmentRequests = 0
        var reports = emptyList<String>()
        var enrolled by mutableStateOf(false)
        compose.setContent {
            ForumIndexReader(
                state(listOf(topic()), enrolled),
                onNeedsEnrollment = { enrollmentRequests++ },
                onReport = { _, kind -> reports += kind },
            )
        }
        compose.onNodeWithTag("topic-10-1").performTouchInput { longClick() }
        compose.onNodeWithText("Inappropriate").performClick()
        compose.runOnIdle { assertEquals(1, enrollmentRequests); assertTrue(reports.isEmpty()) }
        enrolled = true
        compose.onNodeWithTag("topic-10-1").performTouchInput { longClick() }
        compose.onNodeWithText("Wrong subject").performClick()
        compose.runOnIdle { assertEquals(listOf("wrong_subject"), reports) }
        compose.onNodeWithText("Vote").assertDoesNotExist()
    }

    @Test fun failedReportShowsConciseAlert() {
        var state by mutableStateOf(state(listOf(topic()), enrolled = true))
        compose.setContent {
            ForumIndexReader(
                state,
                onReport = { topic, _ ->
                    state = state.copy(contribution = state.contribution.copy(
                        reports = mapOf(topic.id to ReportState.Failed("Try again")),
                    ))
                },
            )
        }
        compose.onNodeWithTag("topic-10-1").performTouchInput { longClick() }
        compose.onNodeWithText("Low quality").performClick()
        compose.onNodeWithText("Report failed").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test fun backgroundStarOrReadFailureDoesNotShowAReportAlert() {
        compose.setContent {
            ForumIndexReader(state(listOf(topic()), enrolled = true).copy(
                contribution = ContributionState(
                    enrollment = Enrollment("token", Installation("id", "Name", "Phone", false, true)),
                    loading = false,
                    reports = mapOf(1 to ReportState.Failed("Try again")),
                ),
            ))
        }
        compose.onNodeWithText("Report failed").assertDoesNotExist()
    }

    @Test fun starredSheetShowsEmptyThenOpensAndRemovesCompositeRow() {
        var stars by mutableStateOf(emptyList<StarredTopic>())
        var opened: StarredTopic? = null
        compose.setContent {
            ForumIndexReader(
                state(listOf(topic())), stars = stars,
                onOpenStarred = { opened = it },
                onRemoveStar = { removed -> stars = stars.filterNot { it.forumId == removed.forumId && it.topicId == removed.topicId } },
            )
        }
        compose.onNodeWithContentDescription("Starred topics").performClick()
        compose.onNodeWithText("No starred topics").assertIsDisplayed()
        compose.runOnIdle { stars = listOf(star()) }
        compose.onNodeWithTag("starred-10-1").performClick()
        compose.runOnIdle { assertEquals(star(), opened) }
        compose.onNodeWithContentDescription("Remove Fixture topic").performClick()
        compose.onNodeWithText("No starred topics").assertIsDisplayed()
    }

    @Test fun settingsAndReportGateOpenTheSameEnrollmentSheet() {
        compose.setContent { ForumIndexReader(state(listOf(topic())), defaultDeviceName = "Test phone") }
        compose.onNodeWithContentDescription("Contribution settings").performClick()
        compose.onNodeWithText("Enable contributions").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close contribution settings").performClick()
        compose.onNodeWithTag("topic-10-1").performTouchInput { longClick() }
        compose.onNodeWithText("Low quality").performClick()
        compose.onNodeWithText("Enable contributions").assertIsDisplayed()
    }

    @Test fun enrollmentValidatesCodePointsAndSubmitsTrimmedValues() {
        var submitted: Pair<String, String>? = null
        compose.setContent {
            ForumIndexReader(
                state(listOf(topic())), defaultDeviceName = " Test phone ",
                onEnroll = { display, device -> submitted = display to device },
            )
        }
        compose.onNodeWithContentDescription("Contribution settings").performClick()
        compose.onNodeWithTag("enable-contributions").assertIsNotEnabled()
        compose.onNodeWithTag("display-name").performTextInput("  Ada  ")
        compose.onNodeWithTag("enable-contributions").assertIsEnabled()
        compose.onNodeWithTag("display-name").performTextClearance()
        compose.onNodeWithTag("display-name").performTextInput("😀".repeat(101))
        compose.onNodeWithTag("display-name").assertTextContains("😀".repeat(100))
        compose.onNodeWithTag("display-name").performTextClearance()
        compose.onNodeWithTag("display-name").performTextInput("  Ada  ")
        compose.onNodeWithTag("device-name").performTextClearance()
        compose.onNodeWithTag("enable-contributions").assertIsNotEnabled()
        compose.onNodeWithTag("device-name").performTextInput("  Pixel  ")
        compose.onNodeWithTag("enable-contributions").performClick()
        compose.runOnIdle { assertEquals("Ada" to "Pixel", submitted) }
    }

    @Test fun contributorStatusRenameAndTransparencyUseNeutralExactCopy() {
        var renamed: String? = null
        var refreshed = 0
        var ui by mutableStateOf(state(listOf(topic()), enrolled = true))
        compose.setContent {
            ForumIndexReader(ui, onRefreshContribution = { refreshed++ }, onUpdateDevice = { renamed = it })
        }
        compose.onNodeWithContentDescription("Contribution settings").performClick()
        compose.onNodeWithText("Public contributor").assertIsDisplayed()
        compose.onNodeWithText("Device unverified").assertIsDisplayed()
        compose.onNodeWithText("Refresh status").performClick()
        compose.runOnIdle { assertEquals(1, refreshed) }
        compose.onNodeWithTag("contribution-device-name").performTextClearance()
        compose.onNodeWithTag("contribution-device-name").performTextInput("  Tablet  ")
        compose.onNodeWithTag("save-device-name").performClick()
        compose.runOnIdle { assertEquals("Tablet", renamed) }
        listOf("Star changes", "Reads", "Low quality reports", "Inappropriate reports", "Wrong subject reports")
            .forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("While contributions are enabled, opening a topic sends a read action, and starring or reporting a topic sends that action to Forum Index. Your saved stars also stay on this device. You can opt out below to stop sending actions.").assertExists()
        listOf("Device ID", "Public ID", "Apple", "Play Integrity", "ranking", "Up votes", "Down votes")
            .forEach { compose.onNodeWithText(it, substring = true, ignoreCase = true).assertDoesNotExist() }
        compose.runOnIdle {
            val enrollment = ui.contribution.enrollment!!
            ui = ui.copy(contribution = ui.contribution.copy(enrollment = enrollment.copy(
                installation = enrollment.installation.copy(trusted = true, deviceVerified = true, deviceName = "Server name"),
            )))
        }
        compose.onNodeWithText("Trusted contributor").assertIsDisplayed()
        compose.onNodeWithText("Device verified").assertIsDisplayed()
        compose.onNodeWithTag("contribution-device-name").assertTextContains("Server name")
    }

    @Test fun optOutRequiresConfirmationAndKeepsLocalStarsAfterSignOut() {
        var optOuts = 0
        var ui by mutableStateOf(state(listOf(topic()), enrolled = true))
        val saved = listOf(star())
        compose.setContent { ForumIndexReader(ui, stars = saved, onOptOut = { optOuts++ }) }
        compose.onNodeWithContentDescription("Contribution settings").performClick()
        compose.onNodeWithText("Opt out").performClick()
        compose.runOnIdle { assertEquals(0, optOuts) }
        compose.onNodeWithText("Confirm opt out").assertIsDisplayed()
        compose.onNodeWithText("Keep contributions").performClick()
        compose.runOnIdle { assertEquals(0, optOuts) }
        compose.onNodeWithText("Opt out").performClick()
        compose.onNodeWithTag("confirm-opt-out").performClick()
        compose.runOnIdle {
            assertEquals(1, optOuts)
            ui = ui.copy(contribution = ui.contribution.copy(enrollment = null))
        }
        compose.onNodeWithContentDescription("Close contribution settings").performClick()
        compose.onNodeWithContentDescription("Starred topics").performClick()
        compose.onNodeWithTag("starred-10-1").assertIsDisplayed()
    }

    @Test fun trustedSettingsExplainPersonalizationAndShowForumSubmission() {
        var ui by mutableStateOf(state(emptyList(), enrolled = true, trusted = true))
        compose.setContent { ForumIndexReader(ui, emptyList()) }

        compose.onNodeWithContentDescription("Contribution settings").performClick()

        compose.onNodeWithText("Forum Index").assertIsDisplayed()
        compose.onNodeWithText("This app shows activity in Discourse forums across different subjects. If you enable contributions, your reading and starring activity is linked to your contributor installation to help improve the results.").assertIsDisplayed()
        compose.onNodeWithText("When you have contributions enabled, your Main feed will be personalized based on starred and read topics.").assertIsDisplayed()
        compose.onNodeWithTag("forum-submission-url").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("submit-forum").assertIsDisplayed()
        compose.runOnIdle {
            ui = ui.copy(contribution = ui.contribution.copy(submittingForum = true))
        }
        compose.onNodeWithTag("forum-submission-url").assertIsNotEnabled()
        compose.onNodeWithTag("submit-forum").assertIsNotEnabled()
        compose.onNodeWithTag("refresh-contribution-status").assertIsNotEnabled()
        compose.onNodeWithTag("opt-out").assertIsNotEnabled()
    }

    private fun state(topics: List<Topic>, enrolled: Boolean = false, trusted: Boolean = false) = UiState(
        taxonomy = TaxonomyState.Loaded(emptyList()),
        allDestinations = listOf(Destination.Main),
        visibleDestinations = listOf(Destination.Main),
        selectedDestination = Destination.Main,
        feeds = mapOf(Destination.Main.id to FeedState.Loaded(topics, 1, false)),
        contribution = ContributionState(
            enrollment = if (enrolled) Enrollment("token", Installation("id", "Name", "Phone", false, trusted)) else null,
            loading = false,
        ),
    )

    companion object {
        private fun topic(id: Int = 1) = Topic(
            id, "Fixture topic", URL("https://forum.example/t/$id"), null,
            ForumSummary(10, "Fixture forum", "fixture", null, null), null, 0,
        )
        private fun star() = StarredTopic("Fixture topic", "https://forum.example/t/1", "Fixture forum", 1, 10)
    }
}
