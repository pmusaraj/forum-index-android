package com.musaraj.forumindex

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URL

internal object TopicPagination {
    fun pageNumber(url: URL): Int? = url.query?.split('&')?.firstOrNull { it.substringBefore('=') == "page" }
        ?.substringAfter('=', "")?.toIntOrNull()

    fun nextUrl(candidate: URL?, topic: URL, after: Int): URL? = validTopicUrl(candidate)?.takeIf {
        it.host.equals(topic.host, ignoreCase = true) && it.effectivePort() == topic.effectivePort() &&
            it.path.removeSuffix(".md") == topic.path.removeSuffix(".md") && (pageNumber(it) ?: 0) > after
    }

    fun previousUrl(candidate: URL?, topic: URL, before: Int): URL? = validTopicUrl(candidate)?.takeIf {
        it.host.equals(topic.host, ignoreCase = true) && it.effectivePort() == topic.effectivePort() &&
            it.path.removeSuffix(".md") == topic.path.removeSuffix(".md") && (pageNumber(it) ?: 0) in 1 until before
    }

    private fun URL.effectivePort() = if (port == -1) 443 else port
}

internal data class MarkdownReaderState(
    val posts: List<MarkdownPost> = emptyList(),
    val baseUrl: URL,
    val title: String? = null,
    val summary: String? = null,
    val startsTopic: Boolean = false,
    val reachedLastPage: Boolean = false,
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val nextPageUrl: URL? = null,
    val previousPageUrl: URL? = null,
    val loadingPrevious: Boolean = false,
    val loadPreviousError: String? = null,
    val firstUnreadPostNumber: Int? = null,
    val latestReplyAt: Long? = null,
    val loadingMore: Boolean = false,
    val loadMoreError: String? = null,
) {
    val hasCompleteTopic get() = startsTopic && reachedLastPage && posts.isNotEmpty() && !loadingMore && loadMoreError == null
    val participantCount get() = if (hasCompleteTopic && posts.all { it.username != null })
        posts.map { it.username!!.lowercase(java.util.Locale.ROOT) }.distinct().size else null
    val replyCount get() = participantCount?.let { (posts.size - 1).coerceAtLeast(0) }
}

/** Owned by one detail page; callers launch work in that page's coroutine scope. */
internal class MarkdownTopicReader(private val url: URL, private val source: TopicMarkdownSource) {
    private val mutableState = MutableStateFlow(MarkdownReaderState(baseUrl = url))
    val state = mutableState.asStateFlow()

    private val loadedPages = mutableSetOf<Int>()
    private var nextPostId = 0
    private fun uniquePosts(posts: List<MarkdownPost>, existing: List<MarkdownPost> = emptyList()): List<MarkdownPost> {
        val numbers = existing.mapNotNull { it.postNumber }.toMutableSet()
        return posts.filter { it.postNumber == null || numbers.add(it.postNumber) }.map { it.copy(id = nextPostId++) }
    }

    suspend fun load(bookmark: StarredTopic? = null, resumeClient: TopicResumeClient = TopicResumeClient()) {
        if (state.value.loading || state.value.loaded) return
        mutableState.value = state.value.copy(loading = true, error = null)
        try {
            val target = bookmark?.takeIf { it.hasUnreadReplies }?.let { resumeClient.target(it) }
            val page = if (target == null) source.initialDocument(url) else {
                val fetched = source.fetch(target.pageUrl)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    ParsedTopicPage(MarkdownTopicDocument.parse(fetched.markdown, fetched.url), fetched.url)
                }
            }
            currentCoroutineContext().ensureActive()
            val document = page.document
            if (target?.postNumber != null && document.posts.none { it.postNumber == target.postNumber })
                throw java.io.IOException("Unread reply no longer available")
            loadedPages.add(document.pageNumber)
            mutableState.value = state.value.copy(
                firstUnreadPostNumber = target?.postNumber, latestReplyAt = target?.latestReplyAt,
                previousPageUrl = TopicPagination.previousUrl(document.previousPageUrl, page.url, document.pageNumber),
                summary = document.summary, startsTopic = document.startsTopic, reachedLastPage = !document.hasNextPage,
                posts = uniquePosts(document.posts), baseUrl = page.url, title = document.title, loaded = true,
                nextPageUrl = TopicPagination.nextUrl(document.nextPageUrl, page.url, document.pageNumber),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            mutableState.value = state.value.copy(error = "Couldn’t load the preview")
        } finally {
            mutableState.value = state.value.copy(loading = false)
        }
    }

    suspend fun loadNextPage(retrying: Boolean = false) {
        val current = state.value
        val next = current.nextPageUrl ?: return
        if (TopicPagination.pageNumber(next) in loadedPages) return
        if (current.loadingMore || current.loadingPrevious || (current.loadMoreError != null && !retrying)) return
        mutableState.value = current.copy(loadingMore = true, loadMoreError = null)
        try {
            val page = source.fetch(next)
            currentCoroutineContext().ensureActive()
            val document = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { MarkdownTopicDocument.parse(page.markdown, page.url) }
            loadedPages.add(document.pageNumber)
            mutableState.value = state.value.copy(
                posts = current.posts + uniquePosts(document.posts, current.posts),
                reachedLastPage = !document.hasNextPage && document.posts.isNotEmpty(),
                nextPageUrl = if (document.posts.isEmpty()) null else
                    TopicPagination.nextUrl(document.nextPageUrl, current.baseUrl, TopicPagination.pageNumber(next)!!),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            mutableState.value = state.value.copy(loadMoreError = "Couldn’t load more replies.")
        } finally {
            mutableState.value = state.value.copy(loadingMore = false)
        }
    }
    suspend fun loadPreviousPage() {
        val current = state.value
        val previous = current.previousPageUrl ?: return
        if (current.loadingPrevious || current.loadingMore || TopicPagination.pageNumber(previous) in loadedPages) return
        mutableState.value = current.copy(loadingPrevious = true, loadPreviousError = null)
        try {
            val page = source.fetch(previous)
            currentCoroutineContext().ensureActive()
            val document = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { MarkdownTopicDocument.parse(page.markdown, page.url) }
            loadedPages.add(document.pageNumber)
            mutableState.value = state.value.copy(
                posts = uniquePosts(document.posts, current.posts) + current.posts,
                startsTopic = current.startsTopic || document.startsTopic,
                summary = if (document.startsTopic) document.summary else current.summary,
                previousPageUrl = if (document.posts.isEmpty()) null else TopicPagination.previousUrl(document.previousPageUrl, current.baseUrl, document.pageNumber),
            )
        } catch (error: CancellationException) { throw error
        } catch (_: Exception) {
            mutableState.value = state.value.copy(loadPreviousError = "Couldn’t load earlier replies.")
        } finally { mutableState.value = state.value.copy(loadingPrevious = false) }
    }

}
