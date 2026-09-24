package com.musaraj.forumindex

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.net.URL

class MarkdownPreviewHistoryTest {
    private val root = URL("https://forum.example/t/root/123")
    private val source = TopicMarkdownSource { url -> MarkdownPage("# Linked topic\n\n**URL:** $url\n**Showing post:** 7\n\nBody", url) }

    @Test fun internalPostLinksKeepExactUrlAndBackRetainsReader() = runTest {
        val history = MarkdownPreviewHistory(root, source)
        val initial = history.current
        initial.reader.load()
        val link = URL("https://FORUM.example:443/t/linked/456/7?foo=bar#post")
        assertTrue(history.open(link))
        assertEquals(link.toString(), history.current.url.toString())
        history.current.reader.load()
        assertEquals("Linked topic", history.current.reader.state.value.title)
        assertEquals("Body", history.current.reader.state.value.posts.single().markdown)
        assertTrue(history.back())
        assertSame(initial, history.current)
        assertTrue(history.current.reader.state.value.loaded)
        assertFalse(history.back())
    }

    @Test fun externalOriginsAreNotAddedAndSamePageIsNotDuplicated() {
        val history = MarkdownPreviewHistory(root, source)
        listOf("https://other.example/t/123", "https://sub.forum.example/t/123", "https://forum.example:444/t/123", "http://forum.example/t/123")
            .forEach { assertFalse(history.open(URL(it))) }
        assertTrue(history.open(root))
        assertTrue(history.open(URL("https://FORUM.example:443/t/root/123")))
        assertEquals(1, history.pages.value.size)
        assertTrue(history.open(URL("$root#reply")))
        assertEquals(2, history.pages.value.size)
    }

    @Test fun linkedBookmarksNeverUseSourceTopicIdentityOrMetadata() = runTest {
        val topic = Topic(123, "Source", root, null, ForumSummary(9, "Forum", "forum", null, null), null, 20)
        val history = MarkdownPreviewHistory(root, source)
        assertEquals(123, history.current.bookmark(topic).topicId)
        history.open(URL("https://forum.example/t/linked/456/7"))
        assertEquals("Linked page", history.current.bookmark(topic).title)
        history.current.reader.load()
        val bookmark = history.current.bookmark(topic)
        assertEquals("Linked topic", bookmark.title)
        assertNull(bookmark.topicId)
        assertNull(bookmark.forumId)
        assertEquals("https://forum.example/t/linked/456/7", bookmark.url)
        history.back()
        assertEquals(topic.bookmark(), history.current.bookmark(topic))
    }

    @Test fun migrationUsesFinalOriginForInternalLinks() = runTest {
        val history = MarkdownPreviewHistory(root) { MarkdownPage("Body", URL("https://new.example/t/root/123")) }
        history.current.reader.load()
        assertTrue(history.open(URL("https://new.example/t/linked/456")))
        assertEquals(2, history.pages.value.size)
    }
}
