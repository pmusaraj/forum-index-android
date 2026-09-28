package com.musaraj.forumindex

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.net.URL

class RecentParityUiTest {
    @get:Rule val compose = createComposeRule()
    private val forum = ForumSummary(7, "Community", "community", URL("https://forum.test"), null, true)
    private val topic = Topic(1, "Fixture topic", URL("https://forum.test/t/1"), "<p>An <b>excerpt</b> &amp; context.</p>", forum, null, 2,
        listOf(ExternalSignal(ExternalSignalSource.HACKER_NEWS, URL("https://news.ycombinator.com/item?id=1"))))

    @Test fun aboutRetriesWithoutOpeningTopicAndOffersExplicitOpen() {
        var calls = 0
        var opened = false
        compose.setContent {
            ForumIndexTheme {
                TopicAbout(topic, onDismiss = {}, onOpen = { opened = true }, fetch = {
                    if (++calls == 1) error("offline")
                    ForumDetail(forum, "<p>Community description</p>", forum.baseUrl, URL("https://parent.test"))
                })
            }
        }
        compose.onNodeWithText("An excerpt & context.").assertIsDisplayed()
        compose.onNodeWithText("Couldn’t load community information.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performScrollTo().performClick()
        compose.onNodeWithText("Community description").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("topic-about-parent-link").performScrollTo().assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, calls); assertFalse(opened) }
        compose.onNodeWithTag("topic-about-open-topic").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(opened) }
    }

    @Test fun feedContextMenuHasAboutAndDiscussionAccessibility() {
        compose.setContent {
            ForumIndexTheme {
                ForumIndexReader(UiState(
                    taxonomy = TaxonomyState.Loaded(emptyList()),
                    visibleDestinations = listOf(Destination.Main), selectedDestination = Destination.Main,
                    feeds = mapOf(Destination.Main.id to FeedState.Loaded(listOf(topic), 1, false)),
                    contribution = ContributionState(loading = false),
                ))
            }
        }
        compose.onNodeWithContentDescription("Fixture topic, Community, 2 replies, Discussed on Hacker News").assertExists()
        compose.onNodeWithTag("topic-7-1").performTouchInput { longClick() }
        compose.onNodeWithTag("topic-context-about").assertIsDisplayed()
    }

    @Test fun previewShowsDiscussionLinkAndRevealsTitleOnlyOnUpwardScroll() {
        val markdown = (0..25).joinToString("\n\n---\n\n") { index ->
            "<div class='post-metadata'>\n**Author:** [@user$index](/u/user$index)\n</div>\n\nPost $index " + "Text. ".repeat(80)
        }
        compose.setContent {
            ForumIndexTheme {
                TopicDetailScreen(TopicDetailSession.create(listOf(topic), topic, Destination.Main.id)!!,
                    isStarred = { false }, onSelect = {}, onToggleStar = {}, onDismiss = {},
                    source = { url -> MarkdownPage(markdown, url) }, webContent = { _, _, _ -> Box(Modifier.fillMaxSize()) })
            }
        }
        compose.onNodeWithTag("external-signal-hacker_news").assertIsDisplayed()
        compose.onNodeWithTag("topic-compact-title").assertDoesNotExist()
        compose.onNodeWithTag("topic-markdown-preview").performScrollToIndex(10)
        compose.onNodeWithTag("topic-compact-title").assertDoesNotExist()
        compose.onNodeWithTag("topic-markdown-preview").performTouchInput { swipe(Offset(width * .5f, height * .3f), Offset(width * .5f, height * .65f), 600) }
        compose.onNodeWithTag("topic-compact-title").assertIsDisplayed()
        compose.onNodeWithTag("topic-markdown-preview").performTouchInput { swipe(Offset(width * .5f, height * .75f), Offset(width * .5f, height * .3f), 600) }
        compose.onNodeWithTag("topic-compact-title").assertDoesNotExist()
        compose.onNodeWithTag("topic-markdown-preview").performScrollToIndex(0)
        compose.onNodeWithTag("topic-compact-title").assertDoesNotExist()
    }
}
