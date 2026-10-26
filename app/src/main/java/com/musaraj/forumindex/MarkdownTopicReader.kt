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

    suspend fun load() {
        if (state.value.loading || state.value.loaded) return
        mutableState.value = state.value.copy(loading = true, error = null)
        try {
            val page = source.fetch(url)
            currentCoroutineContext().ensureActive()
            val document = MarkdownTopicDocument.parse(page.markdown, page.url)
            mutableState.value = state.value.copy(
                summary = document.summary, startsTopic = document.startsTopic, reachedLastPage = !document.hasNextPage,
                posts = document.posts, baseUrl = page.url, title = document.title, loaded = true,
                nextPageUrl = TopicPagination.nextUrl(document.nextPageUrl, page.url, TopicPagination.pageNumber(page.url) ?: 1),
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
        if (current.loadingMore || (current.loadMoreError != null && !retrying)) return
        mutableState.value = current.copy(loadingMore = true, loadMoreError = null)
        try {
            val page = source.fetch(next)
            currentCoroutineContext().ensureActive()
            val document = MarkdownTopicDocument.parse(page.markdown, page.url, current.posts.size)
            mutableState.value = state.value.copy(
                posts = current.posts + document.posts,
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
}
