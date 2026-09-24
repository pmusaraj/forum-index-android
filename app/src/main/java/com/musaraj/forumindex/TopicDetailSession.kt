package com.musaraj.forumindex

data class TopicIdentity(val forumId: Int, val topicId: Int)
internal val Topic.identity get() = TopicIdentity(forum.id, id)

data class FeedReturnTarget(val destinationId: String, val topic: TopicIdentity)

/** A stable snapshot of the feed: refreshes cannot reorder a reader's open topic pages. */
@ConsistentCopyVisibility
data class TopicDetailSession private constructor(
    val topics: List<Topic>,
    val destinationId: String,
    val currentIndex: Int,
    val hasPaged: Boolean = false,
) {
    val currentTopic get() = topics[currentIndex]

    fun selecting(index: Int): TopicDetailSession =
        if (index !in topics.indices || index == currentIndex) this else copy(currentIndex = index, hasPaged = true)

    companion object {
        fun create(topics: List<Topic>, selected: Topic, destinationId: String): TopicDetailSession? {
            val navigable = topics.filter { validTopicUrl(it.url) != null }.distinctBy { it.identity }
            val index = navigable.indexOfFirst { it.identity == selected.identity }
            return if (index < 0) null else TopicDetailSession(navigable, destinationId, index)
        }
    }
}
