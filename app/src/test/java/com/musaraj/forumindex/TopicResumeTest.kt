package com.musaraj.forumindex

import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.URL
import java.time.Instant

class TopicResumeTest {
    private val url = URL("https://forum.test/t/topic/42")
    private fun post(id: Int, number: Int) = JSONObject().put("id", id).put("post_number", number).put("created_at", "2026-10-21T00:00:${number.toString().padStart(2, '0')}Z")
    private fun client(requests: MutableList<URL> = mutableListOf()): TopicResumeClient {
        val posts = listOf(post(100, 1), post(200, 3), post(300, 7), post(400, 9))
        return TopicResumeClient { requested ->
            requests += requested
            if (requested.path.endsWith("/posts.json")) {
                val id = requested.query.substringAfter('=').toInt()
                JSONObject().put("post_stream", JSONObject().put("posts", JSONArray(posts.filter { it.getInt("id") == id })))
            } else JSONObject().put("chunk_size", 2).put("last_posted_at", "2026-10-21T00:00:09Z")
                .put("post_stream", JSONObject().put("stream", JSONArray(listOf(100, 200, 300, 400))).put("posts", JSONArray(posts.take(1))))
        }
    }
    private fun bookmark() = StarredTopic("Topic", "$url/3#reply", "Forum", addedAt = 0, lastReadAt = 0, latestReplyAt = 100, lastReadPostNumber = 3)

    @Test fun locatesFirstUnreadAcrossDeletedPostGaps() = runTest {
        val requests = mutableListOf<URL>()
        val target = client(requests).target(bookmark())
        assertEquals(7, target.postNumber)
        assertEquals(3, target.readThrough)
        assertEquals("https://forum.test/t/topic/42.md?page=2", target.pageUrl.toString())
        assertTrue(requests.size <= 4)
        assertTrue(requests.all { it.protocol == "https" && it.userInfo == null })
    }

    @Test fun legacyBookmarksUseTimestampAndFullyReadTopicsStartAtBeginning() = runTest {
        val timestamp = Instant.parse("2026-10-21T00:00:03Z").toEpochMilli()
        assertEquals(7, client().target(bookmark().copy(lastReadPostNumber = null, lastReadAt = timestamp)).postNumber)
        val target = client().target(bookmark().copy(lastReadPostNumber = 9))
        assertNull(target.postNumber)
        assertEquals("page=1", target.pageUrl.query)
    }

    @Test fun progressNeverRegressesOrConvertsOldPostsIntoLegacyCursor() {
        val saved = bookmark().copy(lastReadPostNumber = null, lastReadAt = 200, latestReplyAt = 400)
        assertEquals(saved, saved.recordProgress(3, 100))
        val progressed = saved.recordProgress(7, 300)
        assertEquals(7, progressed.lastReadPostNumber)
        assertTrue(progressed.hasUnreadReplies)
        assertEquals(progressed, progressed.recordProgress(3, 100))
        assertFalse(progressed.recordProgress(9, 350, 400).hasUnreadReplies)
    }

    @Test fun parserReadsPermalinkNumbersAndPreviousPage() {
        val document = MarkdownTopicDocument.parse("""
            # Topic

            **URL:** $url
            **Page:** 2 of 2

            <div class="post-metadata">
            **Author:** [@alice](/u/alice)
            **Post date:** [date](/t/topic/42/7 "2026-10-21T00:00:07Z")
            </div>
            Reply

            [Previous page]($url.md?page=1)
        """.trimIndent(), URL("$url.md?page=2"))
        assertEquals(7, document.posts.single().postNumber)
        assertEquals(2, document.pageNumber)
        assertEquals("page=1", document.previousPageUrl?.query)
        assertFalse(document.startsTopic)
    }

    private fun page(number: Int, posts: List<Int>, previous: Boolean = false) =
        "# Topic\n\n**URL:** $url\n**Page:** $number\n\n" + posts.joinToString("\n\n") {
            "## Post $it by @alice — 2026-10-21T00:00:07Z\n\nReply $it"
        } + if (previous) "\n\n[Previous page](https://forum.test/t/topic/42.md?page=1)" else ""

    @Test fun resumesThenPrependsWithoutDuplicatingPostsOrChangingExistingIds() = runTest {
        val reader = MarkdownTopicReader(url) { requested ->
            MarkdownPage(if (requested.query == "page=2") page(2, listOf(7, 9), true) else page(1, listOf(1, 3, 7)), requested)
        }
        reader.load(bookmark(), client())
        assertEquals(7, reader.state.value.firstUnreadPostNumber)
        val original = reader.state.value.posts
        reader.loadPreviousPage()
        assertEquals(listOf(1, 3, 7, 9), reader.state.value.posts.map { it.postNumber })
        assertEquals(original, reader.state.value.posts.takeLast(2))
        assertTrue(reader.state.value.hasCompleteTopic)
        assertNull(reader.state.value.previousPageUrl)
    }

    @Test fun missingResumePostFailsWithoutLoadingBeginningAndCanRetry() = runTest {
        var valid = false
        val reader = MarkdownTopicReader(url) { requested -> MarkdownPage(page(2, if (valid) listOf(7, 9) else listOf(9)), requested) }
        reader.load(bookmark(), client())
        assertNotNull(reader.state.value.error)
        assertFalse(reader.state.value.loaded)
        valid = true
        reader.load(bookmark(), client())
        assertTrue(reader.state.value.loaded)
        assertNull(reader.state.value.error)
    }

    @Test fun previousPaginationRejectsOtherOriginsAndForwardPages() {
        assertNull(TopicPagination.previousUrl(URL("https://other.test/t/topic/42?page=1"), url, 2))
        assertNull(TopicPagination.previousUrl(URL("$url?page=3"), url, 2))
        assertNull(TopicPagination.previousUrl(URL("$url?page=0"), url, 2))
    }
}
