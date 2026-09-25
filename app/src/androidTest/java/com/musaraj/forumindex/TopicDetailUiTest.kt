package com.musaraj.forumindex

import android.text.Spanned
import android.text.style.ClickableSpan
import android.view.View
import android.webkit.WebView
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.net.URL

class TopicDetailUiTest {
    @get:Rule val compose = createComposeRule()
    private var backDispatcher: OnBackPressedDispatcher? = null
    private var nativeRoot: View? = null

    @Test fun internalLinksKeepPreviewActionsAndBackRestoresScrollWithoutRefetching() {
        val linkedUrl = "https://forum.example/t/linked/22/7?mode=one#post"
        val requests = mutableListOf<String>()
        var selectedStar: StarredTopic? = null
        val rootMarkdown = (0..15).joinToString("\n\n---\n\n") { index ->
            "<div class='post-metadata'>\n### Author: [@user$index](/u/user$index)\n</div>\n\n" +
                if (index == 10) "[Open linked reply](/t/linked/22/7?mode=one#post)" else "Post $index " + "Text. ".repeat(50)
        }
        show(source = { url ->
            requests += url.toString()
            if (url.toString() == linkedUrl) MarkdownPage("# Linked title\n\n**URL:** $linkedUrl\n**Showing post:** 7\n\n<div class='post-metadata'>\n### Author: [@bob](/u/bob)\n</div>\n\nLinked reply.", url)
            else MarkdownPage("# Root\n\n**URL:** $url\n\n$rootMarkdown", url)
        }, toggle = { selectedStar = it })
        compose.onNodeWithTag("topic-markdown-preview").performScrollToIndex(11)
        val before = compose.onNodeWithTag("post-author-10").fetchSemanticsNode().boundsInRoot.top
        clickMarkdownLink("Open linked reply")
        compose.onNodeWithText("Linked title").assertIsDisplayed()
        compose.onNodeWithText("@bob").assertIsDisplayed()
        compose.onNodeWithTag("topic-preview-details").assertDoesNotExist()
        compose.onNodeWithTag("fixture-web").assertDoesNotExist()
        compose.onNodeWithContentDescription("Star").performClick()
        compose.runOnIdle {
            assertEquals(linkedUrl, selectedStar?.url)
            assertEquals("Linked title", selectedStar?.title)
            assertNull(selectedStar?.topicId)
        }
        compose.onNodeWithTag("topic-view-toggle").performClick()
        compose.onNodeWithTag("fixture-web").assertIsDisplayed()
        compose.onNodeWithText("Full webpage $linkedUrl").assertIsDisplayed()
        compose.onNodeWithTag("topic-header-back").performClick()
        val after = compose.onNodeWithTag("post-author-10").fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after, 1f)
        compose.onNodeWithTag("topic-header-back").assertDoesNotExist()
        compose.onNodeWithContentDescription("Star").performClick()
        compose.runOnIdle {
            assertEquals("https://forum.example/t/1", selectedStar?.url)
            assertEquals(1, selectedStar?.topicId)
            assertEquals(listOf("https://forum.example/t/1", linkedUrl), requests)
        }
    }

    @Test fun linkedFailureStaysInPreviewAndSystemBackReturnsToParent() {
        var closed = false
        var attempts = 0
        show(source = { url ->
            if (url.path == "/t/1") MarkdownPage(document("[Open linked reply](/t/other/2)"), url)
            else { attempts++; error("offline") }
        }, dismiss = { closed = true })
        clickMarkdownLink("Open linked reply")
        compose.onNodeWithText("Couldn’t load the preview").assertIsDisplayed()
        compose.onNodeWithTag("fixture-web").assertDoesNotExist()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(2, attempts) }
        compose.onNodeWithText("View full page").performClick()
        compose.onNodeWithText("Full webpage https://forum.example/t/other/2").assertIsDisplayed()
        compose.runOnIdle { backDispatcher!!.onBackPressed() }
        compose.onNodeWithText("Topic 1").assertIsDisplayed()
        compose.onNodeWithTag("topic-header-back").assertDoesNotExist()
        compose.runOnIdle { assertFalse(closed) }
    }

    private fun clickMarkdownLink(label: String) {
        fun find(view: View?): TextView? {
            if (view is TextView && view.text.toString() == label) return view
            if (view is ViewGroup) for (index in 0 until view.childCount) {
                find(view.getChildAt(index))?.let { return it }
            }
            return null
        }
        var linkView: TextView? = null
        compose.waitUntil(5_000) {
            compose.runOnIdle { linkView = find(nativeRoot) }
            linkView != null
        }
        compose.runOnIdle {
            val view = linkView!!
            val text = view.text as Spanned
            text.getSpans(0, text.length, ClickableSpan::class.java).single().onClick(view)
        }
        compose.waitForIdle()
    }

    @Test fun systemBackDismissesTheTopicSession() {
        var closed = false
        show(source = { MarkdownPage(document("Body"), it) }, dismiss = { closed = true })
        compose.runOnIdle { backDispatcher!!.onBackPressed() }
        compose.runOnIdle { assertTrue(closed) }
    }

    @Test fun markdownHeaderPersistsAcrossFullPageToggleAndSharesStarState() {
        var starred by mutableStateOf(false)
        var closed = false
        show(source = { MarkdownPage(document("A **formatted** post."), it) }, isStarred = { starred },
            toggle = { starred = !starred }, dismiss = { closed = true })
        compose.onNodeWithTag("topic-preview-title").assertIsDisplayed()
        compose.onNodeWithTag("topic-forum-header").assertIsDisplayed()
        compose.onNodeWithText("@alice").assertIsDisplayed()
        compose.onNodeWithTag("topic-view-toggle").performClick()
        compose.onNodeWithTag("fixture-web").assertIsDisplayed()
        compose.onNodeWithTag("topic-forum-header").assertIsDisplayed()
        compose.onNodeWithContentDescription("Star").performClick()
        compose.onNodeWithContentDescription("Unstar").assertIsDisplayed()
        compose.onNodeWithTag("topic-view-toggle").performClick()
        compose.onNodeWithTag("topic-preview-title").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.runOnIdle { assertTrue(starred); assertTrue(closed) }
    }

    @Test fun failedMarkdownFallsBackAndCanRetryFromPersistentHeader() {
        var requests = 0
        show(source = { if (++requests == 1) error("fixture failure") else MarkdownPage(document("Recovered"), it) })
        compose.onNodeWithTag("fixture-web").assertIsDisplayed()
        compose.onNodeWithTag("topic-view-toggle").performClick()
        compose.onNodeWithText("Couldn’t load the preview").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithTag("topic-preview-title").assertIsDisplayed()
        compose.runOnIdle { assertEquals(2, requests) }
    }

    @Test fun nonMarkdownForumNeverFetchesPreview() {
        var fetched = false
        show(topics = listOf(topic(1).copy(forum = topic(1).forum.copy(supportsMarkdown = false))),
            source = { fetched = true; error("must not fetch") })
        compose.onNodeWithTag("fixture-web").assertIsDisplayed()
        compose.onNodeWithTag("topic-view-toggle").assertDoesNotExist()
        compose.runOnIdle { assertFalse(fetched) }
    }

    @Test fun swipingSelectsTopicsAndCloseReturnsFromCurrentTopic() {
        var current = 0
        show(topics = listOf(topic(1), topic(2), topic(3)), source = { MarkdownPage(document("Body"), it) }, select = { current = it })
        compose.onNodeWithTag("topic-detail-pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("Topic 2").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, current) }
        compose.onNodeWithTag("topic-detail-pager").performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText("Topic 1").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, current) }
    }

    @Test fun fullPageTogglePreservesMarkdownScrollPosition() {
        val markdown = (0..15).joinToString("\n\n---\n\n") { index ->
            "<div class='post-metadata'>\n### Author: [@user$index](/u/user$index)\n</div>\n\nPost $index\n\n" + "Text. ".repeat(50)
        }
        show(source = { MarkdownPage("# Topic\n\n**URL:** ${it}\n\n$markdown", it) })
        compose.onNodeWithTag("topic-markdown-preview").performScrollToIndex(10)
        val before = compose.onNodeWithTag("post-author-9").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("topic-view-toggle").performClick()
        compose.onNodeWithTag("topic-view-toggle").performClick()
        val after = compose.onNodeWithTag("post-author-9").fetchSemanticsNode().boundsInRoot.top
        assertEquals(before, after, 1f)
    }

    @Test fun replyFailureOffersRetryAndAppendsPost() {
        var requests = 0
        show(source = { url ->
            when (++requests) {
                1 -> MarkdownPage(document("First") + "\n\n[Next page](/t/1.md?page=2)", url)
                2 -> error("offline")
                else -> MarkdownPage(document("Second", author = "bob"), url)
            }
        })
        compose.onNodeWithText("Couldn’t load more replies.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("@bob").assertIsDisplayed()
        compose.runOnIdle { assertEquals(3, requests) }
    }

    @Test fun realWebPreloadDoesNotBlockPreviewScrolling() {
        var selected = 0
        show(topics = listOf(topic(1), topic(2)),
            source = { MarkdownPage(document("Scrollable paragraph.\n\n".repeat(100)), it) },
            realWeb = true, select = { selected = it })
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("topic-view-toggle").fetchSemanticsNode()
                .config[SemanticsProperties.StateDescription] == "Ready"
        }
        val preview = compose.onNodeWithTag("topic-markdown-preview")
        preview.performTouchInput {
            swipe(Offset(width * .7f, height * .8f), Offset(width * .5f, height * .2f), 600)
        }
        assertTrue("A touch drag must scroll the preview", preview.fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        compose.runOnIdle { assertEquals(0, selected) }
        preview.performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals("Horizontal paging over native text still works", 1, selected) }
    }

    @Test fun webScrollKeepsItsDirectionUntilFingerLifts() {
        var selected = 0
        show(topics = listOf(topic(1), topic(2)),
            source = { MarkdownPage(document("Body"), it) }, realWeb = true, select = { selected = it })
        compose.waitUntil(5_000) {
            compose.onNodeWithTag("topic-view-toggle").fetchSemanticsNode()
                .config[SemanticsProperties.StateDescription] == "Ready"
        }
        compose.onNodeWithTag("topic-view-toggle").performClick()
        compose.onNodeWithTag("topic-webview").performTouchInput {
            down(Offset(width * .85f, height * .8f))
            // Begin vertically, then drift sideways without lifting the finger.
            moveTo(Offset(width * .85f, height * .6f), 200)
            moveTo(Offset(width * .15f, height * .4f), 400)
            up()
        }
        compose.runOnIdle { assertEquals("Vertical scroll must not change topics", 0, selected) }
        fun visibleWeb(view: View?): WebView? {
            if (view is WebView && view.visibility == View.VISIBLE) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) {
                visibleWeb(view.getChildAt(i))?.let { return it }
            }
            return null
        }
        compose.runOnIdle { assertTrue("Web content must scroll", visibleWeb(nativeRoot)!!.scrollY > 0) }
        compose.onNodeWithTag("topic-webview").performTouchInput {
            swipe(Offset(width * .8f, height * .8f), Offset(width * .3f, height * .2f), 600)
        }
        compose.runOnIdle { assertEquals("A diagonal vertical scroll must stay on the topic", 0, selected) }
        compose.onNodeWithTag("topic-detail-pager").performTouchInput { swipeLeft() }
        compose.runOnIdle { assertEquals("A new horizontal swipe must still change topics", 1, selected) }
    }

    private fun show(
        topics: List<Topic> = listOf(topic(1)),
        source: TopicMarkdownSource,
        isStarred: (StarredTopic) -> Boolean = { false },
        toggle: (StarredTopic) -> Unit = {},
        select: (Int) -> Unit = {},
        dismiss: () -> Unit = {},
        realWeb: Boolean = false,
    ) {
        var session by mutableStateOf(TopicDetailSession.create(topics, topics.first(), "main-feed")!!)
        compose.setContent {
            backDispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            nativeRoot = LocalView.current.rootView
            TopicDetailScreen(session, isStarred, { session = session.selecting(it); select(it) }, toggle, dismiss, source,
                webContent = { url, visible, ready ->
                    if (realWeb) TopicWebPage(url, visible = visible,
                        testHtml = "<html><meta name='viewport' content='width=device-width, initial-scale=1'><body>" +
                            "<p>Scrollable web paragraph</p>".repeat(200) + "</body></html>", onReady = ready)
                    else {
                        LaunchedEffect(Unit) { ready(true) }
                        if (visible) Box(Modifier.fillMaxSize().background(Color.White).testTag("fixture-web")) {
                            Text("Full webpage $url")
                        }
                    }
                })
        }
        compose.waitForIdle()
    }

    private fun topic(id: Int) = Topic(id, "Topic $id", URL("https://forum.example/t/$id"), null,
        ForumSummary(10, "Fixture forum", "fixture", null, null, true), null, 1)
    private fun document(body: String, author: String = "alice") =
        "# Topic\n\n**URL:** https://forum.example/t/1\n\n<div class='post-metadata'>\n### Author: [@$author](/u/$author)\n</div>\n\n$body"
}
