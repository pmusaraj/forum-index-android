package com.musaraj.forumindex

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.net.URL

class ReaderUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun returningFromPagedTopicMovesToItsFeedRow() {
        var target by mutableStateOf<FeedReturnTarget?>(null)
        val state = loadedState(rows = 40)
        val topic = state.feeds.getValue("main-feed").rows[25]
        compose.setContent { ForumIndexReader(state, returnTarget = target) }
        compose.runOnIdle { target = FeedReturnTarget("main-feed", topic.identity) }
        compose.onNodeWithTag("topic-${topic.forum.id}-${topic.id}").assertIsDisplayed()
        compose.onNodeWithText("Topic 1").assertDoesNotExist()
    }

    @Test fun headerRemainsFixedAfterFeedScroll() {
        compose.setContent { ForumIndexReader(loadedState(rows = 40)) }
        val before = compose.onNodeWithTag("reader-header").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("feed-main-feed").performScrollToIndex(20)
        val after = compose.onNodeWithTag("reader-header").fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after)
        compose.onNodeWithText("Topic 21").assertIsDisplayed()
    }

    @Test fun pagerMovesExactlyOnePageBySwipeAndTabTap() {
        var state by mutableStateOf(loadedState(subjectCount = 3))
        compose.setContent {
            ForumIndexReader(state, onSelect = { state = state.selecting(it) })
        }
        compose.onNodeWithTag("reader-pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("tab-ai").assertIsSelected()
        compose.onNodeWithTag("reader-pager").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithTag("tab-main-feed").assertIsSelected()
        compose.onNodeWithTag("tab-sport").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("tab-sport").assertIsSelected()
    }

    @Test fun eachPageRetainsItsVerticalScrollPosition() {
        var state by mutableStateOf(loadedState(rows = 40, subjectCount = 2))
        compose.setContent { ForumIndexReader(state, onSelect = { state = state.selecting(it) }) }
        compose.onNodeWithTag("feed-main-feed").performScrollToIndex(18)
        compose.onNodeWithText("Topic 1").assertDoesNotExist()
        compose.onNodeWithTag("reader-pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithTag("reader-pager").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText("Topic 19").assertIsDisplayed()
        compose.onNodeWithText("Topic 1").assertDoesNotExist()
    }

    @Test fun ellipsisStaysPinnedWhileTabStripScrolls() {
        compose.setContent { ForumIndexReader(loadedState(subjectCount = 8)) }
        val before = compose.onNodeWithContentDescription("Choose subjects").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("subject-tabs").performScrollToNode(hasTestTag("tab-subject-8"))
        val after = compose.onNodeWithContentDescription("Choose subjects").fetchSemanticsNode().boundsInRoot
        assertEquals(before.left, after.left)
        assertEquals(before.top, after.top)
        compose.onNodeWithTag("tab-subject-8").assertIsDisplayed()
    }

    @Test fun subjectSheetHidesReordersAndRefusesZeroVisibleSubjects() {
        var state by mutableStateOf(loadedState(subjectCount = 2))
        var updates = emptyList<String>()
        compose.setContent {
            ForumIndexReader(state, onUpdateVisibleOrder = {
                updates = it
                state = state.withVisible(it)
            })
        }
        compose.onNodeWithContentDescription("Choose subjects").performClick()
        compose.onNodeWithContentDescription("Move Main down").performClick()
        compose.runOnIdle { assertEquals(listOf("parent:ai", "main-feed", "parent:sport"), updates) }
        compose.onNodeWithContentDescription("Toggle AI").performClick()
        compose.onNodeWithContentDescription("Toggle Main").performClick()
        compose.runOnIdle { assertEquals(listOf("parent:sport"), updates) }
        compose.onNodeWithContentDescription("Toggle Sport").performClick()
        compose.onNodeWithContentDescription("Toggle Sport").assertIsOn()
        compose.runOnIdle { assertEquals(listOf("parent:sport"), state.visibleDestinations.map { it.id }) }
        compose.onNodeWithText("More subjects").assertIsDisplayed()
    }

    @Test fun initialShowsTwentySkeletonRowsAndFailureRetries() {
        var state by mutableStateOf(baseState(FeedState.Initial))
        var retries = 0
        compose.setContent { ForumIndexReader(state, onRetry = { retries++ }) }
        compose.onNodeWithTag("feed-main-feed").performScrollToIndex(19)
        compose.onNodeWithTag("skeleton-row-19").assertIsDisplayed()
        state = baseState(FeedState.Failed(emptyList(), "Offline"))
        compose.onNodeWithText("Offline").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun initialTaxonomyFailureCanRetryWithoutRestarting() {
        var retries = 0
        compose.setContent {
            ForumIndexReader(
                UiState(taxonomy = TaxonomyState.Error("Offline")),
                onRetryTaxonomy = { retries++ },
            )
        }
        compose.onNodeWithText("Offline").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    private fun loadedState(rows: Int = 4, subjectCount: Int = 1): UiState {
        val subjects = listOf("ai", "sport", "science", "culture", "games", "tech", "design", "subject-8")
            .take(subjectCount).mapIndexed { index, slug -> Subject(index + 1, label(slug), slug, "", index, false, 20) }
        val destinations = listOf(Destination.Main) + subjects.map(Destination::Subject)
        return UiState(
            taxonomy = TaxonomyState.Loaded(subjects),
            allDestinations = destinations,
            visibleDestinations = destinations,
            selectedDestination = destinations.first(),
            feeds = destinations.associate { destination ->
                destination.id to FeedState.Loaded((1..rows).map { topic(it, destination.id) }, 1, false)
            },
        )
    }

    private fun baseState(feed: FeedState): UiState = UiState(
        taxonomy = TaxonomyState.Loaded(emptyList()),
        allDestinations = listOf(Destination.Main),
        visibleDestinations = listOf(Destination.Main),
        selectedDestination = Destination.Main,
        feeds = mapOf(Destination.Main.id to feed),
    )

    private fun UiState.selecting(destination: Destination) = copy(selectedDestination = destination)

    private fun UiState.withVisible(ids: List<String>): UiState {
        val byId = allDestinations.associateBy { it.id }
        val visible = ids.mapNotNull(byId::get).ifEmpty { visibleDestinations.take(1) }
        return copy(
            visibleDestinations = visible,
            selectedDestination = selectedDestination?.takeIf { selected -> visible.any { it.id == selected.id } } ?: visible.first(),
        )
    }

    companion object {
        private fun label(slug: String) = if (slug == "ai") "AI"
            else slug.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        private fun topic(id: Int, destination: String) = Topic(
            id, "Topic $id", URL("https://example.com/$destination/$id"), null,
            ForumSummary(destination.hashCode(), "Example Forum", "example", null, null), null, id % 5,
        )
    }
}
