package com.musaraj.forumindex

import java.net.URL
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OctoberParityTest {
    private val url = URL("https://example.com/t/topic/42")
    private val post = "## Post 1 by @Alice — 2026-10-14T12:00:00Z\n\nFirst post"
    private fun envelope(extra: String = "", body: String = post) = "# Topic\n\n**URL:** $url\n$extra\n\n$body"

    @Test fun extractsOnlyGeneratedSummaryAndPreservesPostContent() {
        val document = MarkdownTopicDocument.parse(envelope(body = "## AI-generated summary\n\nSummary **text**\n\n$post"), url)
        assertEquals("Summary **text**", document.summary)
        assertEquals("First post", document.posts.single().markdown)
        assertTrue(document.startsTopic)
        assertNull(MarkdownTopicDocument.parse("## AI-generated summary\n\nOrdinary markdown", url).summary)
        assertNull(MarkdownTopicDocument.parse(envelope(body = "$post\n\n## AI-generated summary\n\nUser heading"), url).summary)
    }

    @Test fun individualPostsAndLaterPagesNeverClaimCompleteTopic() = runTest {
        for (extra in listOf("**Page:** 2 of 2", "**Showing post:** 3")) {
            val reader = MarkdownTopicReader(url) { MarkdownPage(envelope(extra), it) }
            reader.load()
            assertFalse(reader.state.value.hasCompleteTopic)
        }
    }

    @Test fun footerRequiresValidCompletionAndCountsAuthorsCaseInsensitively() = runTest {
        var calls = 0
        val reader = MarkdownTopicReader(url) {
            MarkdownPage(if (++calls == 1) envelope(body = "$post\n\n[Next page]($url?page=2)") else
                envelope("**Page:** 2", "## Post 2 by @alice — 2026-10-14T13:00:00Z\n\nReply"), it)
        }
        reader.load()
        assertFalse(reader.state.value.hasCompleteTopic)
        reader.loadNextPage()
        assertTrue(reader.state.value.hasCompleteTopic)
        assertEquals(1, reader.state.value.participantCount)
        assertEquals(1, reader.state.value.replyCount)
    }

    @Test fun invalidNextLinkDoesNotShowFalseEndOfTopic() = runTest {
        val reader = MarkdownTopicReader(url) { MarkdownPage(envelope(body = "$post\n\n[Next page](https://other.example/t/42?page=2)"), it) }
        reader.load()
        assertNull(reader.state.value.nextPageUrl)
        assertFalse(reader.state.value.hasCompleteTopic)
    }

    @Test fun activityEndpointKeepsSubdirectoryAndDropsPostQueryAndFragment() {
        assertEquals("https://example.com/forum/t/42.json", BookmarkActivity.endpoint("https://example.com/forum/t/slug/42/8?x=1#post")?.toString())
        assertNull(BookmarkActivity.endpoint("http://example.com/t/42"))
        assertNull(BookmarkActivity.endpoint("https://example.com/about"))
        assertNull(BookmarkActivity.endpoint("https://user:password@example.com/t/42"))
    }

    @Test fun unreadActivityRequiresNewerReplyAndClearsWhenRead() {
        val bookmark = StarredTopic("Title", url.toString(), "Forum", addedAt = 100, lastReadAt = 200, latestReplyAt = 201)
        assertTrue(bookmark.hasUnreadReplies)
        assertFalse(bookmark.copy(lastReadAt = 201).hasUnreadReplies)
        assertFalse(bookmark.copy(latestReplyAt = null).hasUnreadReplies)
    }

    @Test fun feedbackCountsUnicodeCodePointsAndSendsAndroidMetadata() {
        val message = FeedbackSubmission("id", " 😀 ", "1.0", "2", "15")
        assertTrue(message.isValid)
        assertEquals("😀", message.toJson().getString("message"))
        assertEquals("android", message.toJson().getString("platform"))
        assertTrue(message.copy(message = "😀".repeat(1000)).isValid)
        assertFalse(message.copy(message = "😀".repeat(1001)).isValid)
        assertFalse(message.copy(message = " \n ").isValid)
        assertTrue(HttpForumIndexApi().mainTopicsUrl(1).query.contains("feed=trending"))
    }
}
