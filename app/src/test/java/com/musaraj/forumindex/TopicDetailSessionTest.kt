package com.musaraj.forumindex

import org.junit.Assert.*
import org.junit.Test
import java.net.URL

class TopicDetailSessionTest {
    @Test fun snapshotFiltersInvalidUrlsAndDuplicatesUsingCompositeIdentity() {
        val topics = listOf(topic(1, 1), topic(2, 1).copy(url = null), topic(1, 2), topic(3, 1), topic(1, 1))
        val session = TopicDetailSession.create(topics, topics[2], "main-feed")!!
        assertEquals(listOf(TopicIdentity(1, 1), TopicIdentity(2, 1), TopicIdentity(1, 3)), session.topics.map { it.identity })
        assertEquals(1, session.currentIndex)
        val paged = session.selecting(2)
        assertTrue(paged.hasPaged)
        assertEquals(3, paged.currentTopic.id)
        assertSame(paged, paged.selecting(99))
        assertTrue(paged.selecting(1).hasPaged)
        assertEquals(1, session.currentIndex)
    }

    @Test fun missingSelectionCannotOpen() {
        assertNull(TopicDetailSession.create(emptyList(), topic(1, 1), "main-feed"))
        val invalid = topic(1, 1).copy(url = URL("http://example.com/t/1"))
        assertNull(TopicDetailSession.create(listOf(invalid), invalid, "main-feed"))
    }

    private fun topic(id: Int, forum: Int) = Topic(id, "Topic", URL("https://example.com/t/$id"), null,
        ForumSummary(forum, "Forum", "forum", null, null), null, 0)
}
