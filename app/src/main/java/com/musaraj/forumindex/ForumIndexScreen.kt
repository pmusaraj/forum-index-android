package com.musaraj.forumindex

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

private val ReaderBackground = Color(0xFFFAF6EE)
private val ReaderAccent = Color(0xFF008C95)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ForumIndexReader(
    uiState: UiState,
    stars: List<StarredTopic> = emptyList(),
    isOpened: (Topic) -> Boolean = { false },
    isStarred: (Topic) -> Boolean = { false },
    onSelect: (Destination) -> Unit = {},
    onRetryTaxonomy: () -> Unit = {},
    onUpdateVisibleOrder: (List<String>) -> Unit = {},
    onRefresh: (Destination) -> Unit = {},
    onRetry: (Destination) -> Unit = {},
    onLoadNextPage: (Destination) -> Unit = {},
    onOpenTopic: (Topic) -> Unit = {},
    onStars: () -> Unit = {},
    onToggleStar: (Topic) -> Unit = {},
    onReport: (Topic, String) -> Unit = { _, _ -> },
    onNeedsEnrollment: () -> Unit = {},
    onOpenStarred: (StarredTopic) -> Unit = {},
    onRemoveStar: (StarredTopic) -> Unit = {},
    onSettings: () -> Unit = {},
    defaultDeviceName: String = Build.MODEL,
    onEnroll: (String, String) -> Unit = { _, _ -> },
    onRefreshContribution: () -> Unit = {},
    onUpdateDevice: (String) -> Unit = {},
    onSubmitForum: (String) -> Boolean = { false },
    onOptOut: () -> Unit = {},
) {
    val visible = uiState.visibleDestinations
    if (visible.isEmpty()) {
        Box(Modifier.fillMaxSize().background(ReaderBackground), contentAlignment = Alignment.Center) {
            if (uiState.taxonomy is TaxonomyState.Error) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(uiState.taxonomy.message)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRetryTaxonomy) { Text("Retry") }
                }
            } else {
                CircularProgressIndicator()
            }
        }
        return
    }

    var subjectsOpen by rememberSaveable { mutableStateOf(false) }
    var starsOpen by rememberSaveable { mutableStateOf(false) }
    var contributionOpen by rememberSaveable { mutableStateOf(false) }
    var explicitReportTopicId by rememberSaveable { mutableStateOf<Int?>(null) }
    val selectedIndex = visible.indexOfFirst { it.id == uiState.selectedDestination?.id }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = selectedIndex) { visible.size }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.selectedDestination?.id, visible.map { it.id }) {
        val target = visible.indexOfFirst { it.id == uiState.selectedDestination?.id }
        if (target >= 0 && target != pager.currentPage) pager.scrollToPage(target)
    }
    LaunchedEffect(pager, visible.map { it.id }, uiState.selectedDestination?.id) {
        snapshotFlow { pager.settledPage }
            .filter { it in visible.indices }
            .distinctUntilChanged()
            .collect { page ->
                val destination = visible[page]
                if (uiState.selectedDestination?.id != destination.id) onSelect(destination)
            }
    }

    Column(Modifier.fillMaxSize().background(ReaderBackground)) {
        ReaderHeader(
            onStars = { starsOpen = true; onStars() },
            onSettings = { contributionOpen = true; onSettings() },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                modifier = Modifier.weight(1f).height(48.dp).testTag("subject-tabs"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(visible, key = { it.id }) { destination ->
                    val selected = destination.id == uiState.selectedDestination?.id
                    Box(
                        Modifier
                            .height(48.dp)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = {
                                    scope.launch { pager.animateScrollToPage(visible.indexOf(destination)) }
                                },
                            )
                            .padding(horizontal = 14.dp)
                            .testTag("tab-${destination.tabTag()}")
                            .semantics { stateDescription = if (selected) "Selected" else "Not selected" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            destination.label(),
                            color = if (selected) ReaderAccent else Color.Black,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = if (selected) Modifier.padding(bottom = 3.dp) else Modifier,
                        )
                        if (selected) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(ReaderAccent))
                    }
                }
            }
            IconButton(
                onClick = { subjectsOpen = true },
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Choose subjects" },
            ) { Text("•••", fontWeight = FontWeight.Bold) }
        }
        val holder = rememberSaveableStateHolder()
        HorizontalPager(
            state = pager,
            key = { visible[it].id },
            modifier = Modifier.fillMaxSize().testTag("reader-pager"),
        ) { page ->
            val destination = visible[page]
            holder.SaveableStateProvider(destination.id) {
                val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
                FeedPage(
                    destination = destination,
                    feed = uiState.feeds[destination.id] ?: FeedState.Initial,
                    listState = listState,
                    isOpened = isOpened,
                    isStarred = isStarred,
                    onRefresh = onRefresh,
                    onRetry = onRetry,
                    onLoadNextPage = onLoadNextPage,
                    onOpenTopic = onOpenTopic,
                    onToggleStar = onToggleStar,
                    onReport = { topic, kind ->
                        if (uiState.contribution.enrollment == null) {
                            contributionOpen = true
                            onNeedsEnrollment()
                        } else {
                            explicitReportTopicId = topic.id
                            onReport(topic, kind)
                        }
                    },
                )
            }
        }
    }

    if (subjectsOpen) {
        SubjectSheet(
            all = uiState.allDestinations,
            visible = visible,
            onDismiss = { subjectsOpen = false },
            onUpdate = onUpdateVisibleOrder,
        )
    }
    if (starsOpen) StarredSheet(stars, { starsOpen = false }, onOpenStarred, onRemoveStar)
    if (contributionOpen) ContributionSheet(
        state = uiState.contribution,
        defaultDeviceName = defaultDeviceName,
        onDismiss = { contributionOpen = false },
        onEnroll = onEnroll,
        onRefresh = onRefreshContribution,
        onUpdateDevice = onUpdateDevice,
        onSubmitForum = onSubmitForum,
        onOptOut = onOptOut,
    )

    val explicitReportState = explicitReportTopicId?.let(uiState.contribution.reports::get)
    LaunchedEffect(explicitReportState) {
        if (explicitReportState is ReportState.Succeeded || explicitReportState is ReportState.NeedsEnrollment) {
            explicitReportTopicId = null
        }
    }
    if (explicitReportState is ReportState.Failed) {
        AlertDialog(
            onDismissRequest = { explicitReportTopicId = null },
            confirmButton = { Button(onClick = { explicitReportTopicId = null }) { Text("OK") } },
            title = { Text("Report failed") },
            text = { Text(explicitReportState.message) },
        )
    }
}

@Composable
private fun ReaderHeader(onStars: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(start = 16.dp).testTag("reader-header"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).semantics { heading() }, verticalAlignment = Alignment.Bottom) {
            Text("forum", color = Color.Black, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("i", color = ReaderAccent, fontSize = 21.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold)
            Text("ndex", color = ReaderAccent, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        IconButton(onClick = onStars, modifier = Modifier.size(48.dp)) {
            Text("☆", fontSize = 28.sp, modifier = Modifier.semantics { contentDescription = "Starred topics" })
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Settings, contentDescription = "Contribution settings")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContributionSheet(
    state: ContributionState,
    defaultDeviceName: String,
    onDismiss: () -> Unit,
    onEnroll: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onUpdateDevice: (String) -> Unit,
    onSubmitForum: (String) -> Boolean,
    onOptOut: () -> Unit,
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var deviceName by rememberSaveable { mutableStateOf(defaultDeviceName.cappedLabel()) }
    var confirmOptOut by rememberSaveable { mutableStateOf(false) }
    var forumURL by rememberSaveable { mutableStateOf("") }
    val installation = state.enrollment?.installation

    LaunchedEffect(installation?.deviceName) {
        if (installation != null) deviceName = installation.deviceName.cappedLabel()
    }
    LaunchedEffect(state.forumSubmissionMessage) {
        if (state.forumSubmissionMessage == "Forum submitted for review.") forumURL = ""
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ReaderBackground,
    ) {
        Column(
            Modifier.fillMaxHeight(.92f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp).testTag("contribution-sheet"),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Forum Index", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Close contribution settings" },
                ) { Text("×", fontSize = 24.sp) }
            }

            Text(
                "This is an app showing activity in Discourse forums across different subjects. Your reading and starring activity anonymously helps improve the results.",
                color = Color.Black.copy(alpha = .65f), fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "When you have contributions enabled, your Main feed will be personalized based on starred and read topics.",
                color = Color.Black.copy(alpha = .65f), fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))

            if (installation == null) {
                Text("Optionally share simple topic actions to improve the public index.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.cappedLabel() },
                    label = { Text("Display name") },
                    singleLine = true,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth().testTag("display-name"),
                    supportingText = { Text("Required · 100 characters maximum") },
                )
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it.cappedLabel() },
                    label = { Text("Device name") },
                    singleLine = true,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth().testTag("device-name"),
                    supportingText = { Text("Required · 100 characters maximum") },
                )
                Button(
                    onClick = { onEnroll(displayName.trim(), deviceName.trim()) },
                    enabled = !state.loading && displayName.validLabel() && deviceName.validLabel(),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("enable-contributions"),
                ) { Text("Enable contributions") }
            } else {
                Text(if (installation.trusted) "Trusted contributor" else "Public contributor", fontWeight = FontWeight.SemiBold)
                Text(if (installation.deviceVerified) "Device verified" else "Device unverified")
                TextButton(
                    onClick = onRefresh,
                    enabled = !state.loading && !state.submittingForum,
                    modifier = Modifier.height(48.dp).testTag("refresh-contribution-status"),
                ) { Text("Refresh status") }
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it.cappedLabel() },
                    label = { Text("Device name") },
                    singleLine = true,
                    enabled = !state.loading && !state.submittingForum,
                    modifier = Modifier.fillMaxWidth().testTag("contribution-device-name"),
                    supportingText = { Text("Required · 100 characters maximum") },
                )
                Button(
                    onClick = { onUpdateDevice(deviceName.trim()) },
                    enabled = !state.loading && !state.submittingForum && deviceName.validLabel() && deviceName.trim() != installation.deviceName,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save-device-name"),
                ) { Text("Save") }
            }

            if (installation?.trusted == true) {
                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text("Trusted tools", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = forumURL,
                    onValueChange = { if (it.toByteArray().size <= 1_000) forumURL = it },
                    label = { Text("Forum URL") },
                    singleLine = true,
                    enabled = !state.loading && !state.submittingForum,
                    modifier = Modifier.fillMaxWidth().testTag("forum-submission-url"),
                )
                Button(
                    onClick = { onSubmitForum(forumURL.trim()) },
                    enabled = !state.loading && !state.submittingForum && forumURL.trim().startsWith("https://") &&
                        forumURL.trim().toByteArray().size <= 1_000,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("submit-forum"),
                ) { Text(if (state.submittingForum) "Submitting…" else "Submit forum") }
                state.forumSubmissionMessage?.let {
                    Text(
                        it,
                        color = if (it == "Forum submitted for review.") Color.Black.copy(alpha = .65f)
                        else MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                    )
                }
            }

            if (state.loading) {
                Row(
                    Modifier.fillMaxWidth().height(48.dp).testTag("contribution-loading"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Working…")
                }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, maxLines = 3, modifier = Modifier.padding(vertical = 8.dp))
            }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text("Transparency", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            listOf("Star changes", "Reads", "Low quality reports", "Inappropriate reports", "Wrong subject reports").forEach {
                Text(it, Modifier.fillMaxWidth().padding(vertical = 5.dp))
            }
            Text(
                "Opening a topic reports a read. Stars stay local. Actions are sent only while contributions are enabled.",
                color = Color.Black.copy(alpha = .65f), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )
            if (installation != null) {
                TextButton(
                    onClick = { confirmOptOut = true },
                    enabled = !state.loading && !state.submittingForum,
                    modifier = Modifier.height(48.dp).testTag("opt-out"),
                ) { Text("Opt out") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmOptOut) AlertDialog(
        onDismissRequest = { confirmOptOut = false },
        title = { Text("Confirm opt out") },
        text = { Text("Stop sending contribution actions? Your local stars will remain on this device.") },
        dismissButton = {
            TextButton(onClick = { confirmOptOut = false }, modifier = Modifier.height(48.dp)) { Text("Keep contributions") }
        },
        confirmButton = {
            TextButton(
                onClick = { confirmOptOut = false; onOptOut() },
                modifier = Modifier.height(48.dp).testTag("confirm-opt-out"),
            ) { Text("Opt out") }
        },
    )
}

private fun String.validLabel() = trim().let { it.isNotEmpty() && it.codePointCount(0, it.length) <= CONTRIBUTION_LABEL_MAX }

private fun String.cappedLabel(): String = if (codePointCount(0, length) <= CONTRIBUTION_LABEL_MAX) this
else substring(0, offsetByCodePoints(0, CONTRIBUTION_LABEL_MAX))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedPage(
    destination: Destination,
    feed: FeedState,
    listState: LazyListState,
    isOpened: (Topic) -> Boolean,
    isStarred: (Topic) -> Boolean,
    onRefresh: (Destination) -> Unit,
    onRetry: (Destination) -> Unit,
    onLoadNextPage: (Destination) -> Unit,
    onOpenTopic: (Topic) -> Unit,
    onToggleStar: (Topic) -> Unit,
    onReport: (Topic, String) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = feed is FeedState.Refreshing,
        onRefresh = { onRefresh(destination) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("feed-${destination.id}"),
        ) {
            when (feed) {
                FeedState.Initial -> skeletonRows()
                FeedState.Empty -> item {
                    Box(Modifier.fillParentMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No topics yet")
                    }
                }
                is FeedState.Loaded -> topicRows(feed.rows, isOpened, isStarred, onOpenTopic, onToggleStar, onReport)
                is FeedState.Refreshing -> {
                    if (feed.rows.isEmpty()) skeletonRows() else {
                        item { StatusBanner("Refreshing…") }
                        topicRows(feed.rows, isOpened, isStarred, onOpenTopic, onToggleStar, onReport)
                    }
                }
                is FeedState.Failed -> {
                    if (feed.rows.isEmpty()) item {
                        Box(Modifier.fillParentMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(feed.message)
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { onRetry(destination) }) { Text("Retry") }
                            }
                        }
                    } else {
                        item { StatusBanner("${feed.message} Showing saved topics.") }
                        topicRows(feed.rows, isOpened, isStarred, onOpenTopic, onToggleStar, onReport)
                    }
                }
            }
            if (feed is FeedState.Loaded && feed.hasMore) item(key = "end-${feed.page}-${feed.rows.size}") {
                LaunchedEffect(destination.id, feed.page, feed.rows.size) { onLoadNextPage(destination) }
                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) { Text("Loading more…") }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.skeletonRows() {
    items(20) { index ->
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = if (index == 0) 36.dp else 10.dp)
                .testTag("skeleton-row-$index"),
        ) {
            Box(Modifier.fillMaxWidth(.82f).height(18.dp).background(Color.Black.copy(alpha = .08f)))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(150.dp).height(14.dp).background(Color.Black.copy(alpha = .06f)))
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.topicRows(
    rows: List<Topic>,
    isOpened: (Topic) -> Boolean,
    isStarred: (Topic) -> Boolean,
    onOpenTopic: (Topic) -> Unit,
    onToggleStar: (Topic) -> Unit,
    onReport: (Topic, String) -> Unit,
) {
    itemsIndexed(rows, key = { _, topic -> "${topic.forum.id}:${topic.id}" }) { index, topic ->
        TopicRow(topic, index == 0, isOpened(topic), isStarred(topic), onOpenTopic, onToggleStar, onReport)
    }
}

@Composable
private fun TopicRow(
    topic: Topic,
    first: Boolean,
    opened: Boolean,
    starred: Boolean,
    onOpenTopic: (Topic) -> Unit,
    onToggleStar: (Topic) -> Unit,
    onReport: (Topic, String) -> Unit,
) {
    var actionsOpen by remember { mutableStateOf(false) }
    val validUrl = validTopicUrl(topic.url) != null
    Column(
        Modifier.fillMaxWidth()
            .combinedClickable(
                onClick = { if (validUrl) onOpenTopic(topic) },
                onLongClick = { actionsOpen = true },
            )
            .testTag("topic-${topic.forum.id}-${topic.id}")
            .padding(start = 16.dp, end = 16.dp, top = if (first) 36.dp else 10.dp, bottom = 10.dp)
            .alpha(if (opened) .75f else 1f)
            .semantics { contentDescription = "${topic.title}, ${topic.forum.name}, ${topic.replyCount} replies" },
    ) {
        Text(topic.title, color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ForumIcon(topic.forum)
            Spacer(Modifier.width(6.dp))
            Text(
                "${topic.forum.name} · ${topic.replyCount} ${if (topic.replyCount == 1) "reply" else "replies"}",
                color = Color.Black.copy(alpha = .58f), fontSize = 13.sp,
            )
        }
    }
    if (actionsOpen) TopicActions(
        topic = topic,
        starred = starred,
        canStar = validUrl,
        onDismiss = { actionsOpen = false },
        onToggleStar = { actionsOpen = false; onToggleStar(topic) },
        onReport = { kind -> actionsOpen = false; onReport(topic, kind) },
    )
}

@Composable
private fun TopicActions(
    topic: Topic,
    starred: Boolean,
    canStar: Boolean,
    onDismiss: () -> Unit,
    onToggleStar: () -> Unit,
    onReport: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = { Text(topic.title) },
        text = {
            Column {
                if (canStar) Button(onClick = onToggleStar) { Text(if (starred) "Unstar" else "Star") }
                Text("Report", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
                Button(onClick = { onReport("low_quality") }) { Text("Low quality") }
                Button(onClick = { onReport("inappropriate") }) { Text("Inappropriate") }
                Button(onClick = { onReport("wrong_subject") }) { Text("Wrong subject") }
            }
        },
    )
}

@Composable
private fun ForumIcon(forum: ForumSummary) {
    val bitmap by produceState<Bitmap?>(null, forum.iconUrl) {
        value = withContext(Dispatchers.IO) { forum.iconUrl?.let(::loadIcon) }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), contentDescription = null, Modifier.size(16.dp).clip(CircleShape))
    } else {
        Box(
            Modifier.size(16.dp).clip(CircleShape).background(ReaderAccent),
            contentAlignment = Alignment.Center,
        ) { Text(forum.name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
    }
}

private fun loadIcon(url: URL): Bitmap? {
    if (!url.protocol.equals("https", true) || url.path.endsWith(".svg", true)) return null
    val connection = try { url.openConnection() as HttpURLConnection } catch (_: Exception) { return null }
    return try {
        connection.connectTimeout = 3_000
        connection.readTimeout = 5_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "image/png,image/jpeg,image/webp")
        if (connection.responseCode !in 200..299 || connection.contentType?.contains("svg", true) == true ||
            connection.contentLengthLong > MAX_ICON_BYTES) return null
        val output = ByteArrayOutputStream()
        connection.inputStream.use { input ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_ICON_BYTES) return null
                output.write(buffer, 0, count)
            }
        }
        val bytes = output.toByteArray()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..MAX_ICON_DIMENSION || bounds.outHeight !in 1..MAX_ICON_DIMENSION) null
        else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
        null
    } finally {
        connection.disconnect()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StarredSheet(
    stars: List<StarredTopic>,
    onDismiss: () -> Unit,
    onOpen: (StarredTopic) -> Unit,
    onRemove: (StarredTopic) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ReaderBackground) {
        Column(Modifier.fillMaxHeight(.82f).fillMaxWidth().padding(horizontal = 16.dp)) {
            Text("Starred", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
            if (stars.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No starred topics") }
            } else LazyColumn {
                items(stars, key = { "${it.forumId}:${it.topicId}:${it.url}" }) { star ->
                    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.weight(1f).fillMaxHeight().clickable { onOpen(star) }
                                .testTag("starred-${star.forumId}-${star.topicId}").padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(star.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(star.forumName, color = Color.Black.copy(alpha = .58f), fontSize = 13.sp)
                        }
                        IconButton(
                            onClick = { onRemove(star) },
                            modifier = Modifier.size(48.dp).semantics { contentDescription = "Remove ${star.title}" },
                        ) { Text("×", fontSize = 24.sp) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectSheet(
    all: List<Destination>,
    visible: List<Destination>,
    onDismiss: () -> Unit,
    onUpdate: (List<String>) -> Unit,
) {
    val visibleIds = visible.map(Destination::id)
    val hidden = all.filter { it.id !in visibleIds }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = ReaderBackground) {
        LazyColumn(Modifier.fillMaxHeight(.82f).fillMaxWidth().padding(horizontal = 16.dp)) {
            item { Text("Subjects", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp)) }
            itemsIndexed(visible, key = { _, it -> "visible-${it.id}" }) { index, destination ->
                SubjectRow(
                    destination = destination,
                    checked = true,
                    canMoveUp = index > 0,
                    canMoveDown = index < visible.lastIndex,
                    onToggle = {
                        if (visible.size > 1) onUpdate(visibleIds - destination.id)
                    },
                    onMoveUp = {
                        val reordered = visibleIds.toMutableList()
                        reordered[index] = reordered[index - 1].also { reordered[index - 1] = reordered[index] }
                        onUpdate(reordered)
                    },
                    onMoveDown = {
                        val reordered = visibleIds.toMutableList()
                        reordered[index] = reordered[index + 1].also { reordered[index + 1] = reordered[index] }
                        onUpdate(reordered)
                    },
                )
            }
            if (hidden.isNotEmpty()) {
                item { HorizontalDivider(Modifier.padding(vertical = 12.dp)); Text("More subjects", fontWeight = FontWeight.SemiBold) }
                items(hidden, key = { "hidden-${it.id}" }) { destination ->
                    SubjectRow(destination, checked = false, onToggle = { onUpdate(visibleIds + destination.id) })
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SubjectRow(
    destination: Destination,
    checked: Boolean,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("☰", Modifier.width(32.dp).semantics { contentDescription = "Reorder ${destination.label()}" }, color = Color.Black.copy(alpha = .45f))
        Text(destination.label(), Modifier.weight(1f), fontSize = 16.sp)
        if (checked) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move ${destination.label()} up")
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move ${destination.label()} down")
            }
        }
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(48.dp).semantics {
                contentDescription = "Toggle ${destination.label()}"
                stateDescription = if (checked) "Visible" else "Hidden"
            },
        )
    }
}

@Composable
private fun StatusBanner(message: String) {
    Text(
        message,
        Modifier.fillMaxWidth().background(ReaderAccent.copy(alpha = .12f)).padding(horizontal = 16.dp, vertical = 10.dp),
        color = Color.Black,
    )
}

private fun Destination.label() = when (this) {
    Destination.Main -> "Main"
    is Destination.Subject -> subject.name
}

private fun Destination.tabTag() = when (this) {
    Destination.Main -> "main-feed"
    is Destination.Subject -> subject.slug
}

private const val MAX_ICON_BYTES = 256 * 1024L
private const val MAX_ICON_DIMENSION = 1024
private const val CONTRIBUTION_LABEL_MAX = 100
