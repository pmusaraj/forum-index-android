package com.musaraj.forumindex

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URL

internal fun sameTopicOrigin(first: URL, second: URL): Boolean =
    validTopicUrl(first) != null && validTopicUrl(second) != null &&
        first.host.equals(second.host, ignoreCase = true) &&
        (if (first.port == -1) 443 else first.port) == (if (second.port == -1) 443 else second.port)

private fun samePage(first: URL, second: URL) = sameTopicOrigin(first, second) &&
    first.path == second.path && first.query == second.query && first.ref == second.ref

internal class MarkdownPreviewPage(val id: Int, val url: URL, val reader: MarkdownTopicReader) {
    fun bookmark(root: Topic): StarredTopic = if (samePage(url, root.url!!)) root.bookmark() else
        StarredTopic(reader.state.value.title ?: "Linked page", url.toString(), root.forum.name)
}

/** Retains loaded readers on Back; linked URLs never borrow the feed topic's API identity. */
internal class MarkdownPreviewHistory(url: URL, private val source: TopicMarkdownSource) {
    private var nextId = 1
    private val mutablePages = MutableStateFlow(listOf(MarkdownPreviewPage(0, url, MarkdownTopicReader(url, source))))
    val pages = mutablePages.asStateFlow()
    val current get() = pages.value.last()
    val canGoBack get() = pages.value.size > 1

    /** True means handled here, including a link to the already displayed page. */
    fun open(destination: URL): Boolean {
        val base = current.reader.state.value.baseUrl
        if (!sameTopicOrigin(base, destination)) return false
        if (samePage(destination, base) || samePage(destination, current.url)) return true
        mutablePages.value = pages.value + MarkdownPreviewPage(nextId++, destination, MarkdownTopicReader(destination, source))
        return true
    }

    fun back(): Boolean {
        if (!canGoBack) return false
        mutablePages.value = pages.value.dropLast(1)
        return true
    }
}

internal fun Topic.bookmark() = StarredTopic(title, url.toString(), forum.name, id, forum.id)
