package com.musaraj.forumindex

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.net.URL
import java.io.IOException
import java.time.Instant

class RecentParityTest {
    private val url = URL("https://forum.example/t/topic/7")
    private fun topic(field: String, native: Boolean = true): Topic = ForumIndexJson.feed("""
        {"count":1,"results":[{"id":7,"title":"Topic","url":"$url",$field
        "forum":{"id":1,"name":"Forum","slug":"forum","supports_markdown":$native}}]}
    """).results.single()

    @Test fun readerSourceDistinguishesLegacyExplicitOptOutAndConversion() {
        assertEquals(url, topic("").readerMarkdownUrl)
        assertNull(topic("", false).readerMarkdownUrl)
        for (value in listOf("null", "\"http://bad.example/topic\"", "\"garbage\"")) {
            assertFalse(topic("\"reader_markdown_url\":$value,").usesMarkdownReader)
        }
        val converted = topic("\"reader_markdown_url\":\"https://index.example/api/v2/topics/7/markdown\",", false)
        assertTrue(converted.usesConvertedMarkdown)
        assertEquals(converted.readerMarkdownUrl, converted.bookmark().readerMarkdownUrl)
        val history = MarkdownPreviewHistory(url, TopicMarkdownSource { MarkdownPage("Hello", it) }, converted.readerMarkdownUrl!!)
        assertEquals(url.toString(), history.current.bookmark(converted).url)
        assertEquals(converted.readerMarkdownUrl, history.current.reader.state.value.baseUrl)
    }

    @Test fun discoveredRequestKeepsFiltersAndRejectsForeignOrEscapingPaths() {
        val api = HttpForumIndexApi()
        val feed = DiscoveredFeed("fresh", "Fresh", "", "/api/v2/topics?feed=fresh&page=9&limit=1&language=fr&%70age=8", false)
        assertEquals("feed=fresh&page=2&limit=30&language=en", api.discoveredTopicsUrl(feed, 2).query)
        for (path in listOf("https://evil.example/api/v2/topics", "//evil.example/api/v2/topics", "/api/v2/../secret", "/api/v2/%2e%2e/secret", "/api/v2/%5csecret", "/api/v1/topics", "/api/v2/topics#fragment")) {
            assertThrows(IllegalArgumentException::class.java) { api.discoveredTopicsUrl(feed.copy(path = path), 1) }
        }
    }

    @Test fun discoveredPublicFeedsNeverSendCredentialsOrFollowRedirects() {
        val requests = mutableListOf<java.net.HttpURLConnection>()
        val api = HttpForumIndexApi(connectionFactory = { requested ->
            object : java.net.HttpURLConnection(requested) {
                override fun connect() {}
                override fun disconnect() {}
                override fun usingProxy() = false
                override fun getResponseCode() = 200
                override fun getInputStream() = """{"count":0,"results":[]}""".byteInputStream()
            }.also { requests += it }
        })
        val feed = DiscoveredFeed("fresh", "Fresh", "", "/api/v2/topics?feed=fresh", false)
        api.fetchDiscoveredTopics(feed, 1, "secret")
        assertNull(requests.last().getRequestProperty("Authorization"))
        assertFalse(requests.last().instanceFollowRedirects)
        api.fetchDiscoveredTopics(feed.copy(personalized = true), 1, "secret")
        assertEquals("Bearer secret", requests.last().getRequestProperty("Authorization"))
    }

    @Test fun oneboxesUnescapeGithubButPreserveOtherQuotesAndCode() {
        val github = "> <https://github.com/org/project>\n>\n> \\# Title\n> \\*\\*bold\\*\\* and \\[link\\](https://example.com)"
        val segments = MarkdownOnebox.segments("Before\n\n$github\n\nAfter")
        assertEquals(listOf(false, true, false), segments.map { it.isOnebox })
        assertTrue(segments[1].markdown.contains("# Title\n**bold**"))
        assertEquals(0, MarkdownOnebox.segments("```md\n$github\n```\n\n> ordinary quote").count { it.isOnebox })
        val other = MarkdownOnebox.segments(github.replace("github.com", "example.com")).single()
        assertTrue(other.markdown.contains("\\# Title"))
        assertFalse(MarkdownOnebox.segments("> <https://github.com/org/project>\n> ordinary quote").single().isOnebox)
    }

    @Test fun retryAfterIsHostScopedAndKeepsLongestDeadline() {
        var now = Instant.parse("2026-10-29T12:00:00Z")
        val gate = MarkdownRetryGate { now }
        gate.record(url, "60")
        gate.record(url, "10")
        gate.check(URL("https://other.example/topic"))
        now = now.plusSeconds(30)
        assertThrows(IOException::class.java) { gate.check(URL("https://forum.example/t/another")) }
        now = now.plusSeconds(31)
        gate.check(url)
        gate.record(url, "Thu, 29 Oct 2026 12:03:00 GMT")
        assertThrows(IOException::class.java) { gate.check(url) }
        now = now.plusSeconds(120)
        gate.check(url)
        listOf("NaN", "-3", "infinity", "invalid").forEach { gate.record(url, it) }
        gate.check(url)
    }

    @Test fun documentCacheExpiresEvictsAndNeverCachesErrors() = runTest {
        var now = 0L
        var fetches = 0
        val cache = TopicDocumentCache(capacity = 2, maximumBytes = 50, lifetimeMillis = 10, now = { now })
        val fetch: suspend (URL) -> MarkdownPage = { fetches++; MarkdownPage("Hello", it) }
        cache.load(url, fetch); cache.load(url, fetch)
        assertEquals(1, fetches)
        now = 11
        cache.load(url, fetch)
        assertEquals(2, fetches)
        cache.load(URL("https://forum.example/b"), fetch)
        cache.load(URL("https://forum.example/c"), fetch)
        cache.load(url, fetch)
        assertEquals(5, fetches)
        val oversized = URL("https://forum.example/large")
        repeat(2) { cache.load(oversized) { fetches++; MarkdownPage("x".repeat(51), it) } }
        assertEquals(7, fetches)
        val failed = URL("https://forum.example/failure")
        try { cache.load(failed) { throw IOException() }; fail() } catch (_: IOException) { }
        cache.load(failed, fetch)
        assertEquals(8, fetches)
    }
}
