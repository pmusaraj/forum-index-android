package com.musaraj.forumindex

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val TopicBackground: Color @Composable get() = MaterialTheme.colorScheme.background
private val TopicSurface: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant

@Composable
internal fun TopicDetailScreen(
    session: TopicDetailSession,
    isStarred: (StarredTopic) -> Boolean,
    onSelect: (Int) -> Unit,
    onToggleStar: (StarredTopic) -> Unit,
    onDismiss: () -> Unit,
    source: TopicMarkdownSource = remember { TopicMarkdownClient() },
    webContent: @Composable (URL, Boolean, (Boolean) -> Unit) -> Unit = { url, visible, ready ->
        TopicWebPage(url, visible = visible, onReady = ready)
    },
) {
    BackHandler(onBack = onDismiss)
    val pager = rememberPagerState(initialPage = session.currentIndex) { session.topics.size }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect(onSelect)
    }
    HorizontalPager(
        state = pager,
        key = { "${session.topics[it].forum.id}:${session.topics[it].id}" },
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize().background(TopicBackground).testTag("topic-detail-pager"),
    ) { index ->
        val topic = session.topics[index]
        val active = pager.settledPage == index
        Box(Modifier.fillMaxSize().then(if (active) Modifier else Modifier.clearAndSetSemantics { })) {
            TopicDetailPage(topic, active, source, webContent, isStarred, onToggleStar, onDismiss)
        }
    }
}

@Composable
private fun TopicDetailPage(
    topic: Topic,
    active: Boolean,
    source: TopicMarkdownSource,
    webContent: @Composable (URL, Boolean, (Boolean) -> Unit) -> Unit,
    isStarred: (StarredTopic) -> Boolean,
    onToggleStar: (StarredTopic) -> Unit,
    onDismiss: () -> Unit,
) {
    val url = validTopicUrl(topic.url) ?: return
    val history = remember(url, source) { MarkdownPreviewHistory(url, source) }
    val pages by history.pages.collectAsState()
    val page = pages.last()
    val reader = page.reader
    val state by key(page.id) { reader.state.collectAsState() }
    var showsOverview by rememberSaveable { mutableStateOf(false) }
    var usesWeb by rememberSaveable(url.toString()) { mutableStateOf(!topic.forum.supportsMarkdown) }
    var fullPageReady by remember(page.id) { mutableStateOf(false) }
    val scope = key(page.id) { rememberCoroutineScope() }
    val articleState = rememberSaveableStateHolder()
    val bookmark = page.bookmark(topic)
    val goBack = {
        if (history.back()) {
            articleState.removeState(page.id)
            usesWeb = false
        }
    }
    BackHandler(enabled = active && history.canGoBack, onBack = goBack)

    LaunchedEffect(page.id) {
        if (topic.forum.supportsMarkdown) {
            reader.load()
            if (history.current === page && page.id == 0 && reader.state.value.error != null) usesWeb = true
        }
    }
    Box(Modifier.fillMaxSize().background(TopicBackground)) {
        Column(Modifier.fillMaxSize().then(if (topic.forum.supportsMarkdown) Modifier.background(TopicSurface).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) else Modifier)) {
            if (topic.forum.supportsMarkdown) {
                Row(
                    Modifier.fillMaxWidth().background(TopicSurface).padding(horizontal = 16.dp).testTag("topic-forum-header"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (history.canGoBack) TextButton(onClick = goBack, modifier = Modifier.testTag("topic-header-back")) {
                        Text("Back")
                    }
                    Row(Modifier.weight(1f).clickable { showsOverview = true }.padding(vertical = 14.dp)
                        .testTag("topic-header-forum-overview").semantics { contentDescription = "${topic.forum.name}, site information" },
                        verticalAlignment = Alignment.CenterVertically) {
                        ForumIcon(topic.forum)
                        Spacer(Modifier.width(8.dp))
                        Text(topic.forum.name, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 14.sp)
                    }
                    TextButton(
                        onClick = { usesWeb = !usesWeb },
                        modifier = Modifier.testTag("topic-view-toggle").semantics {
                            stateDescription = if (usesWeb) "Full page" else if (fullPageReady) "Ready" else "Loading full page"
                        },
                    ) { Text(if (usesWeb) "Show preview" else "Full topic") }
                }
            }
            Box(Modifier.weight(1f)) {
                // Preserve each reader and its saved list position without retaining hidden native text views.
                if (state.loaded) {
                    Box(Modifier.fillMaxSize().then(if (usesWeb) Modifier.alpha(0f).clearAndSetSemantics { } else Modifier)) {
                        articleState.SaveableStateProvider(page.id) {
                            TopicArticle(
                                title = bookmark.title,
                                publishedAt = if (page.id == 0) topic.publishedAt else null,
                                replyCount = if (page.id == 0) topic.replyCount else null,
                                state = state, active = active && !usesWeb, reader = reader,
                                onInternalLink = { destination ->
                                    history.open(destination).also { handled -> if (handled) usesWeb = false }
                                },
                            )
                        }
                    }
                } else if (!usesWeb) {
                    when {
                        state.error != null -> Column(
                            Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(state.error!!)
                            Button(onClick = { scope.launch { reader.load() } }) { Text("Retry") }
                            Button(onClick = { usesWeb = true }) { Text("View full page") }
                        }
                        else -> CircularProgressIndicator(Modifier.align(Alignment.Center).testTag("topic-loading"))
                    }
                }
                // Every linked page gets its own requested URL, including a post suffix, query, or fragment.
                key(page.id) {
                    // AndroidView still participates in Compose hit testing when its native
                    // view is invisible. Put the preloader below the active scroll surface.
                    Box(Modifier.fillMaxSize().zIndex(if (usesWeb) 1f else -1f)) {
                        CompositionLocalProvider(LocalMatchWebHeader provides !topic.forum.supportsMarkdown) {
                            if (usesWeb || state.loaded) webContent(page.url, usesWeb && active) { fullPageReady = it }
                        }
                    }
                }
            }
        }
        if (showsOverview && active) ForumOverview(topic.forum.id, onDismiss = { showsOverview = false })
        if (active) TopicActions(bookmark, isStarred(bookmark), { onToggleStar(bookmark) }, onDismiss, Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun TopicArticle(
    title: String,
    publishedAt: Instant?,
    replyCount: Int?,
    state: MarkdownReaderState,
    active: Boolean,
    reader: MarkdownTopicReader,
    onInternalLink: (URL) -> Boolean,
) {
    val listState = rememberLazyListState()
    val renderer = rememberMarkdownRenderer(state.baseUrl, onInternalLink)
    val renderedPosts = remember(reader) { mutableStateMapOf<Int, Boolean>() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(active, state.nextPageUrl, state.loadMoreError) {
        if (!active || state.nextPageUrl == null || state.loadMoreError != null) return@LaunchedEffect
        snapshotFlow {
            val layout = listState.layoutInfo
            val last = layout.visibleItemsInfo.lastOrNull()
            val visiblePostsReady = layout.visibleItemsInfo.filter { it.key is Int }.all { renderedPosts[it.key] == true }
            visiblePostsReady && last != null && last.index >= layout.totalItemsCount - 2 &&
                last.offset + last.size - layout.viewportEndOffset <= layout.viewportEndOffset - layout.viewportStartOffset
        }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) reader.loadNextPage() }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(TopicBackground).testTag("topic-markdown-preview"),
        contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 190.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "title") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }.testTag("topic-preview-title"))
                val date = publishedAt?.let { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(it) }
                val details = listOfNotNull(date, replyCount?.let { "$it ${if (it == 1) "reply" else "replies"}" })
                if (details.isNotEmpty()) Text(details.joinToString(" · "),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("topic-preview-details"))
            }
        }
        items(state.posts, key = { it.id }) { post ->
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                if (post.username != null) PostAuthor(post)
                MarkdownPostBody(post.markdown, renderer) { renderedPosts[post.id] = true }
            }
        }
        item(key = "pagination") {
            when {
                state.loadingMore -> Text("Loading more replies…", Modifier.testTag("topic-loading-more"))
                state.loadMoreError != null -> Column {
                    Text(state.loadMoreError)
                    Button(onClick = { scope.launch { reader.loadNextPage(retrying = true) } }) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun PostAuthor(post: MarkdownPost) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(post.id) { while (true) { delay(60_000); now = Instant.now() } }
    Row(Modifier.fillMaxWidth().testTag("post-author-${post.id}"), verticalAlignment = Alignment.CenterVertically) {
        ForumIcon(ForumSummary(0, post.username.orEmpty(), "", null, post.avatarUrl))
        Spacer(Modifier.width(8.dp))
        Text("@${post.username.orEmpty()}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1)
        post.publishedAt?.let { date ->
            Text(relativePostDate(date, now), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { contentDescription = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT)
                    .withZone(ZoneId.systemDefault()).format(date) })
        }
    }
}
