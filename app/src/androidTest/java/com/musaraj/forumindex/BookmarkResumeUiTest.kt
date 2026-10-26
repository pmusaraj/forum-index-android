package com.musaraj.forumindex

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.net.URL

class BookmarkResumeUiTest {
    @get:Rule val compose = createComposeRule()
    private val url = URL("https://forum.test/t/42")
    private val forum = ForumSummary(1, "Community", "community", URL("https://forum.test"), null, true)
    private val topic = Topic(42, "Saved topic", url, null, forum, null, 3)
    private val saved = topic.bookmark().copy(lastReadAt = 0, latestReplyAt = 100, lastReadPostNumber = 2)
    private val resume = TopicResumeClient { JSONObject("""{"chunk_size":2,"last_posted_at":"2026-10-21T00:00:04Z","post_stream":{"stream":[1,2,3,4],"posts":[{"id":1,"post_number":1,"created_at":"2026-10-21T00:00:01Z"},{"id":2,"post_number":2,"created_at":"2026-10-21T00:00:02Z"},{"id":3,"post_number":3,"created_at":"2026-10-21T00:00:03Z"},{"id":4,"post_number":4,"created_at":"2026-10-21T00:00:04Z"}]}}""") }
    private fun page(second: Boolean): String = "# Saved topic\n\n**URL:** $url\n**Page:** ${if (second) 2 else 1}\n\n" +
        (if (second) listOf(3, 4) else listOf(1, 2)).joinToString("\n\n") {
            "## Post $it by @alice — 2026-10-21T00:00:0${it}Z\n\n" + "Reply $it long text. ".repeat(180)
        } + if (second) "\n\n[Previous page]($url.md?page=1)" else "\n\n[Next page]($url.md?page=2)"

    @Test fun resumesAtUnreadAndRecordsOnlyAfterScrollingThenReturnsToBeginning() {
        val read = mutableListOf<Int?>()
        compose.setContent {
            ForumIndexTheme {
                TopicDetailScreen(TopicDetailSession.create(listOf(topic), topic, "bookmarks")!!,
                    isStarred = { true }, onSelect = {}, onToggleStar = {}, onDismiss = {},
                    savedBookmark = { saved }, resumeClient = resume,
                    onReadPost = { _, post, _ -> read += post.postNumber },
                    source = { MarkdownPage(page(it.query == "page=2"), it) },
                    webContent = { _, _, _ -> Box(Modifier.fillMaxSize()) })
            }
        }
        compose.onNodeWithTag("topic-first-unread").assertIsDisplayed()
        compose.runOnIdle { assertTrue(read.isEmpty()) }
        repeat(4) { compose.onNodeWithTag("topic-markdown-preview").performTouchInput { swipeUp() } }
        compose.runOnIdle { assertTrue(3 in read) }
        compose.onNodeWithTag("topic-markdown-preview").performScrollToIndex(0)
        compose.onNodeWithTag("topic-earlier-replies").performClick()
        compose.onNodeWithTag("topic-earlier-replies").assertDoesNotExist()
        compose.onNodeWithTag("topic-back-to-top").performClick()
        compose.onNodeWithTag("topic-preview-title").assertIsDisplayed()
        compose.onNodeWithTag("topic-first-unread").assertDoesNotExist()
    }

    @Test fun resumeFailureKeepsPreviewAndOffersRetryAndBeginning() {
        var calls = 0
        compose.setContent {
            TopicDetailScreen(TopicDetailSession.create(listOf(topic), topic, "bookmarks")!!,
                isStarred = { true }, onSelect = {}, onToggleStar = {}, onDismiss = {}, savedBookmark = { saved },
                resumeClient = TopicResumeClient { calls++; throw java.io.IOException() },
                source = { MarkdownPage(page(false), it) }, webContent = { _, _, _ -> Box(Modifier.fillMaxSize()) })
        }
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(2, calls) }
        compose.onNodeWithText("Back to top").performClick()
        compose.onNodeWithTag("topic-preview-title").assertIsDisplayed()
    }

    @Test fun markdownImagesReceiveAccessibleClickSpansIncludingLinkedImages() {
        lateinit var renderer: io.noties.markwon.Markwon
        var selected: TopicImage? = null
        compose.setContent { renderer = rememberMarkdownRenderer(url, onImage = { selected = it }) }
        compose.runOnIdle {
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            val view = android.widget.TextView(context)
            renderer.setMarkdown(view, "[![Diagram](/image.png)](https://external.test/full.png)")
            val text = view.text as android.text.Spanned
            val clicks = text.getSpans(0, text.length, android.text.style.ClickableSpan::class.java)
            assertEquals(1, clicks.size)
            clicks.single().onClick(view)
            assertEquals("https://forum.test/image.png", selected?.url.toString())
            assertEquals("Diagram", selected?.label)
        }
    }

    @Test fun imageViewerSupportsRetryZoomFitAndClose() {
        var visible by mutableStateOf(true)
        var loads = 0
        compose.setContent {
            if (visible) TopicImageLightbox(TopicImage(URL("https://forum.test/image.png"), "Example image"), loader = {
                if (++loads == 1) throw java.io.IOException()
                android.graphics.Bitmap.createBitmap(20, 20, android.graphics.Bitmap.Config.ARGB_8888)
            }) { visible = false }
        }
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithTag("image-lightbox-image").assertIsDisplayed()
        compose.onNodeWithTag("image-lightbox-zoom-in").performClick()
        compose.onNodeWithText("Zoom out").assertIsEnabled()
        compose.onNodeWithText("Fit").performClick()
        compose.onNodeWithText("Zoom out").assertIsNotEnabled()
        compose.onNodeWithTag("image-lightbox-close").performClick()
        compose.onNodeWithTag("image-lightbox").assertDoesNotExist()
    }
}
