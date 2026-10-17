package com.musaraj.forumindex

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import java.net.URL

class OctoberParityUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun summaryToggleAuthorBylineAndCompleteTopicFooter() {
        val forum = ForumSummary(1, "Community", "community", URL("https://forum.test"), null, true)
        val topic = Topic(1, "A small topic", URL("https://forum.test/t/1"), null, forum, null, 0)
        compose.setContent {
            ForumIndexTheme {
                TopicDetailScreen(TopicDetailSession.create(listOf(topic), topic, "main-feed")!!,
                    isStarred = { false }, onSelect = {}, onToggleStar = {}, onDismiss = {},
                    source = { MarkdownPage("# A small topic\n\n**URL:** ${it}\n\n## AI-generated summary\n\nSummary text\n\n## Post 1 by @alice — 2026-10-14T12:00:00Z\n\nOriginal text", it) },
                    webContent = { _, _, _ -> Box(Modifier.fillMaxSize()) })
            }
        }
        compose.onNodeWithText("@alice").assertIsDisplayed()
        compose.onNodeWithTag("topic-ai-summary").assertDoesNotExist()
        compose.onNodeWithText("AI summary").performClick()
        compose.onNodeWithTag("topic-ai-summary").assertIsDisplayed()
        capture("reader-summary")
        compose.onNodeWithText("Hide AI summary").performClick()
        compose.onNodeWithTag("topic-ai-summary").assertDoesNotExist()
        compose.onNodeWithTag("topic-reader-footer").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("0 replies · 1 participant").assertIsDisplayed()
        compose.onNodeWithText("Open in browser").assertIsDisplayed()
        capture("reader-footer")
    }

    @Test fun bookmarksShowUnreadAndOpenSelectedSave() {
        val bookmark = StarredTopic("Saved topic", "https://forum.test/t/1", "Community", addedAt = 1, lastReadAt = 1, latestReplyAt = 2)
        var opened: StarredTopic? = null
        compose.setContent {
            ForumIndexTheme {
                ForumIndexReader(UiState(taxonomy = TaxonomyState.Loaded(emptyList()),
                    visibleDestinations = listOf(Destination.Main), selectedDestination = Destination.Main,
                    contribution = ContributionState(loading = false)), stars = listOf(bookmark), onOpenStarred = { opened = it })
            }
        }
        compose.onNodeWithContentDescription("Bookmarks, new replies").performClick()
        compose.onNodeWithText("◐ New replies").assertIsDisplayed()
        capture("bookmarks")
        compose.onNodeWithText("Saved topic").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(bookmark, opened) }
        compose.onNodeWithText("Saved topic").assertDoesNotExist()
    }
    private fun capture(name: String) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("captureParity") != "true") return
        compose.waitForIdle()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val file = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "parity-screenshots/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

}
