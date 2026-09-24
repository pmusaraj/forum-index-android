package com.musaraj.forumindex

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.URL

@OptIn(ExperimentalCoroutinesApi::class)
class MarkdownTopicReaderTest {
    private val url = URL("https://example.com/t/topic/123")
    private fun page(body: String, next: Int? = null) = "# Topic\n\n**URL:** $url\n\n$body" +
        (next?.let { "\n\n[Next page]($url.md?page=$it)" } ?: "")

    @Test fun appendsWithStableIdsAndStopsOnRepeatedLink() = runTest {
        var calls = 0
        val reader = MarkdownTopicReader(url) { requested ->
            calls++
            MarkdownPage(if (calls == 1) page("First", 2) else page("Second", 2), requested)
        }
        reader.load()
        val first = reader.state.value.posts.single()
        reader.loadNextPage()
        assertEquals(listOf("First", "Second"), reader.state.value.posts.map { it.markdown })
        assertEquals(listOf(0, 1), reader.state.value.posts.map { it.id })
        assertEquals(first, reader.state.value.posts.first())
        assertNull(reader.state.value.nextPageUrl)
        reader.loadNextPage()
        assertEquals(2, calls)
    }

    @Test fun coalescesRequestsAndRequiresExplicitRetryAfterFailure() = runTest {
        var calls = 0
        val pending = CompletableDeferred<MarkdownPage>()
        val reader = MarkdownTopicReader(url) { requested ->
            when (++calls) {
                1 -> MarkdownPage(page("First", 2), requested)
                2 -> pending.await()
                else -> MarkdownPage(page("Second"), requested)
            }
        }
        reader.load()
        val job = launch { reader.loadNextPage() }
        runCurrent()
        reader.loadNextPage()
        assertEquals(2, calls)
        pending.completeExceptionally(IOException())
        job.join()
        assertNotNull(reader.state.value.loadMoreError)
        reader.loadNextPage()
        assertEquals(2, calls)
        reader.loadNextPage(retrying = true)
        assertEquals(2, reader.state.value.posts.size)
        assertNull(reader.state.value.loadMoreError)
    }

    @Test fun cancellationRetainsPostsAndNextPageWithoutError() = runTest {
        var calls = 0
        val pending = CompletableDeferred<MarkdownPage>()
        val reader = MarkdownTopicReader(url) { requested ->
            if (++calls == 1) MarkdownPage(page("First", 2), requested) else pending.await()
        }
        reader.load()
        val next = reader.state.value.nextPageUrl
        val job = launch { reader.loadNextPage() }
        runCurrent()
        job.cancel()
        job.join()
        assertEquals(listOf("First"), reader.state.value.posts.map { it.markdown })
        assertEquals(next, reader.state.value.nextPageUrl)
        assertNull(reader.state.value.loadMoreError)
        assertFalse(reader.state.value.loadingMore)
    }

    @Test fun initialFailureCanRetryAndEmptyPageEndsPagination() = runTest {
        var calls = 0
        val reader = MarkdownTopicReader(url) { requested ->
            when (++calls) {
                1 -> throw IOException()
                2 -> MarkdownPage(page("First", 2), requested)
                else -> MarkdownPage(page("", 3), requested)
            }
        }
        reader.load()
        assertNotNull(reader.state.value.error)
        reader.load()
        assertTrue(reader.state.value.loaded)
        assertNull(reader.state.value.error)
        reader.loadNextPage()
        assertNull(reader.state.value.nextPageUrl)
    }

    @Test fun migrationUsesFinalUrlForRelativeLinksAndReplyPaging() = runTest {
        val migrated = URL("https://new.example/t/topic/123")
        val reader = MarkdownTopicReader(url) {
            MarkdownPage("# Topic\n\n**URL:** $migrated\n\nBody\n\n[Next page](/t/topic/123.md?page=2)", migrated)
        }
        reader.load()
        assertEquals(migrated, reader.state.value.baseUrl)
        assertEquals("https://new.example/t/topic/123.md?page=2", reader.state.value.nextPageUrl.toString())
    }
}
