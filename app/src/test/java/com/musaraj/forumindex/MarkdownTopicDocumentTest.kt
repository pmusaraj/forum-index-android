package com.musaraj.forumindex

import org.junit.Assert.*
import org.junit.Test
import java.net.URL
import java.time.Instant

class MarkdownTopicDocumentTest {
    private val base = URL("https://example.com/t/topic/123")

    @Test fun legacyEnvelopesSplitAuthorsAndPreserveFencedExamples() {
        val document = MarkdownTopicDocument.parse("""
            # Legacy
            **URL:** $base
            **Posts:** 2

            ## Post 1 by @alice\_smith — 2026-09-24T12:00:00Z

            First post.

            ---

            ## Post 2 by @bob — 2026-09-24T13:00:00Z

            ```markdown
            ## Post 3 by @example — 2026-09-24T14:00:00Z
            ```

            [Next page](/t/topic/123?page=2)
        """.trimIndent(), base)
        assertEquals(2, document.posts.size)
        assertEquals("alice_smith", document.posts[0].username)
        assertEquals("First post.", document.posts[0].markdown)
        assertEquals("bob", document.posts[1].username)
        assertTrue(document.posts[1].markdown.contains("## Post 3 by @example"))
        assertEquals(Instant.parse("2026-09-24T13:00:00Z"), document.posts[1].publishedAt)
        assertEquals("https://example.com/t/topic/123?page=2", document.nextPageUrl.toString())
        val ordinary = "## Post 1 by @alice — 2026-09-24T12:00:00Z"
        assertEquals(ordinary, MarkdownTopicDocument.parse(ordinary, base).posts.single().markdown)
        val invalid = "# Example\n**URL:** $base\n\n## Post 1 by @alice — 2026-invalid"
        assertNull(MarkdownTopicDocument.parse(invalid, base).posts.single().username)
    }

    @Test fun generatedEnvelopeBecomesPostsAndForwardNavigation() {
        val document = MarkdownTopicDocument.parse("""
            # Topic

            **URL:** $base
            **Category:** Feedback
            **Page:** 1

            <div class="post-metadata">
            ### Author: ![Alice](/alice.png) [@alice\_smith](/u/alice)
            #### Post date: [Today](/t/123/1 "2026-09-20T19:27:15.434Z")
            </div>

            Hello **world**.

            ---

            <div class='post-metadata'>
            ### Author: [@bob](/u/bob)
            </div>

            > A quote

            My reply.

            [Previous page](/t/topic/123.md?page=1)
            [Next page](/t/topic/123.md?page=2)
        """.trimIndent(), base, 20)
        assertEquals(listOf(20, 21), document.posts.map { it.id })
        assertEquals("alice_smith", document.posts[0].username)
        assertEquals("https://example.com/alice.png", document.posts[0].avatarUrl.toString())
        assertEquals(Instant.parse("2026-09-20T19:27:15.434Z"), document.posts[0].publishedAt)
        assertEquals("Hello **world**.", document.posts[0].markdown)
        assertEquals("> A quote\n\nMy reply.", document.posts[1].markdown)
        assertEquals("https://example.com/t/topic/123.md?page=2", document.nextPageUrl.toString())
    }

    @Test fun boldMetadataMatchesHeadingMetadataAndPreservesFencedExamples() {
        val sampleMarkdown = """
            # Topic
            **URL:** $base

            <div class="post-metadata">
            ### Author: ![Avatar](/avatar.png) [@alice\_smith](/u/alice)
            #### Post date: [September 20](https://example.com "2026-09-20T19:27:15.434Z")
            </div>

            A **bold** post.

            ---

            <div class="post-metadata">
            ### Author: [@bob](/u/bob)
            </div>

            Reply.
            [Next page](/t/topic/123?page=2)
        """.trimIndent()
        val bold = sampleMarkdown.replace("### Author:", "**Author:**").replace("#### Post date:", "**Post date:**")
        assertEquals(MarkdownTopicDocument.parse(sampleMarkdown, base), MarkdownTopicDocument.parse(bold, base))
        val example = "```html\n<div class='post-metadata'>\n**Author:** [@sample](/u/sample)\n</div>\n```"
        val post = MarkdownTopicDocument.parse(example, base).posts.single()
        assertNull(post.username)
        assertEquals(example, post.markdown)
    }

    @Test fun ordinaryMarkdownAndFencedExamplesArePreserved() {
        val markdown = """
            # My heading

            ```html
            <div class="post-metadata">
            ### Author: [@sample](/u/sample)
            </div>
            ```

            **Category:** My text
            [Next page](/t/topic/123.md?page=2)
        """.trimIndent()
        val document = MarkdownTopicDocument.parse(markdown, base)
        assertEquals(markdown, document.posts.single().markdown)
        assertNull(document.nextPageUrl)
        assertNull(document.posts.single().username)
    }

    @Test fun codeFooterMalformedMetadataAndBodyHeadingsAreNotConsumed() {
        val body = "~~~\n[Next page](/t/topic/123.md?page=2)\n~~~"
        val document = MarkdownTopicDocument.parse("# Topic\n\n**URL:** $base\n\n$body", base)
        assertNull(document.nextPageUrl)
        assertEquals(body, document.posts.single().markdown)
        val malformed = "<div class=\"post-metadata\">\nUnexpected format\n</div>\n\nKeep me."
        assertEquals(malformed, MarkdownTopicDocument.parse(malformed, base).posts.single().markdown)
    }

    @Test fun invalidAvatarAndDateDoNotLoseThePost() {
        val markdown = "<div class='post-metadata'>\n### Author: ![A](http://example.com/a.png) [@alice](/u/alice)\n#### Post date: invalid\n</div>\n\nBody."
        val post = MarkdownTopicDocument.parse(markdown, base).posts.single()
        assertEquals("alice", post.username)
        assertNull(post.avatarUrl)
        assertNull(post.publishedAt)
        assertEquals("Body.", post.markdown)
    }

    @Test fun paginationAcceptsOnlyForwardPagesOfSameTopicAndOrigin() {
        assertEquals(2, TopicPagination.pageNumber(TopicPagination.nextUrl(URL("$base.md?page=2"), base, 1)!!))
        listOf("$base.md?page=1", "$base.md?page=no", "$base.md?page=0", "$base.md", "https://other.example/t/topic/123.md?page=2", "https://example.com:444/t/topic/123.md?page=2", "https://example.com/t/456.md?page=2", "http://example.com/t/topic/123.md?page=2")
            .forEach { assertNull(it, TopicPagination.nextUrl(URL(it), base, 1)) }
    }

    @Test fun relativeDatesUseMinutesHoursAndDays() {
        val now = Instant.ofEpochSecond(2_000_000)
        mapOf(0L to "Just now", 60L to "1 minute ago", 120L to "2 minutes ago", 3600L to "1 hour ago", 86400L to "1 day ago", 864000L to "10 days ago")
            .forEach { (seconds, label) -> assertEquals(label, relativePostDate(now.minusSeconds(seconds), now)) }
    }
}
