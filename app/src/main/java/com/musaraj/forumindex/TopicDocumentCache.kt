package com.musaraj.forumindex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URL

internal data class ParsedTopicPage(val document: MarkdownTopicDocument, val url: URL)

/** Bounded, short-lived initial documents only; readers and unread cursors are never shared. */
internal class TopicDocumentCache(
    private val capacity: Int = 12,
    private val maximumBytes: Int = 4 * 1024 * 1024,
    private val lifetimeMillis: Long = 120_000,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val page: ParsedTopicPage, val bytes: Int, val expires: Long)
    private val entries = linkedMapOf<String, Entry>()
    private val mutex = Mutex()

    suspend fun load(url: URL, fetch: suspend (URL) -> MarkdownPage): ParsedTopicPage {
        val key = url.toString()
        mutex.withLock {
            entries.remove(key)?.takeIf { it.expires > now() }?.let {
                entries[key] = it
                return it.page
            }
        }
        val page = fetch(url)
        val parsed = withContext(Dispatchers.Default) { ParsedTopicPage(MarkdownTopicDocument.parse(page.markdown, page.url), page.url) }
        val bytes = page.markdown.toByteArray(Charsets.UTF_8).size
        if (bytes <= maximumBytes) mutex.withLock {
            entries.remove(key)
            entries[key] = Entry(parsed, bytes, now() + lifetimeMillis)
            while (entries.size > capacity || entries.values.sumOf { it.bytes.toLong() } > maximumBytes) entries.remove(entries.keys.first())
        }
        return parsed
    }
}
